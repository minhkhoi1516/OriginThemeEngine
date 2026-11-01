"""Offline structural checks; not a Kotlin compiler or Android runtime test."""
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def main():
    checks=[]
    sources=list(ROOT.rglob('*.kt'))
    assert len(sources)>=20
    checks.append('modular Kotlin source tree exists')
    resources={}
    for locale in ['values','values-vi','values-ja']:
        entries={}
        for resource_file in sorted((ROOT/f'app/src/main/res/{locale}').glob('*.xml')):
            xml=ET.parse(resource_file).getroot()
            for node in xml.findall('string'):
                assert node.attrib['name'] not in entries,(resource_file,node.attrib['name'])
                entries[node.attrib['name']]=''.join(node.itertext())
        resources[locale]=entries
    base=resources['values']
    for locale,data in resources.items():
        assert set(data)==set(base),(locale,set(base)-set(data),set(data)-set(base))
        for key,text in data.items():
            assert sorted(re.findall(r'%[0-9$]*[sdf]',text))==sorted(re.findall(r'%[0-9$]*[sdf]',base[key])),(locale,key)
    checks.append(f'EN/VI/JA string key and placeholder parity: {len(base)}')
    for source in sources:
        text=source.read_text()
        for key in re.findall(r'R\.string\.([A-Za-z0-9_]+)',text): assert key in base,(source,key)
        if '/ui/' in str(source): assert not re.search(r'\bText\(\s*"',text),source
    checks.append('UI string references exist; no direct Text literal labels')
    for path in ROOT.rglob('*.xml'):
        if '/build/' not in str(path): ET.parse(path)
    checks.append('all project XML parses')
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    permissions={p.attrib.get('{http://schemas.android.com/apk/res/android}name') for p in manifest.findall('uses-permission')}
    assert not permissions & {'android.permission.INTERNET','android.permission.MANAGE_EXTERNAL_STORAGE','android.permission.WRITE_EXTERNAL_STORAGE'}
    checks.append('local-only manifest without network/broad-storage permissions')
    transaction=(ROOT/'engine/device/src/main/kotlin/dev/origin/ote/device/FixedTransaction.kt').read_text()
    for forbidden in ['rm -rf','/dev/block','settings put','setenforce','mount -o','reboot','fastboot','dd if=']:
        assert forbidden not in transaction,forbidden
    checks.append('fixed transaction excludes destructive/system/biometric commands')
    assert 'setContent { OteApp() }' in (ROOT/'app/src/main/kotlin/dev/origin/ote/MainActivity.kt').read_text()
    assert 'HiltAndroidApp' in (ROOT/'app/src/main/kotlin/dev/origin/ote/OteApplication.kt').read_text()
    checks.append('Compose entrypoint and Hilt application wired')
    schemas=ROOT/'docs/otepack-v1.schema.json'
    assert json.loads(schemas.read_text())
    checks.append('versioned pack JSON schema parses')
    tests=sum(len(re.findall(r'@Test\b',p.read_text())) for p in ROOT.rglob('*Test.kt'))
    result={'passed':len(checks),'checks':checks,'kotlin_files':len(sources),'kotlin_test_methods_written':tests,
      'scope':'Offline structure/resource/policy checks ONLY. Kotlin compilation, Gradle dependencies, APK and Android runtime not verified by this script.'}
    out=ROOT/'build/reports/project-structure.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))
if __name__=='__main__':main()
