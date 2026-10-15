#!/usr/bin/env python3
"""Exact detector-stage comparison; deliberately does not evaluate neutron selection."""
import collections,hashlib,itertools,json,pathlib,sys
from compare import f32,keyed

def compare(c,j):
    diffs=[]
    for e in (c,j):
        if e['start_time'] is not None or e['candidate'] is not None or e['hits'] or e.get('selection_evaluated') is not False:
            raise ValueError('Hit-only stream contains a timing/selection result')
    if any(c.get(k)!=j.get(k) for k in ('ordinal','run','event','adc_rows','tdc_rows','input_status')):diffs.append('input')
    for stage,links,values in [('pmts',('adc_row','tdc_row'),('adc','amplitude','time_phase','time_fadc')),('bars',('layer','left_id','right_id'),('time','time_fadc','diff','diff_fadc','x','y','z','energy'))]:
        a,b=keyed(c[stage]),keyed(j[stage])
        if a.keys()!=b.keys():diffs.append(stage+'_selection')
        for k in a.keys()&b.keys():
            if any(a[k][f]!=b[k][f] for f in links):diffs.append(stage+'_links')
            if any(f32(a[k][f])!=b[k][f] for f in values):diffs.append(stage+'_values')
    bars=j['bars'];laser=len(bars)>100;veto=any(b['layer']==6 for b in bars)
    status=1 if laser else 3 if veto else 0 if len(bars)<5 else 2
    if any(b['bank']!=('BAND::laser' if laser else 'BAND::hits') or b['status']!=status for b in bars):diffs.append('classification')
    return sorted(set(diffs))

def run(cpath,jpath,output):
    inputs={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (cpath,jpath)}
    if output.exists():
        saved=json.loads(output.read_text());assert saved['inputs']==inputs and not saved['differences'];return saved
    counts=collections.Counter();diffs=collections.Counter();hist=collections.Counter();channels=set();examples=[]
    with cpath.open() as cf,jpath.open() as jf:
        for c,j in itertools.zip_longest(cf,jf):
            assert c is not None and j is not None
            c,j=json.loads(c),json.loads(j);d=compare(c,j);diffs.update(d)
            counts['records']+=1;counts['pmts']+=len(c['pmts']);counts['bars']+=len(c['bars']);counts['laser_events']+=len(c['bars'])>100
            hist[len(c['bars'])]+=1;channels.update(b['id'] for b in c['bars'])
            if d and len(examples)<10:examples.append({'ordinal':c['ordinal'],'differences':d})
    assert counts['records']
    result={'counts':dict(counts),'bar_multiplicity':dict(sorted(hist.items())),'bar_ids':sorted(channels),'differences':dict(diffs),'examples':examples,'inputs':inputs,'selection':'not evaluated; no REC::Event or synthetic start time','comparison':'Exact discrete identities and links, including veto; PMT/bar values at float32 precision; Java status and output bank checked'}
    output.write_text(json.dumps(result,indent=2)+'\n');assert not diffs,result
    print('PASS hit-only',dict(counts),flush=True);return result
if __name__=='__main__':run(*(pathlib.Path(p) for p in sys.argv[1:]))
