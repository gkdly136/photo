import copy
import json
import unittest
from framecoach_vision.dataset import digest
from framecoach_vision.domain import Box,InvalidObservation
from framecoach_vision.photo_labels import unknown_labels,parse_labels,assemble_labels,conservative_pose
from framecoach_vision.photo_review import validate_export
from framecoach_vision.subject_composition import grid_windows,candidates,iou


class PhotoLabelsTest(unittest.TestCase):
    def test_unreadable_face_cannot_claim_gaze(self):
        labels=unknown_labels(True);labels['expression'].update(visibility='too_small',gaze='camera')
        with self.assertRaises(InvalidObservation):parse_labels(json.dumps(labels))

    def test_bad_person_group_preserves_valid_scene_dimensions(self):
        labels=unknown_labels(True);scene={g:labels[g] for g in ('composition','light')};scene['composition']['categories']=['leading_lines','layers']
        person={g:labels[g] for g in ('expression','pose')};person['expression'].update(visibility='not_applicable',gaze='camera')
        result,issues=assemble_labels(json.dumps(scene),json.dumps(person),True)
        self.assertEqual(result['composition']['categories'],['leading_lines','layers']);self.assertEqual(result['expression']['gaze'],'unknown');self.assertTrue(issues)

    def test_nonperson_scope_is_explicit_not_guessed(self):
        labels=unknown_labels(False);scene={g:labels[g] for g in ('composition','light')}
        result,issues=assemble_labels(json.dumps(scene),None,False)
        self.assertFalse(issues);self.assertTrue(all(v=='not_applicable' for v in result['pose'].values()))

    def test_conservative_upper_body_downgrade_keeps_audit(self):
        labels=unknown_labels(True);labels['pose'].update(visibility='upper_body',stance='standing',legs='apart')
        changes=conservative_pose(labels)
        self.assertEqual(len(changes),2);self.assertEqual(labels['pose']['stance'],'unknown');self.assertEqual(labels['pose']['legs'],'occluded')

    def test_bad_category_or_back_gaze_rejected(self):
        labels=unknown_labels(True);labels['composition']['categories']=['centered','centered']
        with self.assertRaises(InvalidObservation):parse_labels(json.dumps(labels))
        labels=unknown_labels(True);labels['expression'].update(head='back',gaze='camera')
        with self.assertRaises(InvalidObservation):parse_labels(json.dumps(labels))


class CropStudyTest(unittest.TestCase):
    def test_sacd_inspired_grid_has_100_unique_windows(self):
        windows=grid_windows();self.assertEqual(len(windows),100);self.assertEqual(len({tuple(w) for w in windows}),100)
        for b in windows:Box.parse(b)

    def test_subject_preservation_and_meaningful_distinction(self):
        subject=[400,400,600,600];data=candidates(subject);boxes=[w['box'] for w in data['windows']]
        self.assertGreater(len(boxes),1)
        for i,b in enumerate(boxes):
            self.assertTrue(Box.parse(b).contains(Box.parse(subject)))
            self.assertTrue(all(iou(b,o)<.9 for o in boxes[:i]))
        self.assertFalse(data['trained_on_sacd']);self.assertTrue(all(w['preference'] is None and w['aesthetic_score'] is None for w in data['windows']))

    def test_no_forced_alternatives_for_frame_filling_subject(self):
        self.assertEqual(len(candidates([0,0,1000,1000])['windows']),1)
        self.assertEqual(candidates(None)['windows'],[])


class ReviewV2Test(unittest.TestCase):
    def fixture(self):
        item={'id':'FC-1','sha256':'photo'};labels=unknown_labels(True)
        annotation={'id':'FC-1','annotation_sha256':'draft','labels':copy.deepcopy(labels),'validation_error':None}
        manifest={'dataset_id':'synthetic','items':[item]};annotations=[annotation]
        row={'id':'FC-1','image_sha256':'photo','annotation_sha256':'draft','photo_decision':'keep','labels':labels,'dimensions':{g:{'status':'pending','note':''} for g in labels},'target_subject':{'id':'s1','kind':'person','box':[400,400,600,600]},'composition_research':{'windows':[dict(w,decision='pending') for w in candidates([400,400,600,600])['windows']],'best_id':None}}
        payload={'schema_version':2,'dataset_id':'synthetic','manifest_sha256':digest(manifest),'analysis_sha256':digest(annotations),'records':[row]}
        return payload,manifest,annotations

    def test_valid_pending_reviews_do_not_fabricate_preference(self):
        p,m,a=self.fixture();rows=validate_export(p,m,a);self.assertIsNone(rows[0]['composition_research']['best_id'])

    def test_reject_stale_binding_duplicates_and_fake_best(self):
        for mode in ('image','analysis','duplicate','best','near_duplicate'):
            p,m,a=self.fixture();r=p['records'][0]
            if mode=='image':r['image_sha256']='wrong'
            if mode=='analysis':p['analysis_sha256']='wrong'
            if mode=='duplicate':p['records'].append(copy.deepcopy(r))
            if mode=='best':r['composition_research']['best_id']=r['composition_research']['windows'][0]['id']
            if mode=='near_duplicate':r['composition_research']['windows'].append(dict(r['composition_research']['windows'][0],id='duplicate'))
            with self.assertRaises(ValueError,msg=mode):validate_export(p,m,a)

    def test_invalid_model_group_needs_explicit_correction(self):
        p,m,a=self.fixture();a[0]['validation_error']=['expression: invalid output'];p['analysis_sha256']=digest(a);p['records'][0]['dimensions']['expression']['status']='approve'
        with self.assertRaises(ValueError):validate_export(p,m,a)

    def test_person_scope_and_changed_labels_cannot_be_approved_as_original(self):
        p,m,a=self.fixture();r=p['records'][0];r['labels']['expression']['gaze']='camera';r['dimensions']['expression']['status']='approve'
        with self.assertRaises(ValueError):validate_export(p,m,a)
        r['dimensions']['expression']['status']='corrected';validate_export(p,m,a)
        r['target_subject']['kind']='landmark'
        with self.assertRaises(ValueError):validate_export(p,m,a)


if __name__=='__main__':unittest.main()
