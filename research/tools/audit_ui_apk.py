"""Static APK UI inventory. Does not install/execute the APK or its bundled code."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from android_resources import arsc, axml
from dex_audit import Dex


def audit(apk: Path):
    if apk.stat().st_size > 128 * 1024 * 1024:
        raise ValueError('APK exceeds offline analysis budget')
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        if len(entries) > 10000 or sum(e.file_size for e in entries) > 256 * 1024 * 1024:
            raise ValueError('APK entry budget exceeded')
        if len({e.filename for e in entries}) != len(entries):
            raise ValueError('Duplicate APK entry')
        if archive.testzip() is not None:
            raise ValueError('CRC failure')
        manifest = ET.fromstring(axml(archive.read('AndroidManifest.xml'), {}))
        android = '{http://schemas.android.com/apk/res/android}'
        resources, _ = arsc(archive.read('resources.arsc'))
        wanted = {'settings_color_style', 'settings_enable_glass', 'settings_enable_blur',
                  'settings_floating_bottom_bar', 'settings_background_blur',
                  'theme_preview_caption_miuix', 'theme_preview_caption_material'}
        evidence = [{'name': row['name'], 'value': row['value'], 'resource_id': hex(row['id'])}
                    for row in resources if row['name'].removeprefix('string/') in wanted
                    and not any(bytes.fromhex(row['config_hex'])[4:])]
        with tempfile.TemporaryDirectory(prefix='ote-ui-audit-') as work:
            dex_path = Path(work) / 'classes.dex'
            dex_path.write_bytes(archive.read('classes.dex'))
            dex = Dex(dex_path)
            calls = []
            for method in dex.index():
                refs = [r for r in method['refs'] if r['kind'] == 'string' and re.search(
                    r'LiquidGlassLens|enable_glass|enable_blur|color_style|miuix_monet|floating_bottom_bar|Miuix|Material', r['value'])]
                if refs:
                    calls.append({'name': method['name'], 'id': method['id'], 'refs': refs})
    return {
        'analysis_date': datetime.now(timezone.utc).date().isoformat(),
        'file': apk.name, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'size': apk.stat().st_size, 'zip_entries': len(entries), 'zip_crc_valid': True,
        'package': manifest.attrib['package'], 'version_name': manifest.attrib[android + 'versionName'],
        'version_code': manifest.attrib[android + 'versionCode'],
        'sdk': {k.removeprefix(android): v for k, v in manifest.find('uses-sdk').attrib.items()},
        'inspection': 'Static APK only; not executed; signer and source-to-binary relationship not authenticated.',
        'default_ui_resources': evidence, 'dex_class_count': len(dex.classes),
        'dex_defined_methods': len(dex.defs), 'dex_string_count': len(dex.strings), 'ui_callsites': calls,
        'limitations': ['Obfuscated symbols; runtime layout not measured.',
                       'Resource/string presence alone does not prove a live screen.',
                       'No proof that this APK bundles Glasmio.',
                       'No SukiSU root/module/kernel behavior is reused.'],
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    report = audit(args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: report[k] for k in ['sha256', 'package', 'version_name', 'zip_crc_valid', 'dex_class_count']}))
