"""Collect the latest Maven reports and benchmark with a source fingerprint."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', help='New evidence directory; refuses to overwrite an existing run')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    destination = Path(args.output).resolve()
    reports = sorted(root.glob('*/target/surefire-reports/TEST-*.xml'))
    if not reports:
        parser.error('No Surefire XML reports found; run mvn clean verify first')
    suites = []
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in reports:
        suite = ET.parse(report).getroot()
        counts = {key: int(suite.get(key, '0')) for key in totals}
        for key, value in counts.items():
            totals[key] += value
        suites.append({'module': report.relative_to(root).parts[0], 'name': suite.get('name'),
                       **counts, 'seconds': float(suite.get('time', '0')),
                       'cases': [{'name': case.get('name'), 'seconds': float(case.get('time', '0')),
                                  'status': next((tag for tag in ('failure', 'error', 'skipped') if case.find(tag) is not None), 'passed')}
                                 for case in suite.findall('testcase')]})
    coverage = {}
    for report in sorted(root.glob('*/target/site/jacoco/jacoco.xml')):
        coverage[report.relative_to(root).parts[0]] = {
            counter.get('type'): {'missed': int(counter.get('missed')), 'covered': int(counter.get('covered'))}
            for counter in ET.parse(report).getroot().findall('counter')}
    sources = {}
    names = git(root, 'ls-files', '--cached', '--others', '--exclude-standard').splitlines()
    for name in sorted(set(names)):
        path = root / name
        if path.is_file() and (path.suffix in {'.java', '.py', '.js', '.jsx', '.yml', '.yaml'} or path.name == 'pom.xml'):
            sources[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = json.dumps(sources, sort_keys=True).encode('utf-8')
    result = {'collectedUtc': dt.datetime.now(dt.timezone.utc).isoformat(),
              'baseCommit': git(root, 'rev-parse', 'HEAD'),
              'workingTree': git(root, 'status', '--short'),
              'sourceFingerprintSha256': hashlib.sha256(manifest).hexdigest(),
              'command': 'mvn -B -ntp -Dportfolio.benchmark=true clean verify',
              'note': 'Reports collected from the last local run; clean verification prevents stale test reports.',
              'totals': totals, 'suites': suites, 'coverageByModule': coverage, 'sourceSha256': sources}
    destination.mkdir(parents=True, exist_ok=False)
    (destination / 'verification.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    benchmark = root / 'payment-ingestion-service/target/evidence/local-smoke.json'
    if benchmark.exists():
        shutil.copyfile(benchmark, destination / 'local-smoke.json')
    print(json.dumps(totals))
    print(destination)
    return 1 if totals['failures'] or totals['errors'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
