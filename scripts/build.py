#!/usr/bin/env python3
"""Build external drivers and the pinned Java BAND module on JLab/Linux."""
import argparse
import os
from pathlib import Path
import platform
import shlex
import subprocess
import sys
from common import ROOT, config, frozen, run, sha, source_hashes, write_json

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--jobs',type=int,default=2)
p.add_argument('--maven-cache',type=Path,default=ROOT/'build/maven-cache')
p.add_argument('--reuse-cpp-build',type=Path,help='Optional existing CMake build of the pinned C++ source')
p.add_argument('--java-dependencies',type=Path,help='Optional existing Maven dependency classpath text file')
a=p.parse_args()
if platform.system()!='Linux':
    sys.exit('Builds and reconstruction tests belong on JLab/Linux with its existing software stack.')
cfg=config(); cj=Path(cfg['coatjava']); bs=Path(cfg['bandsoft_tools'])
b=ROOT/'build'; b.mkdir(exist_ok=True); logs=b/'logs'; classes=b/'classes';classes.mkdir(exist_ok=True)
if a.jobs<1: p.error('--jobs must be positive')
mcache=a.maven_cache.resolve(); mcache.mkdir(parents=True,exist_ok=True)
run(['mvn','-B','-ntp',f'-Dmaven.repo.local={mcache}','-DskipTests','-pl','reconstruction/band','-am','install'],logs/'maven.log',cwd=cj)
dependencies=b/'dependencies.txt'
if a.java_dependencies:
    dependencies.write_text(a.java_dependencies.resolve().read_text().strip()+'\n')
else:
    run(['mvn','-B','-ntp',f'-Dmaven.repo.local={mcache}','-f',cj/'reconstruction/band/pom.xml',
         'dependency:build-classpath',f'-Dmdep.outputFile={dependencies}'],logs/'classpath.log')
dep_paths=[Path(x) for x in dependencies.read_text().strip().split(os.pathsep)]
if not all(x.is_file() for x in dep_paths):raise RuntimeError('Dependency classpath contains missing files')
cp=os.pathsep.join([str(classes),str(cj/'reconstruction/band/target/classes'),dependencies.read_text().strip()])
(b/'classpath.txt').write_text(cp+'\n')
run(['javac','-cp',cp,'-d',classes,*sorted((ROOT/'harness').glob('*.java'))],logs/'javac.log')
cpp=a.reuse_cpp_build.resolve() if a.reuse_cpp_build else b/'bandsoft'
if not a.reuse_cpp_build:
    run(['cmake','-S',bs,'-B',cpp,'-G','Unix Makefiles','-DCMAKE_BUILD_TYPE=Release'],logs/'cmake-configure.log')
    # Avoid the upstream analysis executables (and their older QADB constructor).
    run(['cmake','--build',cpp,'--parallel',a.jobs,'--target','Banks4','bandhit_tree',
         'clashit_tree','taghit_tree','genpart_tree','particles_tree','e_pid','DC_fiducial'],logs/'cmake-build.log')
cache=(cpp/'CMakeCache.txt').read_text()
if f'CMAKE_HOME_DIRECTORY:INTERNAL={bs}' not in cache:
    raise RuntimeError('C++ build is not configured from the selected reference checkout')
skimmers=cpp/'skimmers'; flags={}
for line in (skimmers/'CMakeFiles/tagged_dis.dir/flags.make').read_text().splitlines():
    if ' = ' in line:
        key,value=line.split(' = ',1); flags[key]=shlex.split(value)
link=shlex.split((skimmers/'CMakeFiles/tagged_dis.dir/link.txt').read_text())
compiler=link[0]; common=flags['CXX_DEFINES']+flags['CXX_INCLUDES']+flags['CXX_FLAGS']
objects={}
for name,src in [('bandreco',bs/'bandreco/bandreco.cpp'),('readhipo_helper',bs/'skimmers/readhipo_helper.cpp')]:
    obj=b/(name+'.o')
    run([compiler,*common,'-c',src,'-o',obj],logs/(name+'.log'));objects[name]=obj
executables=[]
for name in ('baseline_cpp','reconstruction_cpp','campaign_cpp','hit_only_cpp','neutron_selector_cpp','selector_contract'):
    obj=b/(name+'.o'); dest=b/name
    run([compiler,*common,'-c',ROOT/'harness'/(name+'.cpp'),'-o',obj],logs/(name+'-compile.log'))
    args=[]
    for token in link:
        if token.endswith('tagged_dis.cpp.o'): token=str(obj)
        elif token.endswith('readhipo_helper.cpp.o'): token=str(objects['readhipo_helper'])
        elif token.endswith('bandreco.cpp.o'): token=str(objects['bandreco'])
        args.append(token)
    args[args.index('-o')+1]=str(dest)
    run(args,logs/(name+'-link.log'),cwd=skimmers);executables.append(dest)
artifacts={str(x):sha(x) for x in dep_paths+executables}
for extension in ('*.a','*.so','*.so.*'):
    artifacts.update({str(x.resolve()):sha(x) for x in cpp.rglob(extension) if x.is_file()})
for folder in [classes,cj/'reconstruction/band/target/classes']:
    artifacts.update({str(x):sha(x) for x in sorted(folder.rglob('*.class'))})
versions={}
for name,args in [('java',['java','-version']),('compiler',[compiler,'--version']),('ROOT',['root-config','--version'])]:
    versions[name]=subprocess.check_output(args,stderr=subprocess.STDOUT,text=True).strip()
write_json(b/'manifest.json',{'sources':source_hashes(),'configuration':cfg,'artifacts':artifacts,
           'cpp_build':str(cpp),'versions':versions,'classpath':cp})
print('COMPLETE external build; run python3 scripts/validate.py selftest --output results/selftest',flush=True)
