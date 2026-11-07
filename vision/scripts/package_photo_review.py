"""Versioned v2 package: current photos, credits, four-dimension drafts and offline HTML."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile,ZIP_STORED


def main():
    p=argparse.ArgumentParser();p.add_argument('--batch',default='vision/data/batches/batch-001');args=p.parse_args();folder=Path(args.batch).resolve()
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
    names=['review-v2.html','manifest.json','analysis-v2-evidence-final.jsonl','four-dimension-summary.json','四维标注使用说明.md','SACD_NOTES.md']
    files=[folder/n for n in names]
    for item in manifest['items']:
        image=folder/item['image_path']
        if not image.resolve().is_relative_to(folder) or hashlib.sha256(image.read_bytes()).hexdigest()!=item['sha256']:raise ValueError('Invalid source image')
        files.extend([image,folder/'analysis-v2-evidence-final'/(item['id']+'.json')])
    archive=folder.parent/'FrameCoach-四维标注审核-v2.zip'
    with ZipFile(archive,'w',compression=ZIP_STORED) as z:
        for file in files:z.write(file,'FrameCoach-四维标注审核-v2/'+file.relative_to(folder).as_posix())
    with ZipFile(archive) as z:
        if z.testzip() is not None or len(z.namelist())!=len(files):raise ValueError('Archive verification failed')
    print(json.dumps({'archive':str(archive),'files':len(files),'bytes':archive.stat().st_size},ensure_ascii=True))


if __name__=='__main__':main()
