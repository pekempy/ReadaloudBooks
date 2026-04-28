import os
import json
import subprocess
import argparse
import zipfile
import xml.etree.ElementTree as ET
from xml.dom import minidom
import shutil
import re
from tqdm import tqdm
import tempfile

# Lazy-loaded Whisper
whisper_model = None

# Namespaces for EPUB parsing
NS = {
    'opf': 'http://www.idpf.org/2007/opf',
    'container': 'urn:oasis:names:tc:opendocument:xmlns:container',
    'smil': 'http://www.w3.org/ns/SMIL',
    'epub': 'http://www.idpf.org/2007/ops',
    'xhtml': 'http://www.w3.org/1999/xhtml'
}

def get_audio_duration_ms(zip_ref, audio_path):
    """Get duration of an audio file inside the zip."""
    temp_audio = os.path.join(tempfile.gettempdir(), "temp_audio_dur" + os.path.splitext(audio_path)[1])
    try:
        with zip_ref.open(audio_path) as source, open(temp_audio, 'wb') as target:
            shutil.copyfileobj(source, target)
        
        cmd = [
            'ffprobe', '-v', 'error', '-show_entries', 'format=duration',
            '-of', 'default=noprint_wrappers=1:nokey=1', temp_audio
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return int(float(result.stdout.strip()) * 1000)
    finally:
        if os.path.exists(temp_audio):
            os.remove(temp_audio)

def detect_speech_onset(zip_ref, audio_path, target_ms, window_ms=4000):
    """Use ffmpeg to find the exact moment speech starts after silence around a target."""
    # Extract a small window of audio
    ext = os.path.splitext(audio_path)[1]
    temp_snippet = os.path.join(tempfile.gettempdir(), f"snippet_{target_ms}{ext}")
    start_sec = max(0, (target_ms - window_ms // 2) / 1000.0)
    
    try:
        with zip_ref.open(audio_path) as source, open(temp_snippet, 'wb') as target:
            shutil.copyfileobj(source, target)
            
        # Detect silence
        cmd = [
            'ffmpeg', '-ss', str(start_sec), '-t', str(window_ms/1000.0),
            '-i', temp_snippet,
            '-af', 'silencedetect=n=-40dB:d=0.3',
            '-f', 'null', '-'
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        # Look for the last silence_end before the middle of our window
        # silence_end: 1.234
        matches = re.findall(r'silence_end: ([\d\.]+)', result.stderr)
        if matches:
            # Return the silence end closest to the relative middle of the snippet
            rel_onset = float(matches[-1])
            return int((start_sec + rel_onset) * 1000)
            
    except Exception as e:
        pass
    finally:
        if os.path.exists(temp_snippet): os.remove(temp_snippet)
    return target_ms

def verify_with_whisper(zip_ref, audio_path, target_ms, expected_text):
    """Use Whisper to verify if the text at target_ms matches expected_text."""
    global whisper_model
    try:
        import whisper
        if whisper_model is None:
            tqdm.write("  [WHISPER] Loading tiny model...")
            whisper_model = whisper.load_model("tiny.en")
    except ImportError:
        return target_ms # Fallback if not installed

    ext = os.path.splitext(audio_path)[1]
    temp_snippet = os.path.join(tempfile.gettempdir(), f"whisper_{target_ms}{ext}")
    # Extract 5 seconds from the target
    start_sec = max(0, (target_ms - 500) / 1000.0)
    
    try:
        # Extract snippet using ffmpeg (faster than writing whole file)
        # But for ZIP entries we have to write it out or pipe it
        with zip_ref.open(audio_path) as source, open(temp_snippet + ".full", 'wb') as target:
            shutil.copyfileobj(source, target)
        
        subprocess.run([
            'ffmpeg', '-y', '-ss', str(start_sec), '-t', '8',
            '-i', temp_snippet + ".full", '-acodec', 'copy', temp_snippet
        ], capture_output=True)
        
        result = whisper_model.transcribe(temp_snippet, fp16=False)
        found_text = result['text'].lower()
        
        # Clean expected text
        exp = expected_text.lower().strip()
        parts = [p.strip() for p in re.split(r'[-—]', exp) if p.strip()]
        
        # If whisper hears any part of the title, we are golden
        for p in parts:
            if p in found_text:
                # Find the exact timestamp of the matching segment in whisper
                for seg in result['segments']:
                    if p in seg['text'].lower():
                        return int((start_sec + seg['start']) * 1000)
    except Exception as e:
        tqdm.write(f"  [WHISPER ERROR] {e}")
    finally:
        for f in [temp_snippet, temp_snippet + ".full"]:
            if os.path.exists(f): os.remove(f)
            
    return target_ms

def parse_time_to_ms(time_str):
    if not time_str: return 0
    if time_str.endswith('s'): return int(float(time_str[:-1]) * 1000)
    parts = time_str.split(':')
    if len(parts) == 3:
        h, m, s = parts
        return int((int(h) * 3600 + int(m) * 60 + float(s)) * 1000)
    try: return int(float(time_str) * 1000)
    except: return 0

def strip_tags(text):
    return re.sub(r'<[^>]*>', '', text).strip()

def get_m4b_chapters(root_dir):
    for f in os.listdir(root_dir):
        if f.lower().endswith('.m4b'):
            m4b_path = os.path.join(root_dir, f)
            cmd = ['ffprobe', '-v', 'error', '-show_chapters', '-of', 'json', m4b_path]
            try:
                result = subprocess.run(cmd, capture_output=True, text=True, check=True)
                return json.loads(result.stdout).get('chapters', [])
            except: pass
    return []

def process_epub_sync(epub_path, root_dir):
    chapters = []
    epub_filename = os.path.basename(epub_path)
    m4b_markers = get_m4b_chapters(root_dir)
    
    try:
        with zipfile.ZipFile(epub_path, 'r') as zin:
            container = ET.fromstring(zin.read('META-INF/container.xml'))
            rootfile = container.find('.//container:rootfile', NS)
            opf_path = rootfile.get('full-path')
            opf_dir = os.path.dirname(opf_path)
            opf_content = ET.fromstring(zin.read(opf_path))
            manifest = {item.get('id'): item.attrib for item in opf_content.findall('.//opf:item', NS)}
            spine = opf_content.findall('.//opf:itemref', NS)
            
            audio_file_offsets = {}
            current_global_offset = 0
            
            all_segments = []
            tqdm.write(f"  Indexing EPUB sync points...")
            for itemref in tqdm(spine, desc="Indexing", unit="item", leave=False):
                item_id = itemref.get('idref')
                item_data = manifest.get(item_id)
                if not item_data or not item_data.get('media-overlay'): continue
                
                smil_item = manifest.get(item_data['media-overlay'])
                smil_path = os.path.join(opf_dir, smil_item['href']).replace('\\', '/')
                smil_root = ET.fromstring(zin.read(smil_path))
                
                html_path = os.path.join(opf_dir, item_data['href']).replace('\\', '/')
                html_content = zin.read(html_path).decode('utf-8', errors='ignore')
                
                for par in smil_root.findall('.//smil:par', NS):
                    audio_el = par.find('smil:audio', NS)
                    text_el = par.find('smil:text', NS)
                    if audio_el is None or text_el is None: continue
                    
                    audio_href = audio_el.get('src')
                    abs_audio_path = os.path.normpath(os.path.join(os.path.dirname(smil_path), audio_href)).replace('\\', '/')
                    
                    if abs_audio_path not in audio_file_offsets:
                        audio_file_offsets[abs_audio_path] = current_global_offset
                        current_global_offset += get_audio_duration_ms(zin, abs_audio_path)
                    
                    global_ts = audio_file_offsets[abs_audio_path] + parse_time_to_ms(audio_el.get('clipBegin'))
                    
                    txt = ""
                    target_id = text_el.get('src').split('#')[1] if '#' in text_el.get('src') else None
                    if target_id:
                        match = re.search(f'id=["\']{target_id}["\'][^>]*>(.*?)<', html_content, re.DOTALL)
                        if match: txt = strip_tags(match.group(1))
                    
                    all_segments.append({'ts': global_ts, 'text': txt, 'audio_path': abs_audio_path})

            if not m4b_markers: return None

            tqdm.write(f"  Accuracy-FUCK Pipeline: Snapping {len(m4b_markers)} chapters...")
            for m in tqdm(m4b_markers, desc="Aligning Chapters"):
                full_title = m.get('tags', {}).get('title', 'Chapter')
                raw_start = int(float(m['start_time']) * 1000)
                parts = [p.strip().lower() for p in re.split(r'[-—]', full_title) if p.strip()]
                
                # 1. SMIL Matching (Initial Guess)
                best_seg = None
                min_dist = 60000 
                for seg in all_segments:
                    dist = abs(seg['ts'] - raw_start)
                    if dist > min_dist: continue
                    
                    score = 0
                    for p in parts:
                        if p in seg['text'].lower(): score += 10
                    
                    if score > 0:
                        final_score = score - (dist / 1000.0)
                        if best_seg is None or final_score > best_seg['score']:
                            best_seg = {'ts': seg['ts'], 'score': final_score, 'audio_path': seg['audio_path']}
                
                current_ts = best_seg['ts'] if best_seg else raw_start
                audio_path = best_seg['audio_path'] if best_seg else all_segments[0]['audio_path']
                
                # 2. Silence Detection (Fine Tuning)
                refined_ts = detect_speech_onset(zin, audio_path, current_ts)
                
                # 3. Whisper Verification (Ultra Precision)
                # Only use Whisper if the title is complex or we're not sure
                final_ts = verify_with_whisper(zin, audio_path, refined_ts, full_title)
                
                tqdm.write(f"    [OK] {full_title} -> {final_ts/1000.0}s")
                chapters.append({'title': full_title, 'start_ms': final_ts})

            chapters.sort(key=lambda x: x['start_ms'])
            for i in range(len(chapters)):
                chapters[i]['end_ms'] = chapters[i+1]['start_ms'] if i+1 < len(chapters) else current_global_offset
            return chapters
    except Exception as e:
        tqdm.write(f"  [ERROR] {e}")
        return None

def create_chapters_xml(chapters_data):
    root = ET.Element("chapters")
    for ch in chapters_data:
        chapter_el = ET.SubElement(root, "chapter")
        chapter_el.set("title", ch['title'])
        chapter_el.set("start_ms", str(ch['start_ms']))
        chapter_el.set("end_ms", str(ch['end_ms']))
    return minidom.parseString(ET.tostring(root)).toprettyxml(indent="  ")

def inject_into_epub(epub_path, xml_content):
    temp_epub = epub_path + ".tmp"
    try:
        with zipfile.ZipFile(epub_path, 'r') as zin:
            infolist = zin.infolist()
            has_oebps = any(name.startswith('OEBPS/') for name in zin.namelist())
            target_path = "OEBPS/misc/chapters.xml" if has_oebps else "misc/chapters.xml"
            with zipfile.ZipFile(temp_epub, 'w') as zout:
                for item in infolist:
                    if item.filename != target_path:
                        zout.writestr(item, zin.read(item.filename))
                zout.writestr(target_path, xml_content)
        shutil.move(temp_epub, epub_path)
        tqdm.write(f"  [SUCCESS] Updated chapters.xml")
    except Exception as e:
        if os.path.exists(temp_epub): os.remove(temp_epub)
        tqdm.write(f"  [ERROR] {e}")

def process_directory(base_dir):
    epub_files = [os.path.join(r, f) for r, d, fs in os.walk(base_dir) for f in fs if f.lower().endswith('(readaloud).epub')]
    for epub_path in epub_files:
        tqdm.write(f"\nProcessing: {os.path.basename(epub_path)}")
        chapters = process_epub_sync(epub_path, os.path.dirname(epub_path))
        if chapters:
            inject_into_epub(epub_path, create_chapters_xml(chapters))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    args = parser.parse_args()
    process_directory(args.directory)

if __name__ == "__main__":
    main()
