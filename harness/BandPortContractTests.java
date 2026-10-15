import java.nio.file.*;
import java.util.*;
import org.jlab.rec.band.constants.*;
import org.jlab.rec.band.hit.*;
import org.jlab.rec.band.banks.*;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.service.band.BANDEngine;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
public class BandPortContractTests {
 static int passed=0; static String path;
 static void check(String name,boolean value){if(!value)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static void rejects(String name,Runnable r){try{r.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){passed++;System.out.println("PASS "+name);}}
 static IndexedTable table(SnapshotConstants s,String name){return s.getConstants(6363,BandCalibration.PREFIX+name);}
 static SnapshotConstants controlled(){SnapshotConstants s=new SnapshotConstants(path);IndexedTable b=table(s,"bars");
  for(String name:new String[]{"tdc_paddle","fadc_paddle","tdc_layer","fadc_layer","tdc_global","fadc_global","x","y","z"})b.setDoubleValue(0.,name,5,2,2);
  b.setDoubleValue(-4.,"tdc_offset",5,2,2);b.setDoubleValue(-2.,"fadc_offset",5,2,2);b.setDoubleValue(10.,"tdc_velocity",5,2,2);b.setDoubleValue(10.,"fadc_velocity",5,2,2);b.setDoubleValue(200.,"length",5,2,2);b.setDoubleValue(100.,"adc_per_mev",5,2,2);
  for(String name:new String[]{"time_walk_left","time_walk_right"}){IndexedTable t=table(s,name);for(int i=0;i<24;i++)t.setDoubleValue(i==9||i==10?1.:0.,"p"+i,5,2,2);}
  return s;
 }
 static BandCalibration calibration(SnapshotConstants s){return new BandCalibration(name->s.getConstants(6363,name));}
 static BandHitCandidate pmt(int side,double time,float fadc){return new BandHitCandidate(5,2,2,side,1000,500,time,fadc,0,side,side);}
 static ArrayList<BandHitCandidate> pair(double l,double r){return new ArrayList<>(List.of(pmt(0,l,80),pmt(1,r,80)));}
 static class Engine extends BANDEngine {
  SnapshotConstants snapshot;int loads=0;
  Engine(SnapshotConstants s){snapshot=s;}
  @Override public String getEngineConfigString(String key,String fallback){return key.equals("bandReconstruction")?"cpp":super.getEngineConfigString(key,fallback);}
  @Override public ConstantsManager getConstantsManager(){return new ConstantsManager(){@Override public IndexedTable getConstants(int run,String table){loads++;return snapshot.getConstants(6363,table);}};}
 }
 public static void main(String[] a)throws Exception {
  path=a[0];SnapshotConstants s=controlled();BandCalibration c=calibration(s);
  BandHit h=BandReconstruction.find(pair(100,100),c).get(0);
  check("signed negative LR offsets",h.GetMeanTime_TDC()==102 && h.GetMeanTime_FADC()==81 && h.GetX()==-20 && h.GetZ()==-3);
  check("energy retains reference exponential",h.GetEnergy()>10 && h.GetEnergy()<10.00001);
  ArrayList<BandHitCandidate> low=new ArrayList<>();
  for(int side=0;side<2;side++)low.add(new BandHitCandidate(5,2,2,side,100,500,100,80,0,side,side));
  check("subthreshold bars retained for later neutron selection",BandReconstruction.find(low,c).get(0).GetEnergy()<2);
  check("missing opposite PMT returns no bar",BandReconstruction.find(new ArrayList<>(List.of(pmt(0,100,80))),c).isEmpty());
  check("length boundary inclusive",BandReconstruction.find(pair(116,100),c).size()==1);
  check("outside length boundary rejected",BandReconstruction.find(pair(116.0001,100),c).isEmpty());
  ArrayList<BandHitCandidate> reversed=pair(100,100);Collections.reverse(reversed);BandHit rev=BandReconstruction.find(reversed,c).get(0);
  check("links follow source list after ordering",rev.GetIndexLpmt()==1 && rev.GetIndexRpmt()==0);
  s=controlled();table(s,"bars").setDoubleValue(0.,"status",5,2,2);check("disabled bar rejected",BandReconstruction.find(pair(100,100),calibration(s)).isEmpty());
  s=controlled();table(s,"bars").setDoubleValue(0.,"adc_per_mev",5,2,2);SnapshotConstants zero=s;rejects("invalid active energy factor",()->calibration(zero));
  SnapshotConstants missing=controlled();rejects("missing table fails closed",()->new BandCalibration(name->name.endsWith("bars")?null:missing.getConstants(6363,name)));
  IndexedTable empty=new IndexedTable(3,"status/D");rejects("missing channel fails closed",()->new BandCalibration(name->name.endsWith("bars")?empty:missing.getConstants(6363,name)));
  s=controlled();table(s,"bars").setDoubleValue(Double.NaN,"x",5,2,2);SnapshotConstants nan=s;rejects("nonfinite constants fail closed",()->calibration(nan));
  check("invalid physical channels rejected",!HitReader.physicalChannel(6,6,6)&&!HitReader.physicalChannel(5,5,1)&&!HitReader.physicalChannel(2,2,0));
  // Table mutation after construction cannot change the immutable run snapshot.
  s=controlled();c=calibration(s);table(s,"bars").setDoubleValue(400.,"x",5,2,2);check("calibration snapshot immutable",BandReconstruction.find(pair(100,100),c).get(0).GetX()==-20);
  ArrayList<BandHit> many=new ArrayList<>();for(int i=0;i<101;i++)many.add(new BandHit());new BandHitFinder().advancedHitFinder(many,true,5,100);
  check("laser status retains precedence over veto",many.stream().allMatch(x->x.GetStatus()==1));
  HipoDataSync factory=new HipoDataSync();Engine engine=new Engine(controlled());engine.init();DataEvent event=factory.createEvent();DataBank run=event.createBank("RUN::config",1);run.setInt("run",0,6363);event.appendBank(run);
  engine.processDataEventUser(event);int initial=engine.loads;engine.processDataEventUser(event);check("same run reuses calibration",engine.loads==initial);
  run.setInt("run",0,6364);event.removeBank("RUN::config");event.appendBank(run);engine.processDataEventUser(event);check("run transition reloads tables",engine.loads==2*initial);
  engine.setVariation("another-profile");engine.processDataEventUser(event);check("variation change reloads calibration",engine.loads==3*initial);
  engine.setTimeStamp("10/14/2026-11:30:00");engine.processDataEventUser(event);check("timestamp change reloads calibration",engine.loads==4*initial);
  DataBank badAdc=event.createBank("BAND::adc",1),badTdc=event.createBank("BAND::tdc",1);
  for(DataBank b:new DataBank[]{badAdc,badTdc}){b.setByte("sector",0,(byte)6);b.setByte("layer",0,(byte)6);b.setShort("component",0,(short)6);}
  badAdc.setByte("order",0,(byte)0);badAdc.setInt("ADC",0,1000);badAdc.setInt("amplitude",0,500);badAdc.setFloat("time",0,80);
  badTdc.setByte("order",0,(byte)2);badTdc.setInt("TDC",0,20000);event.appendBanks(badAdc,badTdc);
  check("photodiode excluded by actual raw reader",HitReader.getCppCandidates(event).isEmpty());
  event.appendBank(event.createBank("MC::Event",1));rejects("data MC mismatch fails closed",()->engine.processDataEventUser(event));
  System.out.println("COMPLETE "+passed+" BAND port contract tests");
 }
}
