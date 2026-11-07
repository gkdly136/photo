"""Verify delivered photo bytes, four-dimensional bindings and crop geometry, not aesthetics."""
import argparse
import base64
from collections import Counter
import copy
import hashlib
from io import BytesIO
import json
from pathlib import Path
import sys
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'vision'))
from framecoach_vision.dataset import digest
from framecoach_vision.domain import Box
from framecoach_vision.photo_labels import parse_labels
from framecoach_vision.photo_review import validate_export


def main():
    p=argparse.ArgumentParser();p.add_argument('--batch',default='vision/data/batches/batch-001');p.add_argument('--output',default='vision/verification/four-dimension-batch-001.json');args=p.parse_args();folder=Path(args.batch)
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
    records=[json.loads(line) for line in (folder/'analysis-v2-evidence-final.jsonl').read_text(encoding='utf-8').splitlines()]
    text=(folder/'review-v2.html').read_text(encoding='utf-8')
    payload=json.loads(text.split('<script id="dataset" type="application/json">',1)[1].split('</script>',1)[0])
    if payload['manifest_sha256']!=digest(manifest) or payload['analysis_sha256']!=digest(records):raise ValueError('Review payload is stale')
    by_id={r['id']:r for r in records};pages={i['id']:i for i in payload['items']}
    if len(records)!=len(by_id) or len(pages)!=len(manifest['items']) or set(by_id)!=set(pages):raise ValueError('Missing or duplicate annotations')
    pending=[];preview_count=0;window_count=0
    for item in manifest['items']:
        a=by_id[item['id']];page=pages[item['id']];path=folder/item['image_path']
        if not path.resolve().is_relative_to(folder.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:raise ValueError('Invalid source bytes')
        if a['image_sha256']!=item['sha256'] or a['annotation_sha256']!=digest({k:v for k,v in a.items() if k!='annotation_sha256'}) or page['annotation']!=a:raise ValueError('Annotation version mismatch')
        parse_labels(json.dumps(a['labels']))
        uri=page['image_src']
        if not uri.startswith('data:image/jpeg;base64,'):raise ValueError('Preview must be embedded')
        with Image.open(BytesIO(base64.b64decode(uri.split(',',1)[1],validate=True))) as im:
            im.load()
            if im.format!='JPEG' or max(im.size)>960:raise ValueError('Invalid display preview')
        preview_count+=1
        research=copy.deepcopy(a['composition_research'])
        if research['trained_on_sacd'] is not False:raise ValueError('Unsupported training claim')
        windows=research['windows'];window_count+=len(windows)
        for w in windows:
            if w['preference'] is not None or w['aesthetic_score'] is not None:raise ValueError('Invented machine preference')
            if not Box.parse(w['box']).contains(Box.parse(a['target_subject']['box'])):raise ValueError('Subject cut off')
            w['decision']='pending'
        pending.append({'id':item['id'],'image_sha256':item['sha256'],'annotation_sha256':a['annotation_sha256'],'photo_decision':'pending','labels':a['labels'],'dimensions':{g:{'status':'pending','note':''} for g in a['labels']},'target_subject':a['target_subject'],'composition_research':{'windows':windows,'best_id':None},'updated_at':None})
    # Constructed unreviewed rows only: this does not create or import user decisions.
    validate_export({'schema_version':2,'dataset_id':manifest['dataset_id'],'manifest_sha256':digest(manifest),'analysis_sha256':digest(records),'records':pending},manifest,records)
    summary={'date':'2026-10-22','dataset_id':manifest['dataset_id'],'photos':len(records),'embedded_previews_decoded':preview_count,'source_checksums_verified':len(records),'annotation_checksums_verified':len(records),'complete_schema_valid':sum(not r['validation_error'] for r in records),'person_targets':sum(bool(r['target_subject'] and r['target_subject']['kind']=='person') for r in records),'missing_subject_requires_review':sum(r['target_subject'] is None for r in records),'crop_windows':window_count,'photos_without_different_crop':sum(len(r['composition_research']['windows'])==1 for r in records),'crop_window_counts':dict(Counter(len(r['composition_research']['windows']) for r in records)),'subject_containment_and_iou_dedup_checked':True,'manifest_sha256':digest(manifest),'analysis_sha256':digest(records),'review_html_bytes':(folder/'review-v2.html').stat().st_size,'model_weights_changed':False,'sacd_data_downloaded':False,'training_performed':False,'human_approved':0,'assistant_visual_checks':sum('assistant_visual_check' in r for r in records),'semantic_accuracy_evaluated':False,'browser_visual_qa_performed':False,'validation':'File decoding, independent schema validation, geometry, bindings and synthetic DOM tests; not a photography quality assessment.'}
    out=Path(args.output);out.parent.mkdir(exist_ok=True,parents=True);out.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps(summary,ensure_ascii=True))


if __name__=='__main__':main()
