"""Capture repeatable k6 evidence; Python standard library only."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import time
import urllib.request
import uuid


def output(command, root):
    result = subprocess.run(command, cwd=root, text=True, capture_output=True, check=False)
    return (result.stdout + result.stderr).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8081')
    parser.add_argument('--rate', type=int, default=100)
    parser.add_argument('--duration', default='2m')
    parser.add_argument('--vus', type=int, default=50)
    parser.add_argument('--max-vus', type=int, default=400)
    args = parser.parse_args()
    if args.rate <= 0 or args.vus <= 0 or args.max_vus < args.vus:
        parser.error('rate and vus must be positive; max-vus must be at least vus')
    if not shutil.which('k6'):
        parser.error('Install k6 and add it to PATH before running a benchmark')
    root = Path(__file__).resolve().parents[1]
    run_id = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8]
    destination = root / 'performance' / 'results' / run_id
    destination.mkdir(parents=True)
    command = ['k6', 'run', '-e', f'BASE_URL={args.base_url}', '-e', f'RUN_ID={run_id}',
               '-e', f'RATE={args.rate}', '-e', f'DURATION={args.duration}',
               '-e', f'VUS={args.vus}', '-e', f'MAX_VUS={args.max_vus}',
               '-e', f'SUMMARY_PATH={(destination / "summary.json").as_posix()}',
               str(root / 'performance' / 'k6-payments-1000tps.js')]
    metadata = {'runId': run_id, 'timestampUtc': dt.datetime.now(dt.timezone.utc).isoformat(),
                'commit': output(['git', 'rev-parse', 'HEAD'], root),
                'workingTree': output(['git', 'status', '--short'], root),
                'loadGeneratorOs': platform.platform(), 'logicalProcessors': os.cpu_count(),
                'k6': output(['k6', 'version'], root), 'command': command,
                'scope': 'HTTP ingestion acknowledgement; service topology/hardware must be documented separately'}
    (destination / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    # Only scrape localhost services you started or a target explicitly provided by the caller.
    with (destination / 'k6.log').open('w', encoding='utf-8') as log, \
            (destination / 'prometheus-samples.jsonl').open('w', encoding='utf-8') as samples:
        process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        try:
            while process.poll() is None:
                sample = {'timestampUtc': dt.datetime.now(dt.timezone.utc).isoformat()}
                try:
                    with urllib.request.urlopen(args.base_url.rstrip('/') + '/actuator/prometheus', timeout=2) as response:
                        sample['prometheus'] = response.read().decode('utf-8')
                except Exception as error:
                    sample['scrapeError'] = str(error)
                samples.write(json.dumps(sample) + '\n')
                samples.flush()
                time.sleep(2)
        except KeyboardInterrupt:
            process.terminate()
            process.wait()
            raise
    metadata['exitCode'] = process.returncode
    metadata['completedUtc'] = dt.datetime.now(dt.timezone.utc).isoformat()
    (destination / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(f'Evidence: {destination}\nk6 exit code: {process.returncode}')
    return process.returncode


if __name__ == '__main__':
    raise SystemExit(main())
