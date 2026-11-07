"""Validate four-dimension human reviews independently of the review-page UI."""
import json
from .dataset import digest
from .domain import Box, InvalidObservation
from .photo_labels import VOCAB, parse_labels
from .subject_composition import iou

STATES={'pending','approve','corrected','not_applicable','reject'}


def validate_record(row, item, annotation):
    if not isinstance(row,dict) or row.get('id')!=item['id'] or row.get('image_sha256')!=item['sha256'] or row.get('annotation_sha256')!=annotation['annotation_sha256']:
        raise ValueError('Photo or annotation binding mismatch')
    if row.get('photo_decision') not in {'pending','keep','reject'}:raise ValueError('Invalid photo decision')
    labels=parse_labels(json.dumps(row.get('labels')))
    dimensions=row.get('dimensions')
    if not isinstance(dimensions,dict) or set(dimensions)!=set(VOCAB):raise ValueError('Missing review dimensions')
    for group,d in dimensions.items():
        if not isinstance(d,dict) or d.get('status') not in STATES or not isinstance(d.get('note'),str) or len(d['note'])>4000:raise ValueError('Invalid dimension decision')
        if d['status']=='not_applicable' and (group not in {'expression','pose'} or any(v!='not_applicable' for v in labels[group].values())):raise ValueError('Inconsistent not-applicable decision')
        if d['status']=='approve':
            if labels[group]!=annotation['labels'][group]:raise ValueError('Edited labels must be explicitly corrected')
            if any(error.startswith(group+':') for error in annotation.get('validation_error') or []):raise ValueError('Invalid model group requires explicit human correction')
    target=row.get('target_subject')
    if target is not None:
        if not isinstance(target,dict) or target.get('kind') not in {'person','object','landmark'} or not isinstance(target.get('id'),str) or not target['id'] or len(target['id'])>80:raise ValueError('Invalid review target')
        subject=Box.parse(target.get('box'))
        for group in ('expression','pose'):
            na=any(v=='not_applicable' for v in labels[group].values())
            if (target['kind']=='person' and na) or (target['kind']!='person' and any(v!='not_applicable' for v in labels[group].values())):raise ValueError('Person labels outside selected target scope')
    research=row.get('composition_research')
    if not isinstance(research,dict) or not isinstance(research.get('windows'),list) or len(research['windows'])>150:raise ValueError('Invalid composition review')
    ids=set();boxes=[]
    for window in research['windows']:
        if not isinstance(window,dict) or not isinstance(window.get('id'),str) or not window['id'] or window['id'] in ids or window.get('decision') not in {'pending','keep','reject'}:raise ValueError('Invalid or duplicate crop')
        box=Box.parse(window.get('box'))
        if target is None or not box.contains(subject):raise ValueError('Crop must contain the selected subject')
        if any(iou(box.values(),b)>=.9 for b in boxes):raise ValueError('Near-duplicate crop windows')
        ids.add(window['id']);boxes.append(box.values())
        if window.get('aesthetic_score') is not None:raise ValueError('This tool does not provide learned aesthetic scores')
    best=research.get('best_id')
    if best is not None and not any(w['id']==best and w['decision']=='keep' for w in research['windows']):raise ValueError('Best crop must be explicitly kept')
    return row


def validate_export(payload, manifest, annotations):
    if not isinstance(payload,dict) or payload.get('schema_version')!=2 or payload.get('dataset_id')!=manifest['dataset_id'] or payload.get('manifest_sha256')!=digest(manifest) or payload.get('analysis_sha256')!=digest(annotations):raise ValueError('Dataset or analysis version mismatch')
    rows=payload.get('records')
    if not isinstance(rows,list) or len(rows)>len(manifest['items']):raise ValueError('Invalid review list')
    items={i['id']:i for i in manifest['items']};drafts={a['id']:a for a in annotations};seen=set()
    for row in rows:
        if not isinstance(row,dict) or row.get('id') not in items or row['id'] in seen:raise ValueError('Unknown or duplicate photo')
        seen.add(row['id']);validate_record(row,items[row['id']],drafts[row['id']])
    return rows
