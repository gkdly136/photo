"""One-time bootstrap export from the Gradle build host, containing only public wrapper files."""
import base64
import json
from pathlib import Path

paths = ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
print("FRAMECOACH_WRAPPER=" + json.dumps({p: base64.b64encode(Path(p).read_bytes()).decode("ascii") for p in paths}))
