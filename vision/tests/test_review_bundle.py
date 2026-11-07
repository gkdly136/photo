import base64
import hashlib
from io import BytesIO
import importlib.util
from pathlib import Path
import tempfile
import unittest
from PIL import Image

script = Path(__file__).resolve().parents[1] / "scripts/build_dataset_review.py"
spec = importlib.util.spec_from_file_location("review_builder_test", script)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class EmbeddedPreviewTests(unittest.TestCase):
    def test_preview_decodes_without_an_image_directory_and_original_is_unchanged(self):
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "photo.jpg"
            Image.new("RGB", (1600, 1200), "#8aaf92").save(source)
            before = hashlib.sha256(source.read_bytes()).hexdigest()
            uri = builder.embedded_preview(source)
            self.assertTrue(uri.startswith("data:image/jpeg;base64,"))
            pixels = base64.b64decode(uri.split(",", 1)[1], validate=True)
            with Image.open(BytesIO(pixels)) as embedded:
                embedded.load()
                self.assertEqual(embedded.size, (960, 720))
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), before)

    def test_exif_orientation_is_baked_into_embedded_preview(self):
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "rotated.jpg"
            exif = Image.Exif(); exif[274] = 6
            Image.new("RGB", (160, 120)).save(source, exif=exif)
            uri = builder.embedded_preview(source)
            with Image.open(BytesIO(base64.b64decode(uri.split(",", 1)[1]))) as embedded:
                self.assertEqual(embedded.size, (120, 160))
                self.assertIsNone(embedded.getexif().get(274))


if __name__ == "__main__": unittest.main()
