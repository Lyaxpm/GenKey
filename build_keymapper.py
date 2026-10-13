#!/usr/bin/env python3
"""Builder portable. Gunakan GitHub Actions apabila tidak ada Android SDK / Gradle lokal."""
import os
import shutil
import subprocess
import sys

os.chdir(os.path.dirname(os.path.abspath(__file__)))
gradle = shutil.which('gradle')
if gradle is None:
    print('Gradle tidak ditemukan. Push source ke GitHub dan jalankan Actions > Build GenKeymapper Android APK > Run workflow.')
    sys.exit(2)
print('Building Android APK with Gradle...')
sys.exit(subprocess.call([gradle, '--no-daemon', ':app:assembleDebug']))
