#!/usr/bin/env python3
"""Stage a fixed phone or TV build without mutating the shared source tree."""
from pathlib import Path
import argparse
import shutil


def prepare(project: Path, destination: Path, variant: str) -> None:
    if variant not in ('phone', 'tv'):
        raise ValueError('variant must be phone or tv')
    if destination.exists():
        raise ValueError(f'staging destination already exists: {destination}')
    shutil.copytree(project / 'app/src/main', destination)
    package = Path('java/com/zynelabs/flowplay')
    if variant == 'phone':
        # The phone settings/player layout stays at 1.1.35; drawer icons use the shared helper.
        shutil.copy2(project / 'app/src/phone' / package / 'PlayerScreen.kt',
                     destination / package / 'PlayerScreen.kt')
    main = destination / package / 'MainActivity.kt'
    content = main.read_text()
    detection = 'DzTv.isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION'
    if content.count(detection) != 1:
        raise ValueError('device-mode assignment changed; review variant preparation')
    content = content.replace(detection, f'DzTv.isTv = {str(variant == "tv").lower()} // Fixed {variant} APK; no automatic layout switching.')
    main.write_text(content)
    if variant == 'phone':
        manifest = destination / 'AndroidManifest.xml'
        manifest.write_text(manifest.read_text().replace(
            '                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n', ''))
    else:
        strings = destination / 'res/values/strings.xml'
        strings.write_text(strings.read_text().replace('>DZ FlowPlay<', '>DZ FlowPlay TV<'))
    print(f'Staged fixed {variant} sources: {destination}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True, type=Path)
    parser.add_argument('--destination', required=True, type=Path)
    parser.add_argument('--variant', required=True, choices=['phone', 'tv'])
    args = parser.parse_args()
    prepare(args.project.resolve(), args.destination.resolve(), args.variant)
