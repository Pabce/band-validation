import java.nio.file.*;
import java.util.*;
import org.jlab.rec.band.hit.*;
import org.jlab.rec.band.banks.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
import org.jlab.service.band.BANDEngine;
import org.jlab.detector.calib.utils.ConstantsManager;
public class NeutronContractTests {
 static int passed;
 static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static void rejects(String name,Runnable fn){try{fn.run();throw new AssertionError(name);}catch(IllegalArgumentException ex){passed++;System.out.println("PASS "+name);}}
 static BandHit hit(int component,double energy){BandHit h=new BandHit();h.SetSector(2);h.SetLayer(3);h.SetComponent(component);h.SetEnergy(energy);h.SetMeanTime_TDC(100);return h;}
 static class Engine extends BANDEngine {
  String mode,selection;SnapshotConstants snapshot;
  Engine(String mode,String selection,String path){this.mode=mode;this.selection=selection;snapshot=new SnapshotConstants(path);}
  @Override public String getEngineConfigString(String k,String def){return k.equals("bandReconstruction")?mode:k.equals("bandNeutronSelection")?selection:super.getEngineConfigString(k,def);}
  @Override public ConstantsManager getConstantsManager(){return snapshot;}
 }
 static DataEvent event(HipoDataSync factory,boolean rec,float start){DataEvent e=factory.createEvent();DataBank r=e.createBank("RUN::config",1);r.setInt("run",0,6363);e.appendBank(r);if(rec){DataBank b=e.createBank("REC::Event",1);b.setFloat("startTime",0,start);e.appendBank(b);}return e;}
 public static void main(String[] args)throws Exception {
  rejects("legacy config rejects neutron selection",()->new Engine("legacy","true",args[0]).init());
  rejects("invalid boolean config",()->new Engine("cpp","yes",args[0]).init());
  HipoDataSync factory=new HipoDataSync();Engine engine=new Engine("cpp","true",args[0]);engine.init();
  rejects("missing start-time bank",()->engine.processDataEventUser(event(factory,false,0)));
  rejects("nonfinite start time",()->engine.processDataEventUser(event(factory,true,Float.NaN)));
  DataEvent empty=event(factory,true,90);engine.processDataEventUser(empty);
  check("empty event has explicit rejected summary",empty.getBank("BAND::neutrons").getByte("decision",0)==0 && empty.getBank("BAND::neutrons").getShort("lead",0)==-1 && !empty.hasBank("BAND::neutronHits"));
  Engine disabled=new Engine("cpp","false",args[0]);disabled.init();disabled.processDataEventUser(empty);
  check("disabled selection clears old decision",!empty.hasBank("BAND::neutrons"));
  engine.processDataEventUser(empty);
  empty.appendBank(empty.createBank("BAND::hits",1));empty.appendBank(empty.createBank("BAND::neutronHits",1));engine.processDataEventUser(empty);
  check("rerun clears stale reconstructed and neutron rows",!empty.hasBank("BAND::hits")&&!empty.hasBank("BAND::neutronHits"));
  DataBank r=empty.getBank("RUN::config");r.setInt("run",0,0);empty.removeBank("RUN::config");empty.appendBank(r);engine.processDataEventUser(empty);
  check("metadata clears stale neutron decision",!empty.hasBank("BAND::neutrons"));
  BandHit low=hit(1,Math.nextDown(2.0)),high=hit(2,2.0);low.SetStatus(1);high.SetStatus(1);
  ArrayList<BandHit> bars=new ArrayList<>(List.of(high,low));var result=BandNeutronSelector.select(bars,90);
  check("input rows and statuses unchanged",bars.get(0)==high&&high.GetStatus()==1&&low.GetEnergy()==Math.nextDown(2.));
  check("double threshold differs from rounded float",(float)low.GetEnergy().doubleValue()==2f && !result.hits().get(1).eligible());
  rejects("duplicate identities",()->BandNeutronSelector.select(List.of(high,high),90));
  BandHit invalid=hit(3,2);invalid.SetX(Double.NaN);rejects("nonfinite hit rejected",()->BandNeutronSelector.select(List.of(invalid),90));
  try{result.hits().clear();throw new AssertionError("mutable result");}catch(UnsupportedOperationException ex){check("immutable result diagnostics",true);}
  DataEvent ev=event(factory,true,90);RecoBankWriter.appendBANDBanks(ev,new ArrayList<>(),bars);RecoBankWriter.appendNeutronBanks(ev,bars,result);
  Path output=Path.of(args[1]);if(Files.exists(output))throw new IllegalArgumentException("Existing roundtrip file");
  factory.open(output+".partial");factory.writeEvent(ev);factory.close();Files.move(Path.of(output+".partial"),output);
  HipoDataSource reader=new HipoDataSource();reader.open(output.toString());DataEvent restored=reader.getNextEvent();
  DataBank s=restored.getBank("BAND::neutrons"),d=restored.getBank("BAND::neutronHits"),laser=restored.getBank("BAND::laser");
  check("HIPO summary roundtrip and laser source",s.getByte("source",0)==1&&s.getShort("lead",0)==0&&s.getShort("leadId",0)==232&&s.getShort("nEligible",0)==1&&s.getShort("nSurvivors",0)==1);
  check("HIPO double diagnostic precision",d.getDouble("energy",1)==Math.nextDown(2.)&&d.getByte("eligible",1)==0&&d.getDouble("tof",0)==10);
  check("laser rows and status preserved",laser.rows()==2&&laser.getShort("component",0)==2&&laser.getShort("status",1)==1&&!restored.hasBank("BAND::hits"));
  check("HIPO source links",d.getShort("hitIndex",0)==0&&d.getShort("barId",1)==231&&d.getShort("blocker",0)==-1);
  reader.close();System.out.println("COMPLETE "+passed+" neutron integration contracts");
 }
}
