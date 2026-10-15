#!/usr/bin/env python3
"""Step-4 comparison: retain step-3 checks and require eligible and neutron parity."""
import collections, hashlib, itertools, json, pathlib, sys
from compare import compare, f32, keyed

def run(cpath, jpath, output):
    if output.exists():
        saved=json.loads(output.read_text())
        for p in (cpath,jpath):
            assert saved['inputs'][str(p)]==hashlib.sha256(p.read_bytes()).hexdigest()
        assert not saved['differences']
        return saved
    counts=collections.Counter(); differences=collections.Counter(); examples=[]
    for c,j in itertools.zip_longest(cpath.open(),jpath.open()):
        assert c is not None and j is not None, 'Unequal event counts'
        c,j=json.loads(c),json.loads(j);diffs,_=compare(c,j)
        if c.get('input_status')!='metadata' and j['candidate'] is None:diffs.append('missing_decision')
        ch,jh=keyed(c['hits']),keyed(j['hits'])
        if list(ch)!=list(jh):diffs.append('eligibility_or_order')
        for k in ch.keys() & jh.keys():
            if f32(ch[k]['tof'])!=f32(jh[k]['tof']):diffs.append('eligible_tof')
        cb,jb=keyed(c['bars']),keyed(j['bars'])
        for k in cb.keys() & jb.keys():
            if cb[k]['eligible']!=jb[k]['eligible']:diffs.append('bar_eligibility')
            if any(cb[k][f]!=jb[k][f] for f in ('left_id','right_id')):diffs.append('bar_links')
        differences.update(set(diffs));counts['records']+=1
        counts['eligible_hits']+=len(ch)
        counts['accepted_cpp']+=bool(c['candidate'] and c['candidate']['accepted'])
        counts['accepted_java']+=bool(j['candidate'] and j['candidate']['accepted'])
        if diffs and len(examples)<20:examples.append({'ordinal':c['ordinal'],'differences':diffs,'cpp':c['candidate'],'java':j['candidate']})
        if counts['records']%1000==0:print('PROGRESS neutron comparison',dict(counts),flush=True)
    assert counts['records']>0
    result={'counts':dict(counts),'differences':dict(differences),'examples':examples,
            'comparison':'Exact discrete decisions, eligibility, source links and ordering; continuous values at common float32 precision, no tolerance',
            'inputs':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in (cpath,jpath)}}
    output.write_text(json.dumps(result,indent=2)+'\n')
    assert not differences, result
    print('PASS neutron comparison',dict(counts),flush=True)
    return result

if __name__=='__main__':run(*(pathlib.Path(p) for p in sys.argv[1:]))
