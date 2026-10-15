import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
import org.jlab.rec.band.constants.BandCalibration;
import org.jlab.rec.band.banks.HitReader;
/** Engineered raw inputs: all physical channels, classification boundaries, and run reloads. */
public class CampaignFixtures {
 static DataEvent event(HipoDataSync out, BandCalibration cal, List<Integer> ids, int runno, int number) {
  DataEvent ev=out.createEvent();DataBank run=ev.createBank("RUN::config",1),rec=ev.createBank("REC::Event",1);
  run.setInt("run",0,runno);run.setInt("event",0,number);run.setLong("timestamp",0,-1);rec.setFloat("startTime",0,90);ev.appendBanks(run,rec);
  if(cal.simulation)ev.appendBank(ev.createBank("MC::Event",1));
  int rows=0;for(int id:ids)rows+=id/10%10==6?1:2;
  DataBank adc=ev.createBank("BAND::adc",rows),tdc=ev.createBank("BAND::tdc",rows);int i=0;
  for(int id:ids){var b=cal.bar(id);int s=id/100,l=id/10%10,c=id%10;
   for(int side=0;side<(l==6?1:2);side++,i++){
    // Invert the offsets only to place synthetic signals near the bar center.
    // The actual independent C++ and Java engines still reconstruct every value.
    double t=220+(side==0?b.tdcOffset:0)+(cal.simulation?0:b.timeWalk(side+1,1000));
    int raw=(int)Math.round(t/0.02345);if(cal.simulation)raw*=10000;
    adc.setByte("sector",i,(byte)s);adc.setByte("layer",i,(byte)l);adc.setShort("component",i,(short)c);adc.setByte("order",i,(byte)side);
    adc.setInt("ADC",i,(int)Math.round(5*b.adcPerMeV));adc.setInt("amplitude",i,1000);adc.setShort("ped",i,(short)0);adc.setFloat("time",i,(float)(220+(side==0?b.fadcOffset:0)));
    tdc.setByte("sector",i,(byte)s);tdc.setByte("layer",i,(byte)l);tdc.setShort("component",i,(short)c);tdc.setByte("order",i,(byte)(side+2));tdc.setInt("TDC",i,raw);
   }
  }
  ev.appendBanks(adc,tdc);return ev;
 }
 static BandCalibration calibration(String path,int run){SnapshotConstants snap=new SnapshotConstants(path);return new BandCalibration(t->snap.getConstants(run,t));}
 public static void main(String[] args)throws Exception {
  // dictionary source, calibration run-map, output, mode (channels or transitions)
  Path dest=Path.of(args[2]);if(Files.exists(dest))throw new IllegalArgumentException("Output exists");
  HipoDataSource src=new HipoDataSource();src.open(args[0]);HipoDataSync out=new HipoDataSync(src.getReader().getSchemaFactory());out.open(dest+".partial");
  JSONObject map=new JSONObject(Files.readString(Path.of(args[1])));JSONArray cases=new JSONArray();int n=0;
  if(args[3].equals("transitions")){
   for(int run:new int[]{6290,6291,6399,6400,6363,6290}){
    BandCalibration cal=calibration(map.getString(""+run),run);List<Integer> ids=List.of(522,267);
    out.writeEvent(event(out,cal,ids,run,2000000+n));cases.put(new JSONObject().put("ordinal",n++).put("run",run).put("name","calibration_transition").put("input_ids",ids));
   }
  } else {
   int run=6363;BandCalibration cal=calibration(map.getString(""+run),run);List<Integer> active=new ArrayList<>();
   for(int s=1;s<=5;s++)for(int l=1;l<=6;l++)for(int c=1;HitReader.physicalChannel(s,l,c);c++){
    int id=s*100+l*10+c;if(cal.bar(id).status!=0)active.add(id);
    out.writeEvent(event(out,cal,List.of(id),run,2100000+n));cases.put(new JSONObject().put("ordinal",n++).put("name","single_channel_"+id).put("input_ids",List.of(id)).put("expected_bars",cal.bar(id).status==0?0:1));
   }
   for(int size:new int[]{4,5,6,99,100,101,active.size()}){
    if(size>active.size())continue;List<Integer> ids=new ArrayList<>(active.subList(0,size));out.writeEvent(event(out,cal,ids,run,2100000+n));
    cases.put(new JSONObject().put("ordinal",n++).put("name","multiplicity_"+size).put("input_ids",ids).put("expected_bars",size));
   }
  }
  out.close();src.close();Files.move(Path.of(dest+".partial"),dest);Files.writeString(Path.of(dest+".cases.json"),cases.toString(2)+"\n");System.out.println("COMPLETE campaign fixtures "+dest+" cases="+n);
 }
}
