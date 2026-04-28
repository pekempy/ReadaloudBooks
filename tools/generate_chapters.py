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

def get_audio_duration_ms(zip_ref, audio_path):
    temp_audio = "temp_audio_dur" + os.path.splitext(audio_path)[1]
    try:
        with zip_ref.open(audio_path) as source, open(temp_audio, 'wb') as target:
            shutil.copyfileobj(source, target)
        cmd = ['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'default=noprint_wrappers=1:nokey=1', temp_audio]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return int(float(result.stdout.strip()) * 1000)
    finally:
        if os.path.exists(temp_audio): os.remove(temp_audio)

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

def ms_to_timestamp(ms):
    hours = ms // 3600000
    minutes = (ms % 3600000) // 60000
    seconds = (ms % 60000) // 1000
    return f"{hours:02}:{minutes:02}:{seconds:02}.{ms%1000:03}"

def process_epub(epub_path, root_dir):
    m4b_markers = get_m4b_chapters(root_dir)
    if not m4b_markers:
        print(f"  [SKIP] No m4b found in {root_dir}")
        return None

    # We still need the total duration to cap chapters
    total_dur_ms = 0
    try:
        with zipfile.ZipFile(epub_path, 'r') as zin:
            container = ET.fromstring(zin.read('META-INF/container.xml'))
            rootfile = container.find('.//*{urn:oasis:names:tc:opendocument:xmlns:container}rootfile')
            opf_path = rootfile.get('full-path')
            opf_content = ET.fromstring(zin.read(opf_path))
            ns = {'opf': 'http://www.idpf.org/2007/opf'}
            manifest = {item.get('id'): item.attrib for item in opf_content.findall('.//opf:item', ns)}
            spine = opf_content.findall('.//opf:itemref', ns)
            
            seen_audio = set()
            for itemref in spine:
                item_id = itemref.get('idref')
                item_data = manifest.get(item_id)
                if not item_data or not item_data.get('media-overlay'): continue
                
                smil_item = manifest.get(item_data['media-overlay'])
                smil_path = os.path.join(os.path.dirname(opf_path), smil_item['href']).replace('\\', '/')
                smil_root = ET.fromstring(zin.read(smil_path))
                
                for par in smil_root.findall('.//{http://www.w3.org/ns/SMIL}par'):
                    audio_el = par.find('{http://www.w3.org/ns/SMIL}audio')
                    if audio_el is None: continue
                    src = audio_el.get('src')
                    abs_audio = os.path.normpath(os.path.join(os.path.dirname(smil_path), src)).replace('\\', '/')
                    if abs_audio not in seen_audio:
                        total_dur_ms += get_audio_duration_ms(zin, abs_audio)
                        seen_audio.add(abs_audio)
    except: pass

    chapters = []
    print(f"  Mapping {len(m4b_markers)} chapters directly from M4B...")
    for m in m4b_markers:
        title = m.get('tags', {}).get('title', 'Chapter')
        start_ms = int(float(m['start_time']) * 1000)
        end_ms = int(float(m['end_time']) * 1000)
        
        # Cap to total duration if necessary
        if total_dur_ms > 0:
            if start_ms >= total_dur_ms: start_ms = total_dur_ms - 1
            if end_ms > total_dur_ms: end_ms = total_dur_ms

        print(f"    [OK] {title} -> {ms_to_timestamp(start_ms)}")
        chapters.append({'title': title, 'start_ms': start_ms, 'end_ms': end_ms})
    
    return chapters

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
    with zipfile.ZipFile(epub_path, 'r') as zin:
        has_oebps = any(name.startswith('OEBPS/') for name in zin.namelist())
        target_path = "OEBPS/misc/chapters.xml" if has_oebps else "misc/chapters.xml"
        with zipfile.ZipFile(temp_epub, 'w') as zout:
            for item in zin.infolist():
                if item.filename != target_path:
                    zout.writestr(item, zin.read(item.filename))
            zout.writestr(target_path, xml_content)
    shutil.move(temp_epub, epub_path)
    print(f"  [SUCCESS] Injected M4B markers into {os.path.basename(epub_path)}")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    args = parser.parse_args()
    
    epub_files = [os.path.join(r, f) for r, d, fs in os.walk(args.directory) for f in fs if f.lower().endswith('(readaloud).epub')]
    for epub in epub_files:
        print(f"\nProcessing: {os.path.basename(epub)}")
        chapters = process_epub(epub, os.path.dirname(epub))
        if chapters:
            inject_into_epub(epub, create_chapters_xml(chapters))

if __name__ == "__main__":
    main()
