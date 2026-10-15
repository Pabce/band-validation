#!/usr/bin/env python3
"""Compare aligned baseline JSONL streams and classify the first differing stage.
Numerical equality is evaluated after C++ -> float32, matching HIPO bank storage.
This is an exact diagnostic, not an acceptance tolerance for the future port.
"""
import argparse, collections, hashlib, itertools, json, math, pathlib, struct

def f32(x):
    if x is None:return None
    return struct.unpack('f',struct.pack('f',x))[0]
def keyed(rows):
    result={x['id']:x for x in rows}
    if len(rows)!=len(result):raise ValueError('duplicate detector identity in event')
    return result

def compare(c,j):
    identity=['ordinal','run','event']
    if any(c[k]!=j[k] for k in identity):raise ValueError('input stream identity mismatch')
    diffs=[];residuals=[]
    if c.get('input_status')!=j.get('input_status'):diffs.append('input')
    if any(c[k]!=j[k] for k in ('adc_rows','tdc_rows','start_time')):diffs.append('input')
    cp,jp=keyed(c['pmts']),keyed(j['pmts'])
    if cp.keys()!=jp.keys():diffs.append('pmt_selection')
    for pid in cp.keys()&jp.keys():
        a,b=cp[pid],jp[pid]
        if any(a[k]!=b[k] for k in ('adc_row','tdc_row')):diffs.append('pmt_selection')
        for k in ('adc','amplitude','time_phase','time_fadc'):
            if f32(a[k])!=b[k]:diffs.append('pmt_values');residuals.append((f'pmt.{k}',pid,a[k],b[k]))
    cb,jb=keyed(c['bars']),keyed(j['bars'])
    if cb.keys()!=jb.keys():diffs.append('bar_selection')
    for bid in cb.keys()&jb.keys():
        a,b=cb[bid],jb[bid]
        if a['layer']!=6 and any(a[k]!=b[k] for k in ('left_id','right_id')):diffs.append('bar_links')
        for k in ('time','time_fadc','diff','diff_fadc','x','y','z','energy'):
            if f32(a[k])!=b[k]:diffs.append('bar_values');residuals.append((f'bar.{k}',bid,a[k],b[k]))
    if j['candidate'] is None:
        if c['bars'] or j['bars']:diffs.append('neutron_selection_unavailable')
    elif c['candidate']!=j['candidate']:diffs.append('neutron_selection')
    order=['input','pmt_selection','pmt_values','bar_selection','bar_links','bar_values','neutron_selection_unavailable','neutron_selection']
    return [x for x in order if x in diffs],residuals

def run(cpath,jpath,output):
    stages=collections.Counter();allstages=collections.Counter();stats={};examples=[];n=0
    counts=collections.Counter();audit=output.with_suffix('.events.jsonl')
    if output.exists() or audit.exists():raise ValueError('refusing to overwrite report')
    with cpath.open() as cf,jpath.open() as jf,audit.open('w') as eventout:
        for cl,jl in itertools.zip_longest(cf,jf):
            if cl is None or jl is None:raise ValueError('different event counts')
            c,j=json.loads(cl),json.loads(jl);diffs,res=compare(c,j);n+=1
            first=diffs[0] if diffs else 'equal';stages[first]+=1;allstages.update(diffs)
            counts['cpp_pmts']+=len(c['pmts']);counts['java_pmts']+=len(j['pmts']);counts['cpp_bars']+=len(c['bars']);counts['java_bars']+=len(j['bars']);counts['cpp_eligible_hits']+=len(c['hits']);counts['cpp_good_neutrons']+=bool(c['candidate'] and c['candidate']['accepted']);counts['metadata_events']+=c.get('input_status')=='metadata';counts['events_with_raw_adc']+=c['adc_rows']>0
            for key,bid,x,y in res:
                d=stats.setdefault(key,{'count':0,'nonfinite':0,'max_abs':0.0,'sum_abs':0.0,'max_relative':0.0});d['count']+=1
                if x is None or y is None:d['nonfinite']+=1;continue
                delta=abs(x-y);d['sum_abs']+=delta;d['max_abs']=max(d['max_abs'],delta);d['max_relative']=max(d['max_relative'],delta/max(abs(x),abs(y),1e-30))
            record={k:c[k] for k in ('ordinal','run','event')};record.update(first_difference=first,differing_stages=diffs,cpp_candidate=c['candidate'],missing_java_pmts=sorted(keyed(c['pmts']).keys()-keyed(j['pmts']).keys()),extra_java_pmts=sorted(keyed(j['pmts']).keys()-keyed(c['pmts']).keys()),missing_java_bars=sorted(keyed(c['bars']).keys()-keyed(j['bars']).keys()),extra_java_bars=sorted(keyed(j['bars']).keys()-keyed(c['bars']).keys()))
            eventout.write(json.dumps(record)+'\n')
            if diffs and len(examples)<20:examples.append({**record,'first_residuals':res[:8]})
            if n%1000==0:print(f'PROGRESS compare events={n} first_stages={dict(stages)}',flush=True)
    if n==0:raise ValueError('empty comparison')
    result={'events':n,'first_difference_counts':dict(stages),'all_difference_counts':dict(allstages),'counts':dict(counts),'residuals':stats,'examples':examples,'comparison':'Exact after float32 casting of C++ values; no numerical tolerance; Java neutron selector unavailable','inputs':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (cpath,jpath)},'per_event_report':str(audit)}
    output.write_text(json.dumps(result,indent=2)+'\n');print(f'COMPLETE compare events={n} report={output}',flush=True)
    return result
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('cpp',type=pathlib.Path);p.add_argument('java',type=pathlib.Path);p.add_argument('output',type=pathlib.Path);a=p.parse_args();run(a.cpp,a.java,a.output)
