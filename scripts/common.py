"""Shared local configuration and logged subprocesses (Python standard library)."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PINS = json.loads((ROOT/'versions.json').read_text())

def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name+'.partial')
    tmp.write_text(json.dumps(value, indent=2, sort_keys=True)+'\n')
    tmp.replace(path)

def frozen(path, value):
    path = Path(path)
    if path.exists():
        if json.loads(path.read_text()) != value:
            raise RuntimeError(f'Configuration changed; preserve {path.parent} and choose a new output directory')
    else:
        write_json(path, value)

def check_repo(path, name):
    path = Path(path).resolve()
    head = subprocess.check_output(['git','-C',str(path),'rev-parse','HEAD'], text=True).strip()
    dirty = subprocess.check_output(['git','-C',str(path),'status','--porcelain'], text=True).strip()
    if head != PINS[name]['commit'] or dirty:
        raise RuntimeError(f'{name}: requires clean pinned checkout {PINS[name]["commit"]}; found {head}, dirty={bool(dirty)}')
    return path

def source_hashes():
    return {str(p.relative_to(ROOT)):sha(p) for folder in ('harness','scripts')
            for p in sorted((ROOT/folder).iterdir()) if p.suffix in ('.py','.java','.cpp')}

def run(command, log, env=None, cwd=None):
    """Persist logs/exit receipts and report progress even during quiet builds."""
    command = list(map(str, command))
    log = Path(log)
    log.parent.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()
    print('RUN', ' '.join(command), flush=True)
    with log.open('ab') as stream:
        proc = subprocess.Popen(command, cwd=cwd or ROOT, env=env,
                                stdout=stream, stderr=subprocess.STDOUT)
        offset = 0
        while True:
            try:
                code = proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                code = None
            size = log.stat().st_size
            with log.open('rb') as read:
                read.seek(max(offset, size-2500))
                tail = read.read().decode(errors='replace')
            if tail.strip():
                print(tail.rstrip(), flush=True)
            offset = size
            print(f'PROGRESS elapsed={time.monotonic()-start:.1f}s log_bytes={size} {log.name}', flush=True)
            if code is not None:
                break
    write_json(str(log)+'.receipt.json', {'command':command,'returncode':code,
               'seconds':round(time.monotonic()-start,3),'log_sha256':sha(log)})
    if code:
        raise subprocess.CalledProcessError(code, command)

def config():
    cfg = json.loads((ROOT/'local-config.json').read_text())
    for key in ('coatjava','bandsoft_tools'):
        check_repo(cfg[key],key)
    return cfg

def runtime(cfg):
    env = dict(os.environ, CLAS12DIR=cfg['coatjava'], BANDSOFT_TOOLS_DIR=cfg['bandsoft_tools'])
    return env
