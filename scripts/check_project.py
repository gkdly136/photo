"""Read-only consistency checks; Android compilation and tests run separately."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
xml_files = list((root / "app/src/main").rglob("*.xml"))
for path in xml_files:
    ET.parse(path)

registry = (root / "app/src/main/java/com/framecoach/app/Knowledge.kt").read_text(encoding="utf-8")
ids = re.findall(r'Source\("([^"]+)"', registry)
assert len(ids) == len(set(ids)), "Duplicate source ids"
coach = (root / "core/src/main/kotlin/com/framecoach/core/Coach.kt").read_text(encoding="utf-8")
references = set()
for block in re.findall(r'listOf\(([^)]+)\)', coach):
    references.update(re.findall(r'"([^"]+)"', block))
assert not references - set(ids), f"Unresolved source ids: {references - set(ids)}"

test_count = sum(p.read_text(encoding="utf-8").count("@Test") for p in (root / "core/src/test").rglob("*.kt"))
print(f"Parsed {len(xml_files)} XML files; resolved {len(references)} source references in {len(ids)} entries.")
print(f"Found {test_count} JVM test methods (this script does not execute them).")
