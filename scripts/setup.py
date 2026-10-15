#!/usr/bin/env python3
"""Clone pinned dependencies, or register existing clean JLab checkouts."""
import argparse
from pathlib import Path
import subprocess
from common import ROOT, PINS, check_repo, write_json

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--coatjava',type=Path,help='Existing clean checkout at the pinned port commit')
p.add_argument('--bandsoft',type=Path,help='Existing clean C++ reference checkout')
a=p.parse_args()
cfg={}
for name,existing in [('coatjava',a.coatjava),('bandsoft_tools',a.bandsoft)]:
    dest=existing.resolve() if existing else ROOT/'deps'/name
    if not dest.exists():
        if existing:
            raise FileNotFoundError(dest)
        dest.parent.mkdir(parents=True,exist_ok=True)
        subprocess.run(['git','clone','--filter=blob:none','--no-checkout',PINS[name]['url'],str(dest)],check=True)
        subprocess.run(['git','-C',str(dest),'checkout','--detach',PINS[name]['commit']],check=True)
    cfg[name]=str(check_repo(dest,name))
write_json(ROOT/'local-config.json',cfg)
print('Configured pinned source checkouts. Build and run on the JLab stack.')
