import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Small raw-bank fixtures replayed through both actual reconstructions. */
public class MakeBandFixtures {
 static final String[] NAMES={"reference_pair","empty","missing_tdc","single_side","duplicate_adc","duplicate_tdc","rejected_adc_duplicate","amplitude249","amplitude250","saturation4094","saturation4095","pedestal_floor","zero_adc","zero_fadc","zero_tdc","veto_left","veto_right","both_veto_sides","tdc_far_outside_bar","mc_adc4999","mc_adc5000","mc_truncated_tdc","negative_timestamp"};
 static class Signal {int s=5,l=2,c=2,o,adc,amp,ped,tdc;float time;
  Signal copy(){Signal x=new Signal();x.s=s;x.l=l;x.c=c;x.o=o;x.adc=adc;x.amp=amp;x.ped=ped;x.tdc=tdc;x.time=time;return x;}}
 public static void main(String[] a)throws Exception {
  HipoDataSource src=new HipoDataSource();src.open(a[0]);DataEvent original=null;for(int i=0;i<=146;i++)original=src.getNextEvent();
  DataBank ad=original.getBank("BAND::adc"),td=original.getBank("BAND::tdc");Signal[] ref={new Signal(),new Signal()};
  for(int i=0;i<ad.rows();i++)if(ad.getByte("sector",i)==5&&ad.getByte("layer",i)==2&&ad.getShort("component",i)==2){
   int side=ad.getByte("order",i);Signal x=ref[side];x.o=side;x.adc=ad.getInt("ADC",i);x.amp=ad.getInt("amplitude",i);x.ped=ad.getShort("ped",i);x.time=ad.getFloat("time",i);
  }
  for(int i=0;i<td.rows();i++)if(td.getByte("sector",i)==5&&td.getByte("layer",i)==2&&td.getShort("component",i)==2)ref[td.getByte("order",i)-2].tdc=td.getInt("TDC",i);
  long stamp=original.getBank("RUN::config").getLong("timestamp",0);
  for(boolean mc:new boolean[]{false,true}){
   Path dest=Path.of(a[1]+(mc?"-mc":"-data")+".hipo");if(Files.exists(dest))throw new IllegalArgumentException("Fixture exists "+dest);
   HipoDataSync w=new HipoDataSync(src.getReader().getSchemaFactory());w.open(dest.toString());JSONArray cases=new JSONArray();
   for(int k=0;k<NAMES.length;k++){
    Signal l=ref[0].copy(),r=ref[1].copy();ArrayList<Signal> aa=new ArrayList<>(),tt=new ArrayList<>();
    if(mc){l.adc=r.adc=20000;l.amp=r.amp=500;l.ped=r.ped=0;l.tdc*=10000;r.tdc*=10000;}
    aa.add(l);aa.add(r);tt.add(l);tt.add(r);
    switch(k){
     case 1:aa.clear();tt.clear();break; case 2:tt.clear();break;case 3:aa.remove(1);break;
     case 4:aa.add(l.copy());break;case 5:tt.add(l.copy());break;
     case 6:Signal bad=l.copy();bad.amp=bad.ped+249;aa.add(bad);break;
     case 7:l.amp=l.ped+249;break;case 8:l.amp=l.ped+250;break;
     case 9:l.amp=4094;break;case 10:l.amp=4095;break;
     case 11:l.amp=500;l.ped=251;break;case 12:l.adc=0;break;
     case 13:l.time=0;break;case 14:l.tdc=0;break;
     case 15:case 16:case 17:
      l.s=r.s=2;l.l=r.l=6;l.c=r.c=7;
      if(k!=17){aa.clear();tt.clear();Signal v=k==15?l:r;aa.add(v);tt.add(v);}break;
     case 18:l.tdc+=mc?100000000:10000;break;
     case 19:l.adc=4999;break;case 20:l.adc=5000;break;
     case 21:if(mc)l.tdc+=9999;break;
    }
    DataEvent ev=w.createEvent();DataBank run=ev.createBank("RUN::config",1),rec=ev.createBank("REC::Event",1);
    run.setInt("run",0,6363);run.setInt("event",0,1000000+k);run.setLong("timestamp",0,k==22?-1:stamp);rec.setFloat("startTime",0,90);
    ev.appendBanks(run,rec);if(mc)ev.appendBank(ev.createBank("MC::Event",1));
    if(!aa.isEmpty()){DataBank b=ev.createBank("BAND::adc",aa.size());for(int i=0;i<aa.size();i++){Signal x=aa.get(i);b.setByte("sector",i,(byte)x.s);b.setByte("layer",i,(byte)x.l);b.setShort("component",i,(short)x.c);b.setByte("order",i,(byte)x.o);b.setInt("ADC",i,x.adc);b.setInt("amplitude",i,x.amp);b.setShort("ped",i,(short)x.ped);b.setFloat("time",i,x.time);}ev.appendBank(b);}
    if(!tt.isEmpty()){DataBank b=ev.createBank("BAND::tdc",tt.size());for(int i=0;i<tt.size();i++){Signal x=tt.get(i);b.setByte("sector",i,(byte)x.s);b.setByte("layer",i,(byte)x.l);b.setShort("component",i,(short)x.c);b.setByte("order",i,(byte)(x.o+2));b.setInt("TDC",i,x.tdc);}ev.appendBank(b);}
    w.writeEvent(ev);cases.put(new JSONObject().put("ordinal",k).put("event",1000000+k).put("name",NAMES[k]));
   }
   w.close();Files.writeString(Path.of(dest+".cases.json"),cases.toString(2)+"\n");System.out.println("COMPLETE fixtures "+dest+" cases="+NAMES.length);
  }src.close();
 }
}
