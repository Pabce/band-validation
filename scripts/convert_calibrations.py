#!/usr/bin/env python3
"""Convert the frozen RGB C++ calibration selection to proposed CCDB v2 tables.
No database writes. Layer offsets are expanded exactly as readLayerOffset does.
"""
import hashlib,json,pathlib,sys
import argparse,shutil
p=argparse.ArgumentParser();p.add_argument('run',type=int);p.add_argument('period',type=int);p.add_argument('output',type=pathlib.Path);p.add_argument('--mc',action='store_true');p.add_argument('--bandsoft',type=pathlib.Path,required=True);a=p.parse_args()
r=a.bandsoft.resolve();run=a.run;period=a.period;mc=a.mc;output=a.output
if output.exists():raise FileExistsError('Preserve existing calibration snapshot: '+str(output))
if period<0 or period>12:raise ValueError('unsupported period')
if mc and period>3:raise ValueError('Reference MC loaders do not support this period')
folder='spring2019' if period<2 else 'fall2019' if period<4 else 'rgm' if period<8 else 'rgd' if period==8 else 'rge' if period==9 else 'rgl'
base=r/'include/calibrations'/folder
output.parent.mkdir(parents=True,exist_ok=True)
early=not mc and run<=6290
def offset(name):return name+('_006290_10pt6.txt' if early else '.txt')
def global_file(kind):
 suffix=''
 if not mc:
  if run<=6290:suffix='_006290_10pt6'
  elif run<=6399:suffix='_006399_10pt6'
  elif folder=='fall2019' and 11286<=run<=11300:suffix='_ler'
  elif folder=='rgm':
   for lo,hi,label in [(15539,15727,'D2.1'),(15731,15784,'4gev'),(15014,15491,'6p1'),(15787,15884,'6p2')]:
    if lo<run<=hi:suffix='_rgm_'+label
 return 'global_offsets_'+kind+suffix+'.txt'
inputs={}
def read(name,sim=False):
 p=(r/'include/calibrations/simulation' if sim else base)/name
 inputs[str(p.relative_to(r))]=hashlib.sha256(p.read_bytes()).hexdigest();rows={}
 frozen=output.parent/'sources'/p.parent.name;frozen.mkdir(parents=True,exist_ok=True);shutil.copy2(p,frozen/p.name)
 for line in p.read_text().splitlines():
  if not line.strip() or line.lstrip().startswith('#'):continue
  a=line.split();key=tuple(map(int,a[:3]))
  if key in rows:raise ValueError('duplicate calibration channel '+str(key))
  rows[key]=list(map(float,a[3:]))
 return rows
left,right=read('time_walk_amp_L.txt'),read('time_walk_amp_R.txt')
lr=read(offset('lr_offsets') if not mc else 'lr_offsets.txt',mc);paddle=read(offset('paddle_offsets') if not mc else 'paddle_offsets.txt',mc);layers=read(offset('layer_offsets'))
geometry=read('band_geometry.txt');energy=read('edep_calibration.txt');status=read('status_list.txt')
gt=read(global_file('tdc'));gf=read(global_file('fadc'))
ref=None
for (s,l,c),a in layers.items():
 if a[1]!=0 and a[4]!=0:ref=(s,c)
if ref is None:raise ValueError('no layer reference')
cols=['status','length','x','y','z','tdc_offset','fadc_offset','tdc_velocity','fadc_velocity','tdc_paddle','fadc_paddle','tdc_layer','fadc_layer','tdc_global','fadc_global','adc_per_mev']
rows=[]
for k in sorted(geometry):
 s,l,c=k;a=lr[k];p=paddle[k];layer=layers.get((ref[0],l,ref[1]),[0]*6)
 timing=[a[2],a[3],a[0],a[1],p[2],p[0],0,0,0,0] if mc else [a[0],a[3],a[1],a[4],p[1],p[4],layer[1],layer[4],gt[k][2],gf[k][2]]
 rows.append([*k,status[k][0],[163.7,201.9,51.2,51.2,201.9][s-1],*geometry[k],*timing,1e4 if mc else energy[k][0]])
def table(name,columns,rows):return {'name':'/calibration/band/reconstruction_v2/'+name,'columns':['sector','layer','component']+columns,'rows':rows}
tables=[table('config',['simulation','mother_z','attenuation','hit_cut','laser_cut'],[[0,0,0,int(mc),-3,1e10,5,100]]),table('bars',cols,rows)]
for name,data in [('time_walk_left',left),('time_walk_right',right)]:
 assert all(len(a)<=24 for a in data.values())
 for v in data.values():v.extend([0.]*(24-len(v)))
 tables.append(table(name,['p'+str(i) for i in range(24)],[[*k,*data[k]] for k in sorted(data)]))
output.write_text(json.dumps({'run_min':run,'run_max':run,'period':period,'simulation':mc,'source_sha256':inputs,'layer_reference':ref,'tables':tables},indent=2)+'\n');print('COMPLETE tables '+str(output),flush=True)
