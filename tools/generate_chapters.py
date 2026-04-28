import os
import json
import subprocess
import argparse
import zipfile
import xml.etree.ElementTree as ET
from xml.dom import minidom
import shutil
from tqdm import tqdm

def get_chapters(m4b_path):
    """Extract chapters from m4b using ffprobe."""
    cmd = [
        'ffprobe', 
        '-v', 'error', 
        '-show_chapters', 
        '-of', 'json', 
        m4b_path
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        data = json.loads(result.stdout)
        return data.get('chapters', [])
    except Exception as e:
        print(f"  [ERROR] Extracting chapters from {os.path.basename(m4b_path)}: {e}")
        return []

def get_duration(m4b_path):
    """Get total duration of m4b in milliseconds."""
    cmd = [
        'ffprobe', 
        '-v', 'error', 
        '-show_entries', 'format=duration', 
        '-of', 'default=noprint_wrappers=1:nokey=1', 
        m4b_path
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return int(float(result.stdout.strip()) * 1000)
    except Exception as e:
        print(f"  [ERROR] Getting duration for {os.path.basename(m4b_path)}: {e}")
        return 0

def create_chapters_xml(chapters_data):
    """Create the chapters.xml content."""
    root = ET.Element("chapters")
    for ch in chapters_data:
        chapter_el = ET.SubElement(root, "chapter")
        title = ch.get('tags', {}).get('title', 'Chapter')
        start_ms = str(int(float(ch['start_time']) * 1000))
        end_ms = str(int(float(ch['end_time']) * 1000))
        
        chapter_el.set("title", title)
        chapter_el.set("start_ms", start_ms)
        chapter_el.set("end_ms", end_ms)
    
    # Pretty print
    xml_str = ET.tostring(root, encoding='utf-8')
    parsed = minidom.parseString(xml_str)
    return parsed.toprettyxml(indent="  ")

def inject_into_epub(epub_path, xml_content):
    """Inject chapters.xml into the EPUB zip file with a progress bar."""
    temp_epub = epub_path + ".tmp"
    epub_filename = os.path.basename(epub_path)
    try:
        with zipfile.ZipFile(epub_path, 'r') as zin:
            # Determine path: check if OEBPS exists
            infolist = zin.infolist()
            has_oebps = any(name.startswith('OEBPS/') for name in zin.namelist())
            target_path = "OEBPS/misc/chapters.xml" if has_oebps else "misc/chapters.xml"
            
            with zipfile.ZipFile(temp_epub, 'w') as zout:
                with tqdm(total=len(infolist) + 1, desc=f"Writing to {epub_filename[:30]}...", unit="file", leave=False) as pbar:
                    for item in infolist:
                        if item.filename != target_path:
                            zout.writestr(item, zin.read(item.filename))
                        pbar.update(1)
                    
                    # Add our new chapters.xml
                    zout.writestr(target_path, xml_content)
                    pbar.update(1)
        
        shutil.move(temp_epub, epub_path)
        print(f"  [SUCCESS] Updated {epub_filename} -> {target_path}")
    except Exception as e:
        if os.path.exists(temp_epub):
            os.remove(temp_epub)
        print(f"  [ERROR] Injecting XML into {epub_filename}: {e}")

def process_directory(base_dir):
    """Walk directory and process matching files."""
    print(f"Scanning directory: {base_dir}")
    
    # First pass to count folders for overall progress
    all_dirs = []
    for root, dirs, files in os.walk(base_dir):
        m4b_files = [f for f in files if f.lower().endswith('.m4b')]
        epub_files = [f for f in files if f.lower().endswith('(readaloud).epub')]
        if m4b_files and epub_files:
            all_dirs.append((root, m4b_files, epub_files))
            
    if not all_dirs:
        print("No folders found with both .m4b and (readaloud).epub files.")
        return

    print(f"Found {len(all_dirs)} folders to process.")
    
    for root, m4b_names, epub_names in tqdm(all_dirs, desc="Overall Progress", unit="folder"):
        m4b_files = sorted(m4b_names)
        epub_files = epub_names
        
        # tqdm.write is better than print when using progress bars
        tqdm.write(f"\nProcessing: {os.path.relpath(root, base_dir)}")
        
        all_chapters = []
        cumulative_duration_ms = 0
        
        for m4b in m4b_files:
            m4b_path = os.path.join(root, m4b)
            chapters = get_chapters(m4b_path)
            
            if not chapters:
                tqdm.write(f"  [WARN] No metadata chapters in {m4b}. Using file as single chapter.")
                duration_ms = get_duration(m4b_path)
                all_chapters.append({
                    'tags': {'title': os.path.splitext(m4b)[0]},
                    'start_time': cumulative_duration_ms / 1000.0,
                    'end_time': (cumulative_duration_ms + duration_ms) / 1000.0
                })
                cumulative_duration_ms += duration_ms
                continue
            
            for ch in chapters:
                start_ms = int(float(ch['start_time']) * 1000) + cumulative_duration_ms
                end_ms = int(float(ch['end_time']) * 1000) + cumulative_duration_ms
                
                all_chapters.append({
                    'tags': ch.get('tags', {}),
                    'start_time': start_ms / 1000.0,
                    'end_time': end_ms / 1000.0
                })
            
            cumulative_duration_ms += get_duration(m4b_path)
            
        if all_chapters:
            tqdm.write(f"  Generated {len(all_chapters)} chapters total.")
            xml_content = create_chapters_xml(all_chapters)
            for epub in epub_files:
                inject_into_epub(os.path.join(root, epub), xml_content)
        else:
            tqdm.write(f"  [SKIP] No chapters could be determined.")

def main():
    parser = argparse.ArgumentParser(description="Generate chapters.xml from m4b and inject into (readaloud).epub")
    parser.add_argument("directory", help="The directory to search")
    args = parser.parse_args()
    
    if not os.path.isdir(args.directory):
        print(f"Error: {args.directory} is not a directory")
        return
        
    process_directory(args.directory)

if __name__ == "__main__":
    main()
