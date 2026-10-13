#!/usr/bin/env python3
"""Build with the repository's pinned Gradle wrapper (JDK 17 + Android SDK 35)."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent
wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
raise SystemExit(subprocess.call([str(wrapper), "--no-daemon", ":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"], cwd=root))
