"""Second, versioned pass: visible expression, composition, light and pose."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import time
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"vision"))
from framecoach_vision.backend import QwenObserver
from framecoach_vision.dataset import digest
from framecoach_vision.domain import Box, InvalidObservation
from framecoach_vision.photo_labels import SCENE_SCHEMA, PERSON_SCHEMA, SCENE_PROMPT, PERSON_PROMPT, assemble_labels, conservative_pose
from framecoach_vision.subject_composition import candidates


def target_for(old):
    scene = old.get("perception") or {"subjects":[]}
    selected = old["guidance"].get("selected_subject_id")
    return next((s for s in scene["subjects"] if s["id"] == selected), None)


def brightness(image, subject):
    gray=image.convert("L"); gray.thumbnail((256,256)); hist=gray.histogram(); n=sum(hist)
    def quantile(q):
        total=0
        for i,count in enumerate(hist):
            total+=count
            if total>=q*n:return round(i/255,4)
    data={"method":"rendered_luma_percentiles_v1","p05":quantile(.05),"p50":quantile(.5),"p95":quantile(.95),
          "meaning":"显示图灰度分位数，不是 RAW 动态范围、曝光是否正确或现场照度。"}
    data["p95_minus_p05"]=round(data["p95"]-data["p05"],4)
    if subject:
        b=subject["box"]; w,h=gray.size
        coords=(max(0,int(b[0]*w/1000)),max(0,int(b[1]*h/1000)),min(w,int(b[2]*w/1000)),min(h,int(b[3]*h/1000)))
        if coords[2]>coords[0] and coords[3]>coords[1]:
            inside=gray.crop(coords); sh=inside.histogram(); count=sum(sh)
            data["subject_box_mean"]=round(sum(i*v for i,v in enumerate(sh))/count/255,4)
            if n>count: data["outside_box_mean"]=round((sum(i*v for i,v in enumerate(hist))-sum(i*v for i,v in enumerate(sh)))/(n-count)/255,4)
        data["subject_box_note"]="这是主体矩形内外的均值，矩形不是像素分割，也不代表脸部曝光。"
    return data


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--batch",default="vision/data/batches/batch-001")
    parser.add_argument("--limit",type=int)
    parser.add_argument("--batch-size",type=int,choices=[1,2,4],default=4)
    parser.add_argument("--output-tag",default="analysis-v2-evidence-final")
    args=parser.parse_args(); folder=Path(args.batch)
    if not args.output_tag.startswith("analysis-v2") or Path(args.output_tag).name != args.output_tag:raise ValueError("Invalid analysis version")
    manifest=json.loads((folder/"manifest.json").read_text(encoding="utf-8"))
    old={a["id"]:a for a in [json.loads(line) for line in (folder/"annotations.jsonl").read_text(encoding="utf-8").splitlines()]}
    output=folder/args.output_tag; output.mkdir(exist_ok=True)
    prompt_sha=digest({"prompts":[SCENE_PROMPT,PERSON_PROMPT],"schemas":[SCENE_SCHEMA,PERSON_SCHEMA],"version":"2.3-evidence-before-taxonomy"})
    todo=[]
    for item in manifest["items"]:
        path=output/(item["id"]+".json")
        if path.exists():
            saved=json.loads(path.read_text(encoding="utf-8"))
            if saved["image_sha256"]!=item["sha256"] or saved["prompt_sha256"]!=prompt_sha:raise ValueError("Version mismatch; do not overwrite reviews")
        else:todo.append(item)
    if args.limit is not None:todo=todo[:args.limit]
    if todo:
        observer=QwenObserver(device="cuda:0")
        for offset in range(0,len(todo),args.batch_size):
            batch=todo[offset:offset+args.batch_size]; images=[]; prompts=[]; targets=[]
            for item in batch:
                file=folder/item["image_path"]
                if hashlib.sha256(file.read_bytes()).hexdigest()!=item["sha256"]:raise ValueError("Source image changed")
                with Image.open(file) as im:images.append(ImageOps.exif_transpose(im).convert("RGB"))
                target=target_for(old[item["id"]]); targets.append(target)
                prompt="Annotate THIS whole photo's composition and light. "
                if target:
                    prompt+=f"Selected subject: kind={target['kind']}, id={target['id']}, normalized box={target['box']}. "
                else:prompt+="No reliable subject box is supplied; subject separation may be unknown. "
                prompts.append(prompt)
            started=time.perf_counter()
            try:responses=observer.generate_json_many(images,SCENE_PROMPT,SCENE_SCHEMA,prompts,max_new_tokens=420)
            except observer.torch.cuda.OutOfMemoryError:
                observer.torch.cuda.empty_cache()
                responses=[observer.generate_json_many([im],SCENE_PROMPT,SCENE_SCHEMA,[prompt],420)[0] for im,prompt in zip(images,prompts)]
            person_indices=[i for i,t in enumerate(targets) if t and t["kind"]=="person"]
            person_responses={}
            if person_indices:
                crops=[]
                for i in person_indices:
                    im=images[i]; b=targets[i]["box"]; w,h=im.size
                    # A small margin preserves limb context. This is analysis input only, not a new source asset.
                    crops.append(im.crop((max(0,int((b[0]-30)*w/1000)),max(0,int((b[1]-30)*h/1000)),min(w,int((b[2]+30)*w/1000)),min(h,int((b[3]+30)*h/1000)))))
                try:person_values=observer.generate_json_many(crops,PERSON_PROMPT,PERSON_SCHEMA,["Describe this selected person's visible face and body; do not guess cropped-out parts."]*len(crops),420)
                except observer.torch.cuda.OutOfMemoryError:
                    observer.torch.cuda.empty_cache()
                    person_values=[observer.generate_json_many([im],PERSON_PROMPT,PERSON_SCHEMA,["Describe this visible person."],420)[0] for im in crops]
                person_responses=dict(zip(person_indices,person_values))
            invalid=0
            for index,(item,image,target,(raw,stats)) in enumerate(zip(batch,images,targets,responses)):
                has_person=target is not None and target["kind"]=="person"
                person_raw,person_stats=person_responses.get(index,(None,None))
                labels,issues=assemble_labels(raw,person_raw,has_person)
                failure=issues or None
                if failure:invalid+=1
                scope_adjustments=[] if has_person else ["expression/pose: no selected person; not_applicable supplied by scope, not model"]
                scope_adjustments.extend(conservative_pose(labels))
                flags=[]
                if failure:flags.append("四维模型输出未通过一致性校验，已保留原始响应，显示待确认标签")
                if target is None:flags.append("主体未确认，请核对标注适用对象")
                if labels["expression"]["visibility"] in {"too_small","occluded","partial","unknown"}:flags.append("神态可见性不足，请勿把草标当作事实")
                if labels["light"]["direction"]!="unknown":flags.append("光源方向属于视觉推测，需要复核")
                if set(labels['composition']['categories']) >= {'centered','thirds'}:flags.append('模型同时标了居中和三分，请分别核对主体、眼线或地平线的依据；不成立的类别请删除')
                if has_person:flags.append('眼神左右按画面方向，模型可能左右混淆；请对照原图核实')
                subject_geometry=None
                if target:
                    b=Box.parse(target["box"]); subject_geometry={"box":b.values(),"center":list(b.center),"area_fraction":round(b.area/1e6,4)}
                record={"schema_version":2,"id":item["id"],"image_sha256":item["sha256"],"source_annotation_sha256":old[item["id"]]["annotation_sha256"],
                        "prompt_sha256":prompt_sha,"base_model":observer.lock["model_id"],"base_revision":observer.lock["revision"],
                        "status":"four_dimension_machine_draft_pending_human_review","training_eligible":False,
                        "target_subject":target,"labels":labels,"scope_adjustments":scope_adjustments,
                        "measured_light":brightness(image,target),"subject_geometry":subject_geometry,
                        "composition_research":candidates(target["box"] if target else None),
                        "raw_model_output":{"scene":raw,"person_crop":person_raw},"validation_error":failure,"performance":{"scene":stats,"person_crop":person_stats},"review_flags":flags}
                record["annotation_sha256"]=digest(record)
                path=output/(item["id"]+".json");tmp=path.with_suffix(".tmp")
                tmp.write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf-8");tmp.replace(path)
            print(json.dumps({"done":min(offset+len(batch),len(todo)),"total_this_run":len(todo),"seconds":round(time.perf_counter()-started,1),"invalid":invalid}),flush=True)
    records=[json.loads((output/(i["id"]+".json")).read_text(encoding="utf-8")) for i in manifest["items"] if (output/(i["id"]+".json")).exists()]
    (folder/(args.output_tag+".jsonl")).write_text("".join(json.dumps(r,ensure_ascii=False)+"\n" for r in records),encoding="utf-8")
    print(f"Saved {len(records)} four-dimension drafts; no training.",flush=True)


if __name__=="__main__":main()
