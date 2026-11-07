"""Build versioned, offline four-dimension review; never replace old A/B reviews."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'vision'))
from framecoach_vision.dataset import digest
from framecoach_vision.photo_labels import VOCAB, parse_labels
from build_dataset_review import embedded_preview


def main():
    p=argparse.ArgumentParser();p.add_argument('--batch',default='vision/data/batches/batch-001');p.add_argument('--analysis',default='analysis-v2-evidence-final');args=p.parse_args();folder=Path(args.batch)
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
    records=[json.loads(line) for line in (folder/(args.analysis+'.jsonl')).read_text(encoding='utf-8').splitlines()]
    by_id={r['id']:r for r in records}
    if len(records)!=len(by_id) or set(by_id)!={i['id'] for i in manifest['items']}:raise ValueError('Every current source needs one versioned draft')
    items=[]
    for i in manifest['items']:
        path=folder/i['image_path'];a=by_id[i['id']]
        if not path.resolve().is_relative_to(folder.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest()!=i['sha256'] or a['image_sha256']!=i['sha256']:raise ValueError('Stale image or invalid path')
        if digest({k:v for k,v in a.items() if k!='annotation_sha256'})!=a['annotation_sha256']:raise ValueError('Invalid annotation digest')
        parse_labels(json.dumps(a['labels']))
        items.append({k:i[k] for k in ['id','group','title','author','license','license_url','source_page','sha256']}|{'annotation':a,'image_src':embedded_preview(path)})
    payload={'schema_version':2,'dataset_id':manifest['dataset_id'],'manifest_sha256':digest(manifest),'analysis_sha256':digest(records),'vocab':VOCAB,'items':items}
    data=json.dumps(payload,ensure_ascii=False,separators=(',',':')).replace('<','\\u003c').replace('\u2028','\\u2028').replace('\u2029','\\u2029')
    template=(ROOT/'vision/photo-review-template.html').read_text(encoding='utf-8')
    script=(ROOT/'vision/photo-review.js').read_text(encoding='utf-8')
    (folder/'review-v2.html').write_text(template.replace('__REVIEW_SCRIPT__',script).replace('__DATASET_JSON__',data),encoding='utf-8')
    summary={'schema_version':2,'photos':len(items),'dimensions':list(VOCAB),'annotation_digest':digest(records),'valid_complete_drafts':sum(not a['validation_error'] for a in records),'partially_invalid_drafts':sum(bool(a['validation_error']) for a in records),'person_targets':sum(bool(a['target_subject'] and a['target_subject']['kind']=='person') for a in records),'sacd_training_performed':False,'model_training_performed':False,'human_approved':0}
    (folder/'four-dimension-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=True))


if __name__=='__main__':main()
