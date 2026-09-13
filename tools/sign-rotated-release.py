#!/usr/bin/env python3
"""Sign a verified Android 9+ APK with the new private key and existing rotation lineage."""
import argparse
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
required = ['ANDROID_BUILD_TOOLS', 'STREAMY_KEYSTORE', 'STREAMY_STORE_PASSWORD', 'STREAMY_KEY_PASSWORD', 'STREAMY_LINEAGE', 'STREAMY_PREVIOUS_APK']
missing = [key for key in required if not os.environ.get(key)]
if missing:
    parser.error('Missing environment variables: ' + ', '.join(missing))
tools = Path(os.environ['ANDROID_BUILD_TOOLS'])
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
minimum = re.search(r"sdkVersion:'(\d+)'", badging)
if not minimum or int(minimum.group(1)) < 28:
    parser.error('Build this APK with -PstreamyMinSdk=28. Secure in-place rotation requires Android 9+.')
subprocess.run([
    str(tools / 'apksigner'), 'sign', '--ks', os.environ['STREAMY_KEYSTORE'],
    '--ks-key-alias', os.environ.get('STREAMY_KEY_ALIAS', 'streamy'),
    '--ks-pass', 'env:STREAMY_STORE_PASSWORD', '--key-pass', 'env:STREAMY_KEY_PASSWORD',
    '--lineage', os.environ['STREAMY_LINEAGE'], '--rotation-min-sdk-version', '28',
    '--min-sdk-version', str(minimum.group(1)), '--v1-signing-enabled', 'false',
    '--v2-signing-enabled', 'false', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
    '--out', str(args.output), str(args.apk)
], check=True)
subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.output)], check=True)
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
subprocess.run([java, '-cp', str(tools / 'lib/apksigner.jar'),
                str(Path(__file__).with_name('VerifyUpgrade.java')),
                os.environ['STREAMY_PREVIOUS_APK'], str(args.output), str(tools / 'aapt')], check=True)
