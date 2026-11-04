"""Verify handoff links, evidence hashes and declared scope; not Android QA."""
from pathlib import Path
import hashlib
import json
import re
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]


def main():
    index = json.loads((ROOT / 'research/evidence-index.json').read_text())
    for name, expected in index['files'].items():
        path = ROOT / name
        assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected, name
    documents = [ROOT / 'README.md', *sorted((ROOT / 'docs/redesign').glob('*.md')),
                 *sorted((ROOT / 'prompts').glob('*.md'))]
    links = 0
    for path in documents:
        for target in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            if target.startswith(('https://', 'http://', '#')):
                continue
            resolved = (path.parent / unquote(target.split('#')[0])).resolve()
            assert resolved.is_relative_to(ROOT) and resolved.exists(), (path.name, target)
            links += 1
    inputs = json.loads((ROOT / 'research/inputs.json').read_text())
    for item in inputs['files']:
        assert re.fullmatch(r'[a-f0-9]{64}', item['sha256'])
        assert item['bytes'] > 0 and item['published'] is False
        assert '/' not in item['private_filename'] and '\\' not in item['private_filename']
    sources = json.loads((ROOT / 'research/evidence/ui-upstream-sources.json').read_text())
    for source in sources['sources']:
        assert re.fullmatch(r'[a-f0-9]{40}', source['commit'])
        assert re.fullmatch(r'[a-f0-9]{40}', source['blob_sha'])
    report = {'evidence_files': len(index['files']), 'local_links': links,
              'private_input_records': len(inputs['files']), 'upstream_files': len(sources['sources']),
              'scope': 'Document links and file hashes only; not source compilation, dependency resolution or device QA.'}
    output = ROOT / 'build/reports/handoff.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
