import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
import org.jlab.service.band.BANDEngine;
import org.jlab.rec.band.constants.CalibrationConstantsLoader;
import org.jlab.utils.groups.IndexedTable;

/** Execute the actual frozen BAND engine and export its produced banks. */
public class HitOnlyJava {
 static void num(JSONObject o,String key,double x){o.put(key,Double.isFinite(x)?x:JSONObject.NULL);}
 static int id(DataBank b,int i){return b.getByte("sector",i)*100+b.getByte("layer",i)*10+b.getShort("component",i);}
 public static void main(String[] args)throws Exception{
  if(args.length!=5)throw new IllegalArgumentException("input output start count constants.json");
  Path dest=Path.of(args[1]);if(Files.exists(dest))throw new IOException("Refusing to overwrite completed output");
  long start=Long.parseLong(args[2]),count=Long.parseLong(args[3]);if(start<0||count<1)throw new IllegalArgumentException();
  HipoDataSource source=new HipoDataSource();source.open(args[0]);
  org.jlab.jnp.hipo4.data.SchemaFactory updated=new org.jlab.jnp.hipo4.data.SchemaFactory();
  updated.initFromDirectory(System.getenv("CLAS12DIR")+"/etc/bankdefs/hipo4");
  for(String name:new String[]{"BAND::neutrons","BAND::neutronHits"})source.getReader().getSchemaFactory().addSchema(updated.getSchema(name));
  HipoDataSync hipo=null;
  if(System.getProperty("band.output")!=null){
   if(Files.exists(Path.of(System.getProperty("band.output"))))throw new IOException("HIPO output exists");
   hipo=source.createWriter();hipo.open(System.getProperty("band.output")+".partial");
  }
  BANDEngine engine=new BANDEngine(){
   private final org.jlab.detector.calib.utils.ConstantsManager snapshot=new CampaignConstants(System.getProperty("band.tables"));
   @Override public String getEngineConfigString(String key,String fallback){
    if(key.equals("bandNeutronSelection"))return "false";
    if(key.equals("bandReconstruction"))return snapshot==null ? "legacy":"cpp";
    return super.getEngineConfigString(key,fallback);
   }
   @Override public org.jlab.detector.calib.utils.ConstantsManager getConstantsManager(){return snapshot==null ? super.getConstantsManager():snapshot;}

  };engine.init();engine.setVariation("default");engine.getConstantsManager().setTimeStamp("10/14/2026-11:30:00");
  long ordinal=0,written=0;int oldRun=-1;Path partial=Path.of(args[1]+".partial");
  try(BufferedWriter out=Files.newBufferedWriter(partial)){
   while(source.hasEvent()&&written<count){DataEvent ev=source.getNextEvent();if(ordinal++<start)continue;
    if(ev.hasBank("RUN::config") && ev.getBank("RUN::config").rows()==1 && ev.getBank("RUN::config").getInt("run",0)==0 && !ev.hasBank("REC::Event") && !ev.hasBank("BAND::adc") && !ev.hasBank("BAND::tdc")){
     JSONObject header=new JSONObject();header.put("ordinal",ordinal-1);header.put("run",0);header.put("event",ev.getBank("RUN::config").getInt("event",0));header.put("input_status","metadata");header.put("start_time",JSONObject.NULL);header.put("adc_rows",0);header.put("tdc_rows",0);header.put("pmts",new JSONArray());header.put("bars",new JSONArray());header.put("hits",new JSONArray());header.put("candidate",JSONObject.NULL);header.put("selection_evaluated",false);out.write(header.toString());out.newLine();written++;if(hipo!=null)hipo.writeEvent(ev);continue;
    }
    if(!ev.hasBank("RUN::config")||ev.getBank("RUN::config").rows()!=1)throw new IOException("Missing run row");
    if(ev.hasBank("REC::Event"))throw new IOException("Hit-only validation requires REC::Event removed");
    
    int run=ev.getBank("RUN::config").getInt("run",0), event=ev.getBank("RUN::config").getInt("event",0);

    int na=ev.hasBank("BAND::adc")?ev.getBank("BAND::adc").rows():0,nt=ev.hasBank("BAND::tdc")?ev.getBank("BAND::tdc").rows():0;
    // Remove old cooked output: it must not masquerade as newly reconstructed data.
    ev.removeBanks("BAND::rawhits","BAND::hits","BAND::laser","BAND::neutrons","BAND::neutronHits");
    if(!engine.processDataEventUser(ev))throw new IOException("Engine failure");
    if(ev.hasBank("BAND::neutrons")||ev.hasBank("BAND::neutronHits")||ev.hasBank("REC::Event"))throw new IOException("Unexpected selection/timing output");
    JSONObject row=new JSONObject();row.put("ordinal",ordinal-1);row.put("run",run);row.put("event",event);row.put("start_time",JSONObject.NULL);row.put("adc_rows",na);row.put("tdc_rows",nt);
    JSONArray pmts=new JSONArray(),bars=new JSONArray();Map<Integer,Integer> pmtIds=new HashMap<>();
    if(ev.hasBank("BAND::rawhits")){DataBank p=ev.getBank("BAND::rawhits");for(int i=0;i<p.rows();i++){
     int pid=id(p,i)*10+p.getShort("side",i)-1;pmtIds.put(i,pid);JSONObject o=new JSONObject();o.put("id",pid);
     int ar=p.getShort("indexAdc",i),tr=p.getShort("indexTdc",i);o.put("adc_row",ar);o.put("tdc_row",tr);
     if(ar<0||ar>=na||tr<0||tr>=nt)throw new IOException("Invalid raw-hit link");
     DataBank a=ev.getBank("BAND::adc"),t=ev.getBank("BAND::tdc");
     if(id(a,ar)*10+Math.floorMod(a.getByte("order",ar),10)!=pid||id(t,tr)*10+Math.floorMod(t.getByte("order",tr),10)-2!=pid)throw new IOException("Raw link identity mismatch");
     num(o,"adc",p.getFloat("adc",i));num(o,"amplitude",p.getFloat("ampl",i));num(o,"time_phase",p.getFloat("timeCorr",i));num(o,"time_fadc",p.getFloat("timeFadc",i));pmts.put(o);
    }}
    for(String bank:new String[]{"BAND::hits","BAND::laser"})if(ev.hasBank(bank)){DataBank b=ev.getBank(bank);for(int i=0;i<b.rows();i++){
     JSONObject o=new JSONObject();o.put("id",id(b,i));int layer=b.getByte("layer",i);o.put("layer",layer);o.put("status",b.getShort("status",i));o.put("bank",bank);
     int l=b.getShort("indexLpmt",i),r=b.getShort("indexRpmt",i);
     o.put("left_id",pmtIds.getOrDefault(l,-1));o.put("right_id",pmtIds.getOrDefault(r,-1));
     // One side is a dummy index in legacy Java veto output; validate both sides only for main bars.
     if((layer!=6||!"pmts".equals(System.getProperty("band.stage")))&&(!pmtIds.containsKey(l)||!pmtIds.containsKey(r)||pmtIds.get(l)/10!=id(b,i)||pmtIds.get(r)/10!=id(b,i)))throw new IOException("Invalid bar-to-PMT link");
     for(String field:new String[]{"time","x","y","z","energy"})num(o,field,b.getFloat(field,i));
     num(o,"time_fadc",b.getFloat("timeFadc",i));num(o,"diff",b.getFloat("difftime",i));num(o,"diff_fadc",b.getFloat("difftimeFadc",i));
     bars.put(o);
    }}
    row.put("pmts",pmts);row.put("bars",bars);row.put("hits",new JSONArray());row.put("candidate",JSONObject.NULL);row.put("selection_evaluated",false);if(hipo!=null)hipo.writeEvent(ev);
    out.write(row.toString());out.newLine();written++;if(written%1000==0){out.flush();System.out.println("PROGRESS java events="+written+" ordinal="+(ordinal-1));}
   }
  }
  source.close();if(hipo!=null){hipo.close();Files.move(Path.of(System.getProperty("band.output")+".partial"),Path.of(System.getProperty("band.output")));}if(written==0)throw new IOException("No events");Files.move(partial,dest);System.out.println("COMPLETE java events="+written);
 }
}
