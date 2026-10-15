#!/usr/bin/env python3
"""Run resumable cross-language comparisons without external Python packages."""
import argparse
import itertools
import json
import platform
from pathlib import Path
import shutil
import sys
import time
from common import ROOT, config, frozen, run, runtime, sha, source_hashes, write_json

sys.path.insert(0,str(ROOT/'harness'))
from compare_neutrons import run as compare_neutrons
from compare_hits import run as compare_hits

def built():
    cfg=config()
    manifest=json.loads((ROOT/'build/manifest.json').read_text())
    if manifest['configuration']!=cfg or manifest['sources']!=source_hashes():
        raise RuntimeError('Sources/configuration changed: rebuild before comparing')
    for path,digest in manifest['artifacts'].items():
        if sha(path)!=digest:raise RuntimeError('Build artifact changed: '+path)
    return cfg,manifest

def java(manifest,name,*args,props=()):
    return ['java','-Duser.timezone=UTC',*props,'-cp',manifest['classpath'],name,*args]

def stage(name,command,outputs,out,env):
    """Reuse a successful phase only after checking its output hashes."""
    outputs=list(map(Path,outputs));receipt=out/(name+'.done.json')
    command=list(map(str,command))
    if receipt.exists():
        saved=json.loads(receipt.read_text())
        if saved['command']!=command or saved['outputs']!={str(x):sha(x) for x in outputs}:
            raise RuntimeError('Changed completed phase: '+str(receipt))
        print('REUSE',name,flush=True);return
    # A killed process may have completed one output but not the whole phase.
    # Preserve such unreceipted attempts rather than trusting or deleting them.
    leftovers=[p for x in outputs for p in (x,Path(str(x)+'.partial')) if p.exists()]
    if leftovers:
        archive=out/'incomplete'/f'{name}-{time.time_ns()}';archive.mkdir(parents=True)
        for path in leftovers:shutil.move(str(path),archive/path.name)
    run(command,out/'logs'/(name+'.log'),env=env)
    write_json(receipt,{'command':command,'outputs':{str(x):sha(x) for x in outputs}})

def tables(cfg,runno,period,mc,out,env):
    dest=out/'tables.json';router=out/'runs.json'
    stage('calibration',[sys.executable,ROOT/'scripts/convert_calibrations.py',runno,period,dest,
          '--bandsoft',cfg['bandsoft_tools'],*(['--mc'] if mc else [])],[dest],out,env)
    frozen(router,{str(runno):str(dest)})
    return router

def compare_streams(cfg,manifest,input,router,out,period,mc,start,count,chunk,hit_only=False,standalone=False):
    out.mkdir(parents=True,exist_ok=True);env=runtime(cfg)
    tablemap=json.loads(router.read_text())
    settings={'build_manifest_sha256':sha(ROOT/'build/manifest.json'),'input':str(input),
              'input_sha256':sha(input),'router':tablemap,
              'table_sha256':{k:sha(v) for k,v in tablemap.items()},'period':period,
              'mc':mc,'start':start,'count':count,'chunk':chunk,'hit_only':hit_only,'standalone':standalone}
    frozen(out/'configuration.json',settings)
    total=0;rows=[]
    for offset in range(start,start+count,chunk):
        size=min(chunk,start+count-offset);d=out/f'{offset:09d}';d.mkdir(exist_ok=True)
        c=d/'cpp.jsonl';j=d/'java.jsonl';h=d/'java.hipo'
        cpp='hit_only_cpp' if hit_only else 'campaign_cpp'
        stage('cpp',[ROOT/'build'/cpp,input,c,period,offset,size,int(mc),
              *(['--reviewed-standalone-period'] if standalone else [])],[c],d,env)
        cls='HitOnlyJava' if hit_only else 'CampaignJava'
        stage('java',java(manifest,cls,input,j,offset,size,d/'unused-constants',
              props=[f'-Dband.tables={router}',f'-Dband.output={h}']),[j,h],d,env)
        summary=(compare_hits if hit_only else compare_neutrons)(c,j,d/'summary.json')
        actual=summary['counts']['records']
        if actual!=size:
            raise RuntimeError(f'Requested {size} records at {offset}; got {actual}. Use the actual input record count.')
        reader='HitOnlyReadback' if hit_only else 'NeutronHipoReadback'
        stage('readback',java(manifest,reader,h,j),[],d,env)
        rows.append({'start':offset,**summary['counts']});total+=actual
        print(f'PASS comparison records={total}/{count} chunks={len(rows)} mismatches=0',flush=True)
    result={'records':total,'chunks':rows,'mismatches':0,'mode':'hits' if hit_only else 'neutrons'}
    write_json(out/'summary.json',result)
    return result

def selftest(cfg,manifest,out):
    out.mkdir(parents=True,exist_ok=True);env=runtime(cfg)
    frozen(out/'configuration.json',{'build_manifest_sha256':sha(ROOT/'build/manifest.json'),'suite':'public-v1'})
    sel=out/'selector';sel.mkdir(exist_ok=True)
    stage('cases',[sys.executable,ROOT/'scripts/make_neutron_cases.py','--output',sel],
          [sel/'cases.txt',sel/'cases.json'],sel,env)
    stage('cpp',[ROOT/'build/neutron_selector_cpp',sel/'cases.txt',sel/'cpp.jsonl'],[sel/'cpp.jsonl'],sel,env)
    stage('java',java(manifest,'NeutronSelectorJava',sel/'cases.txt',sel/'java.jsonl'),[sel/'java.jsonl'],sel,env)
    named={x['name']:x for x in json.loads((sel/'cases.json').read_text())['named_cases']};n=0
    with (sel/'cpp.jsonl').open() as cfile,(sel/'java.jsonl').open() as jfile:
        for c,j in itertools.zip_longest(cfile,jfile):
            if c is None or j is None:raise AssertionError('Selector stream lengths differ')
            c,j=json.loads(c),json.loads(j)
            if c!=j:raise AssertionError({'cpp':c,'java':j})
            if c['name'] in named:
                expected=named[c['name']]
                if (c['lead_id'],c['survivors'])!=(expected['expected_lead'],expected['expected_survivors']):
                    raise AssertionError({'expected':expected,'actual':c})
            n+=1
    if n!=10000+len(named):raise AssertionError('Unexpected selector case count')
    write_json(sel/'summary.json',{'cases':n,'named':len(named),'generated':10000,'mismatches':0})
    seed=out/'dictionary.hipo'
    stage('dictionary',java(manifest,'SchemaSeed',seed),[seed],out,env)
    comparisons=[]
    for mc in (False,True):
        d=out/('mc' if mc else 'data');d.mkdir(exist_ok=True)
        router=tables(cfg,6363,0,mc,d,env)
        if not mc:
            stage('reconstruction-contracts',java(manifest,'BandPortContractTests',d/'tables.json'),[],d,env)
            stage('neutron-contracts',java(manifest,'NeutronContractTests',d/'tables.json',d/'roundtrip.hipo'),[d/'roundtrip.hipo'],d,env)
            stage('comparator-tests',[sys.executable,'-m','unittest','discover','-s','harness','-p','test_compare.py'],[],d,env)
        fixture=d/'channels.hipo'
        stage('fixtures',java(manifest,'CampaignFixtures',seed,router,fixture,'channels'),[fixture,Path(str(fixture)+'.cases.json')],d,env)
        cases=json.loads(Path(str(fixture)+'.cases.json').read_text())
        comparisons.append(compare_streams(cfg,manifest,fixture,router,d/'neutrons',0,mc,0,len(cases),1000))
        bare=d/'without-timing.hipo'
        stage('remove-timing',java(manifest,'RemoveRecTiming',fixture,bare),[bare],d,env)
        comparisons.append(compare_streams(cfg,manifest,bare,router,d/'hits',0,mc,0,len(cases),1000,True))
    trans=out/'transitions';trans.mkdir(exist_ok=True);mapping={}
    for runno in (6290,6291,6399,6400,6363):
        d=trans/str(runno);d.mkdir(exist_ok=True)
        router=tables(cfg,runno,0 if runno<6400 else 1,False,d,env)
        mapping.update(json.loads(router.read_text()))
    router=trans/'runs.json';frozen(router,mapping);fixture=trans/'transitions.hipo'
    stage('fixtures',java(manifest,'CampaignFixtures',seed,router,fixture,'transitions'),[fixture,Path(str(fixture)+'.cases.json')],trans,env)
    cpp_parts=[]
    for i,runno in enumerate((6290,6291,6399,6400,6363,6290)):
        dest=trans/f'cpp-{i}.jsonl';cpp_parts.append(dest)
        stage(f'cpp-{i}',[ROOT/'build/campaign_cpp',fixture,dest,0 if runno<6400 else 1,i,1,0],[dest],trans,env)
    c=trans/'cpp.jsonl';text=''.join(x.read_text() for x in cpp_parts)
    if c.exists() and c.read_text()!=text:raise RuntimeError('Changed transition reference')
    c.write_text(text)
    j=trans/'java.jsonl';h=trans/'java.hipo'
    # One Java engine processes all six events, exercising live run transitions.
    stage('java',java(manifest,'CampaignJava',fixture,j,0,6,trans/'unused',
          props=[f'-Dband.tables={router}',f'-Dband.output={h}']),[j,h],trans,env)
    compare_neutrons(c,j,trans/'comparison.json')
    stage('readback',java(manifest,'NeutronHipoReadback',h,j),[],trans,env)
    write_json(out/'summary.json',{'selector_cases':n,'reconstruction_contracts':21,'neutron_contracts':17,
               'raw_neutron_fixture_records':300,'timing_free_fixture_records':294,
               'comparisons':comparisons,'mismatches':0})
    print('PASS selftest: 10049 selector cases, 38 contracts, 300 raw fixtures, 294 timing-free fixtures',flush=True)

def main():
    p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='action',required=True)
    t=sub.add_parser('selftest',help='Synthetic fixtures; no private HIPO needed');t.add_argument('--output',required=True,type=Path)
    strip=sub.add_parser('strip-timing',help='Make a separate timing-free input; preserve the original')
    strip.add_argument('--input',required=True,type=Path);strip.add_argument('--output',required=True,type=Path,
        help='New output directory; contains input-without-timing.hipo')
    c=sub.add_parser('compare',help='Single-run user-supplied raw-bank HIPO')
    c.add_argument('--input',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c.add_argument('--run',required=True,type=int);c.add_argument('--period',required=True,type=int,choices=range(4))
    c.add_argument('--mc',action='store_true');c.add_argument('--mode',choices=['neutrons','hits'],default='neutrons')
    c.add_argument('--start',type=int,default=0);c.add_argument('--count',required=True,type=int)
    c.add_argument('--chunk-size',type=int,default=1000)
    c.add_argument('--reviewed-standalone-period',action='store_true',help='Hit-only: caller has reviewed standalone laser calibration choice')
    a=p.parse_args()
    if platform.system()!='Linux':p.error('Run reconstruction validation on JLab/Linux, not the Mac')
    cfg,manifest=built();out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
    if a.action=='selftest':return selftest(cfg,manifest,out)
    if a.action=='strip-timing':
        frozen(out/'configuration.json',{'input':str(a.input.resolve()),'sha256':sha(a.input),
               'build_manifest_sha256':sha(ROOT/'build/manifest.json')})
        return stage('strip-timing',java(manifest,'RemoveRecTiming',a.input.resolve(),out/'input-without-timing.hipo'),
                     [out/'input-without-timing.hipo'],out,runtime(cfg))
    if a.count<1 or a.chunk_size<1 or a.start<0:p.error('Positive count/chunk size and nonnegative start required')
    if a.reviewed_standalone_period and a.mode!='hits':p.error('Standalone override is hit-only')
    # Freeze selection before generating constants so a reused directory cannot mix settings.
    frozen(out/'request.json',{'input':str(a.input.resolve()),'input_sha256':sha(a.input),
           'run':a.run,'period':a.period,'mc':a.mc,'mode':a.mode,'start':a.start,
           'count':a.count,'chunk':a.chunk_size,'standalone':a.reviewed_standalone_period,
           'build_manifest_sha256':sha(ROOT/'build/manifest.json')})
    router=tables(cfg,a.run,a.period,a.mc,out,runtime(cfg))
    compare_streams(cfg,manifest,a.input.resolve(),router,out/'comparison',a.period,a.mc,
                    a.start,a.count,a.chunk_size,a.mode=='hits',a.reviewed_standalone_period)

if __name__=='__main__':main()
