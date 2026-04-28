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
    temp_audio = "temp_audio_dur" + os.path.splitext(audio_path)[1]
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

def parse_time_to_ms(time_str):
    """Convert SMIL time strings to milliseconds."""
    if not time_str: return 0
    if time_str.endswith('s'):
        return int(float(time_str[:-1]) * 1000)
    parts = time_str.split(':')
    if len(parts) == 3:
        h, m, s = parts
        return int((int(h) * 3600 + int(m) * 60 + float(s)) * 1000)
    try:
        return int(float(time_str) * 1000)
    except:
        return 0

def strip_tags(text):
    return re.sub(r'<[^>]*>', '', text).strip()

def is_chapter_title(text, is_first_segment=False):
    """Detect if text looks like a chapter title with extremely high confidence."""
    t = text.strip().lower()
    if not t or len(t) > 60: return False
    
    # 1. Explicit keywords
    if any(k in t.split() for k in ["chapter", "part", "prologue", "epilogue"]):
        return True
    
    # 2. Strict patterns at the start of a file
    if is_first_segment:
        # Just a number: "1", "01", "Chapter 1"
        if re.search(r'^(chapter\s+)?\d+[:\.\s\-]*$', t):
            return True
        # Written numbers as full words
        nums = ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"]
        words = t.split()
        if words and (words[0] in nums or (len(words) > 1 and words[0] == "chapter" and words[1] in nums)):
            return True
        # Short uppercase titles (e.g. "SPINNER'S END")
        if text.isupper() and 3 < len(text) < 40:
            return True
            
    return False

def get_m4b_chapters(root_dir):
    """Find and extract rough chapter markers from any .m4b in the folder."""
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
    """Hybrid Sync: Use M4B markers for names, align to nearest SMIL segment for timing."""
    chapters = []
    epub_filename = os.path.basename(epub_path)
    m4b_markers = get_m4b_chapters(root_dir)
    
    if not m4b_markers:
        tqdm.write(f"  [WARN] No M4B markers found. Falling back to text scanning...")
        # (Will implement text scan fallback below)
    
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
            
            # Step 1: Build a map of all SMIL segments to global timestamps
            all_segments = [] # List of (timestamp, title_text)
            
            tqdm.write(f"  Indexing EPUB sync points...")
            
            # Use same logic as Android app: cumulative SMIL clip durations
            current_smil_offset = 0
            
            for itemref in tqdm(spine, desc="Indexing", unit="item", leave=False):
                item_id = itemref.get('idref')
                item_data = manifest.get(item_id)
                if not item_data or not item_data.get('media-overlay'): continue
                
                smil_item = manifest.get(item_data['media-overlay'])
                smil_path = os.path.join(opf_dir, smil_item['href']).replace('\\', '/')
                smil_root = ET.fromstring(zin.read(smil_path))
                
                html_path = os.path.join(opf_dir, item_data['href']).replace('\\', '/')
                html_content = zin.read(html_path).decode('utf-8', errors='ignore')
                
                seg_idx = 0
                for par in smil_root.findall('.//smil:par', NS):
                    audio_el = par.find('smil:audio', NS)
                    text_el = par.find('smil:text', NS)
                    if audio_el is None or text_el is None: continue
                    
                    clip_begin = parse_time_to_ms(audio_el.get('clipBegin'))
                    clip_end = parse_time_to_ms(audio_el.get('clipEnd'))
                    clip_dur = clip_end - clip_begin
                    if clip_dur <= 0: continue
                    
                    global_ts = current_smil_offset
                    
                    # Extract text snippet for this segment
                    txt = ""
                    target_id = text_el.get('src').split('#')[1] if '#' in text_el.get('src') else None
                    if target_id:
                        match = re.search(f'id=["\']{target_id}["\'][^>]*>(.*?)<', html_content, re.DOTALL)
                        if match: txt = strip_tags(match.group(1))
                    
                    all_segments.append({'ts': global_ts, 'text': txt, 'is_first': seg_idx == 0})
                    seg_idx += 1
                    
                    current_smil_offset += clip_dur

            if not all_segments: return None

            # Step 2: Match M4B markers to the best segment using keyword snapping
            if m4b_markers:
                tqdm.write(f"  Precision snapping {len(m4b_markers)} chapters...")
                for m in m4b_markers:
                    full_title = m.get('tags', {}).get('title', 'Chapter')
                    raw_start = int(float(m['start_time']) * 1000)
                    
                    # Split title into parts (e.g. "Chapter One - The Other Minister")
                    parts = [p.strip().lower() for p in re.split(r'[-—]', full_title) if p.strip()]
                    
                    best_seg = None
                    min_dist = 60000 # 60s fuzzy window
                    
                    # Search for the best segment match
                    for i, seg in enumerate(all_segments):
                        dist = abs(seg['ts'] - raw_start)
                        if dist > min_dist: continue
                        
                        seg_text = seg['text'].lower()
                        
                        # Perfect match: segment contains one of our title parts
                        match_score = 0
                        for p in parts:
                            # Use word-boundary match to avoid "The" matching "The Other Minister"
                            if re.search(r'\b' + re.escape(p) + r'\b', seg_text):
                                match_score += 10
                            elif p in seg_text:
                                match_score += 5
                        
                        # Also check the NEXT segment (for split titles like HP)
                        if i + 1 < len(all_segments):
                            next_seg_text = all_segments[i+1]['text'].lower()
                            for p in parts:
                                if re.search(r'\b' + re.escape(p) + r'\b', next_seg_text):
                                    match_score += 5
                        
                        if match_score > 0:
                            # Prioritize matches that are closer to the m4b time
                            final_score = match_score - (dist / 1000.0)
                            if best_seg is None or final_score > best_seg['score']:
                                best_seg = {'ts': seg['ts'], 'score': final_score}
                    
                    if best_seg:
                        chapters.append({'title': full_title, 'start_ms': best_seg['ts']})
                    else:
                        # Fallback to raw time if no text match found
                        chapters.append({'title': full_title, 'start_ms': raw_start})
            else:
                # Fallback to pure text scan if no M4B
                for seg in all_segments:
                    if is_chapter_title(seg['text'], seg['is_first']):
                        if not chapters or (seg['ts'] - chapters[-1]['start_ms']) > 10000:
                            chapters.append({'title': seg['text'], 'start_ms': seg['ts']})

            if not chapters: return None
            chapters.sort(key=lambda x: x['start_ms'])
            for i in range(len(chapters)):
                chapters[i]['end_ms'] = chapters[i+1]['start_ms'] if i+1 < len(chapters) else current_smil_offset
            return chapters
    except Exception as e:
        tqdm.write(f"  [ERROR] {e}")
        return None

def create_chapters_xml(chapters_data):
    """Create the chapters.xml content."""
    root = ET.Element("chapters")
    for ch in chapters_data:
        chapter_el = ET.SubElement(root, "chapter")
        chapter_el.set("title", ch['title'])
        chapter_el.set("start_ms", str(ch['start_ms']))
        chapter_el.set("end_ms", str(ch['end_ms']))
    
    xml_str = ET.tostring(root, encoding='utf-8')
    parsed = minidom.parseString(xml_str)
    return parsed.toprettyxml(indent="  ")

def inject_into_epub(epub_path, xml_content):
    """Inject chapters.xml into the EPUB zip file."""
    temp_epub = epub_path + ".tmp"
    epub_filename = os.path.basename(epub_path)
    try:
        with zipfile.ZipFile(epub_path, 'r') as zin:
            infolist = zin.infolist()
            has_oebps = any(name.startswith('OEBPS/') for name in zin.namelist())
            target_path = "OEBPS/misc/chapters.xml" if has_oebps else "misc/chapters.xml"
            
            with zipfile.ZipFile(temp_epub, 'w') as zout:
                with tqdm(total=len(infolist) + 1, desc=f"Writing to {epub_filename[:30]}...", unit="file", leave=False) as pbar:
                    for item in infolist:
                        if item.filename != target_path:
                            zout.writestr(item, zin.read(item.filename))
                        pbar.update(1)
                    zout.writestr(target_path, xml_content)
                    pbar.update(1)
        
        shutil.move(temp_epub, epub_path)
        tqdm.write(f"  [SUCCESS] Updated {epub_filename} -> {target_path}")
    except Exception as e:
        if os.path.exists(temp_epub):
            os.remove(temp_epub)
        tqdm.write(f"  [ERROR] Injecting XML into {epub_filename}: {e}")

def process_directory(base_dir):
    """Walk directory and process Readaloud EPUBs."""
    print(f"Scanning directory: {base_dir}")
    
    epub_files_found = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.lower().endswith('(readaloud).epub'):
                epub_files_found.append(os.path.join(root, f))
            
    if not epub_files_found:
        print("No (readaloud).epub files found.")
        return

    print(f"Found {len(epub_files_found)} EPUB(s) to process.")
    
    for epub_path in tqdm(epub_files_found, desc="Overall Progress", unit="book"):
        tqdm.write(f"\nProcessing: {os.path.relpath(epub_path, base_dir)}")
        
        chapters = process_epub_sync(epub_path, os.path.dirname(epub_path))
        
        if chapters:
            tqdm.write(f"  Generated {len(chapters)} chapters using granular sync data.")
            xml_content = create_chapters_xml(chapters)
            inject_into_epub(epub_path, xml_content)
        else:
            tqdm.write(f"  [SKIP] Could not generate sync data for this book.")

def main():
    parser = argparse.ArgumentParser(description="Generate perfect chapters.xml using EPUB internal SMIL sync data.")
    parser.add_argument("directory", help="The directory to search")
    args = parser.parse_args()
    
    if not os.path.isdir(args.directory):
        print(f"Error: {args.directory} is not a directory")
        return
        
    process_directory(args.directory)

if __name__ == "__main__":
    main()
