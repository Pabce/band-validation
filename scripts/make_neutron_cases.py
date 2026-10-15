#!/usr/bin/env python3
"""Deterministic calibrated-hit fixtures; tests execute on JLab only."""
import argparse, json, math, pathlib, random
parser=argparse.ArgumentParser();parser.add_argument('--output',type=pathlib.Path,required=True);args=parser.parse_args()
out=args.output;out.mkdir(parents=True,exist_ok=True)
cases=[]
def hit(id=231,energy=2.,time=10.,x=0.,y=0.):return [id,energy,time,x,y]
def add(name,hits,lead=None,survivors=None,start=0.):
    cases.append(dict(name=name,hits=hits,start=start,expected_lead=lead,expected_survivors=survivors))
add('empty',[],-1,0);add('single',[hit()],231,1)
add('veto_only',[hit(261)],-1,0)
add('blocking_chain',[hit(211),hit(221),hit(231)],231,1)
add('veto_chain',[hit(241),hit(251),hit(261)],-1,0)
add('ordinary_inclusive',[hit(231),hit(241,time=13,x=15,y=8)],241,1)
add('veto_ignores_x',[hit(251),hit(261,time=25,x=10000,y=8)],-1,0)
for id,v in [(541,561),(346,366),(446,466)]:add('missing_layer_'+str(id),[hit(id),hit(v)],-1,0)
add('no_missing_layer_exception',[hit(341),hit(361)],341,1)
add('no_missing_layer_in_sector2',[hit(241),hit(261)],241,1)
add('no_sector_equality_requirement',[hit(231),hit(341)],341,1)
add('different_layer_survivors',[hit(211),hit(231)],-1,2)
add('three_survivors',[hit(211),hit(231),hit(251)],-1,3)
add('cluster_first',[hit(231),hit(232,time=12,x=15,y=8)],231,2)
add('cluster_second',[hit(231,time=12),hit(232,x=15,y=8)],232,2)
add('cluster_tie',[hit(231),hit(232,x=15,y=8)],232,2)
add('cluster_tie_reversed_input',[hit(232,x=15,y=8),hit(231)],232,2)
add('cluster_inclusive',[hit(231),hit(232,time=13,x=15,y=8)],231,2)
for axis,bound in [('x',15.),('y',8.),('time',13.)]:
    for sign in [-1,1]:
        target=math.nextafter(bound,math.inf) if sign==1 else math.nextafter(bound,-math.inf)
        add('ordinary_'+axis+('_outside' if sign==1 else '_inside'),[hit(),hit(241,**{axis:target})],-1 if sign==1 else 241,2 if sign==1 else 1)
        add('cluster_'+axis+('_outside' if sign==1 else '_inside'),[hit(),hit(232,**{axis:target})],-1 if sign==1 else (231 if axis=='time' else 232),2)
for axis,bound in [('y',8.),('time',25.)]:
    for sign in [-1,1]:
        target=math.nextafter(bound,math.inf if sign==1 else -math.inf)
        add('veto_'+axis+('_outside' if sign==1 else '_inside'),[hit(251),hit(261,**{axis:target})],251 if sign==1 else -1,1 if sign==1 else 0)
for label,energy,lead,count in [('below',math.nextafter(2.,0.),-1,0),('at',2.,231,1),('above',math.nextafter(2.,math.inf),231,1)]:add('main_threshold_'+label,[hit(energy=energy)],lead,count)
veto=(2./7.2)*2
for label,energy,lead,count in [('below',math.nextafter(veto,0.),251,1),('at',veto,-1,0),('above',math.nextafter(veto,math.inf),-1,0)]:add('veto_threshold_'+label,[hit(251),hit(261,energy=energy)],lead,count)
add('subthreshold_main_cannot_block',[hit(),hit(241,energy=math.nextafter(2.,0.))],231,1)
add('negative_times',[hit(time=-20),hit(232,time=-19)],231,2,start=90)
add('nonzero_start',[hit(time=1010),hit(241,time=1013)],241,1,start=1000)
add('multiple_blockers_first_detector_id',[hit(),hit(242),hit(241)],242,2)
add('energy_not_summed',[hit(energy=1.1),hit(232,energy=1.1)],-1,0)
# Boundaries in the negative direction also use inclusive absolute differences.
add('negative_boundaries',[hit(),hit(241,time=7,x=-15,y=-8)],241,1)
add('negative_veto_boundary',[hit(251),hit(261,time=-5,x=-999,y=-8)],-1,0)
limits=[[3,7,6,6,2]]*4+[[3,7,5,5,0],[3,7,6,6,2]]
ids=[100*s+10*l+c for l in range(1,7) for s in range(1,6) for c in range(1,limits[l-1][s-1]+1)]
rng=random.Random(636304)
for i in range(10000):
    hs=[]
    for id in rng.sample(ids,rng.randrange(0,16)):
        energy=rng.choice([0.,math.nextafter(2.,0.),2.,3.,math.nextafter(veto,0.),veto,1.])
        hs.append(hit(id,energy,rng.choice([-5.,7.,10.,13.,25.,25.00001]),rng.choice([-15.,0.,15.,15.00001]),rng.choice([-8.,0.,8.,8.00001])))
    add('random_'+str(i),hs,start=rng.choice([0.,90.,-20.]))
text=''.join(f"{c['name']} {c['start']:.17g} {len(c['hits'])}\n"+''.join(' '.join(format(v,'.17g') for v in h)+'\n' for h in c['hits']) for c in cases)
for path,content in [(out/'cases.txt',text),(out/'cases.json',json.dumps({'named_cases':[c for c in cases if not c['name'].startswith('random_')],'random_cases':10000,'seed':636304},indent=2)+'\n')]:
    if path.exists():assert path.read_text()==content,'Changed fixtures: choose a new run directory'
    else:path.write_text(content)
print(f'COMPLETE {len(cases)} calibrated selector cases',flush=True)
