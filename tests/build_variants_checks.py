"""Validate fixed Phone/TV APK source sets before compilation."""
from pathlib import Path
from tempfile import TemporaryDirectory
import sys

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root))
from prepare_variant import prepare

pkg = Path('java/com/zynelabs/flowplay')
original_player = (root / 'app/src/phone' / pkg / 'PlayerScreen.kt').read_bytes()
original_main = (root / 'app/src/main' / pkg / 'MainActivity.kt').read_text()
detection = 'DzTv.isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION'
with TemporaryDirectory() as tmp:
    for variant in ('phone', 'tv'):
        dest = Path(tmp) / variant
        prepare(root, dest, variant)
        main = (dest / pkg / 'MainActivity.kt').read_text()
        fixed = f'DzTv.isTv = {str(variant == "tv").lower()} // Fixed {variant} APK; no automatic layout switching.'
        assert fixed in main and detection not in main
        assert main.replace(fixed, detection) == original_main
        player = (dest / pkg / 'PlayerScreen.kt').read_bytes()
        if variant == 'phone':
            assert player == original_player
            assert b'TvChoiceGrid' not in player
            assert 'LEANBACK_LAUNCHER' not in (dest / 'AndroidManifest.xml').read_text()
        else:
            assert b'private fun TvChoiceGrid' in player
            assert 'LEANBACK_LAUNCHER' in (dest / 'AndroidManifest.xml').read_text()
            assert '>DZ FlowPlay TV<' in (dest / 'res/values/strings.xml').read_text()
        # Unrelated production files are byte-identical in both APK source sets.
        allowed = {pkg / 'MainActivity.kt', pkg / 'PlayerScreen.kt',
                   Path('AndroidManifest.xml'), Path('res/values/strings.xml')}
        for source in (root / 'app/src/main').rglob('*'):
            if source.is_file():
                rel = source.relative_to(root / 'app/src/main')
                if rel not in allowed:
                    assert (dest / rel).read_bytes() == source.read_bytes(), (variant, rel)
        # Same package and signature path preserve upgrade/data compatibility.
        assert 'package="com.zynelabs.flowplay"' in (dest / 'AndroidManifest.xml').read_text()
print('PASS: separate fixed Phone/TV source sets; no runtime device auto-switch')
print('PASS: Phone player layout preserved; only shared drawer icon extraction; new panels TV-only')
print('PASS: shared playback/data/cache/resources unchanged; package preserved')
