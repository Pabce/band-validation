import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Reopen hit-only HIPO output and verify values, identities and all source links. */
public class HitOnlyReadback {
 static int id(DataBank b,int i){return 100*b.getByte("sector",i)+10*b.getByte("layer",i)+b.getShort("component",i);}
 static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static void value(JSONObject o,String key,float v){require(o.isNull(key)?!Float.isFinite(v):o.getDouble(key)==v,"Changed "+key);}
 public static void main(String[] a)throws Exception {
  HipoDataSource in=new HipoDataSource();in.open(a[0]);int n=0;
  try(BufferedReader json=Files.newBufferedReader(Path.of(a[1]))){
   while(in.hasEvent()){
    DataEvent e=in.getNextEvent();String line=json.readLine();require(line!=null,"Extra HIPO event");JSONObject x=new JSONObject(line);n++;
    require(!e.hasBank("REC::Event")&&!e.hasBank("BAND::neutrons")&&!e.hasBank("BAND::neutronHits"),"Unexpected timing/selection bank");
    DataBank run=e.getBank("RUN::config");require(run.getInt("run",0)==x.getInt("run")&&run.getInt("event",0)==x.getInt("event"),"Input identity");
    JSONArray pmts=x.getJSONArray("pmts"),bars=x.getJSONArray("bars");Map<Integer,Integer> ids=new HashMap<>();
    require((e.hasBank("BAND::rawhits")?e.getBank("BAND::rawhits").rows():0)==pmts.length(),"PMT count");
    if(pmts.length()>0){DataBank b=e.getBank("BAND::rawhits"),ad=e.getBank("BAND::adc"),td=e.getBank("BAND::tdc");
     for(int i=0;i<b.rows();i++){JSONObject o=pmts.getJSONObject(i);int pid=id(b,i)*10+b.getShort("side",i)-1;ids.put(i,pid);require(pid==o.getInt("id"),"PMT identity");
      int ai=b.getShort("indexAdc",i),ti=b.getShort("indexTdc",i);require(ai==o.getInt("adc_row")&&ti==o.getInt("tdc_row"),"Raw row link");require(ai>=0&&ai<ad.rows()&&ti>=0&&ti<td.rows(),"Raw row range");
      require(id(ad,ai)*10+Math.floorMod(ad.getByte("order",ai),10)==pid&&id(td,ti)*10+Math.floorMod(td.getByte("order",ti),10)-2==pid,"Raw row identity");
      value(o,"adc",b.getFloat("adc",i));value(o,"amplitude",b.getFloat("ampl",i));value(o,"time_phase",b.getFloat("timeCorr",i));value(o,"time_fadc",b.getFloat("timeFadc",i));
     }
    }
    int seen=0;for(String name:new String[]{"BAND::hits","BAND::laser"})if(e.hasBank(name)){DataBank b=e.getBank(name);for(int i=0;i<b.rows();i++){
     JSONObject o=bars.getJSONObject(seen++);require(name.equals(o.getString("bank"))&&id(b,i)==o.getInt("id")&&b.getShort("status",i)==o.getInt("status"),"Bar identity/status");
     require(Objects.equals(ids.get((int)b.getShort("indexLpmt",i)),o.getInt("left_id"))&&Objects.equals(ids.get((int)b.getShort("indexRpmt",i)),o.getInt("right_id")),"Bar PMT link");
     for(String k:new String[]{"time","x","y","z","energy"})value(o,k,b.getFloat(k,i));value(o,"time_fadc",b.getFloat("timeFadc",i));value(o,"diff",b.getFloat("difftime",i));value(o,"diff_fadc",b.getFloat("difftimeFadc",i));
    }}require(seen==bars.length(),"Bar count");if(n%1000==0)System.out.println("PROGRESS hit readback="+n);
   }require(json.readLine()==null,"Missing HIPO events");
  }in.close();System.out.println("PASS hit readback="+n);
 }
}
