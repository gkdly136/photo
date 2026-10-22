"""Archive validated human labels locally; importing does not start model training."""
import argparse
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'vision'))
from framecoach_vision.dataset import digest
from framecoach_vision.photo_review import validate_export


def main():
    p=argparse.ArgumentParser();p.add_argument('--batch',default='vision/data/batches/batch-001');p.add_argument('--analysis',default='analysis-v2-evidence-final');p.add_argument('--review',required=True);args=p.parse_args();folder=Path(args.batch);source=Path(args.review)
    if source.stat().st_size>15000000:raise ValueError('Review file too large')
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
    annotations=[json.loads(line) for line in (folder/(args.analysis+'.jsonl')).read_text(encoding='utf-8').splitlines()]
    payload=json.loads(source.read_text(encoding='utf-8'));rows=validate_export(payload,manifest,annotations)
    ready=[r['id'] for r in rows if r['photo_decision']=='keep' and all(d['status'] in {'approve','corrected','not_applicable'} for d in r['dimensions'].values())]
    record={'review':payload,'review_sha256':digest(payload),'human_verified_candidate_ids':ready,'model_training_performed':False,'note':'Human review is recorded; no training has been started. Split, license and release checks remain separate.'}
    out=folder/'human-review-v2';out.mkdir(exist_ok=True);dest=out/(digest(payload)+'.json')
    if not dest.exists():dest.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'saved':str(dest),'reviewed_records':len(rows),'human_verified_candidates':len(ready),'training_performed':False},ensure_ascii=True))


if __name__=='__main__':main()
