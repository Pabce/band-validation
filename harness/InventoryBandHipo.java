import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Bounded input inventory only; no reconstruction or event selection. */
public class InventoryBandHipo {
 public static void main(String[] a)throws Exception{
  HipoDataSource src=new HipoDataSource();if(a.length>3)src.getReader().setTags(Long.parseLong(a[3]));src.open(a[0]);int max=Integer.parseInt(a[2]),n=0,adc=0,tdc=0,mc=0,rec=0,sentinel=0;long ar=0,tr=0;Set<Integer> runs=new TreeSet<>();JSONArray examples=new JSONArray();Set<Integer> mcPids=new TreeSet<>();
  while(src.hasEvent()&&n<max){DataEvent ev=src.getNextEvent();n++;int na=ev.hasBank("BAND::adc")?ev.getBank("BAND::adc").rows():0,nt=ev.hasBank("BAND::tdc")?ev.getBank("BAND::tdc").rows():0;adc+=na>0?1:0;tdc+=nt>0?1:0;ar+=na;tr+=nt;
   mc+=ev.hasBank("MC::Event")?1:0;rec+=ev.hasBank("REC::Event")?1:0;
   if(ev.hasBank("RUN::config"))runs.add(ev.getBank("RUN::config").getInt("run",0));
   if(ev.hasBank("REC::Event")&&ev.getBank("REC::Event").getFloat("startTime",0)==-1000f)sentinel++;
   if(ev.hasBank("MC::Particle")){DataBank b=ev.getBank("MC::Particle");for(int i=0;i<b.rows();i++)mcPids.add(b.getInt("pid",i));}
   if(na>0 && examples.length()<3){JSONObject x=new JSONObject().put("ordinal",n-1).put("adc_rows",na).put("tdc_rows",nt);DataBank b=ev.getBank("BAND::adc");x.put("first_adc",b.getInt("ADC",0)).put("first_amplitude",b.getInt("amplitude",0));if(nt>0)x.put("first_tdc",ev.getBank("BAND::tdc").getInt("TDC",0));examples.put(x);}
   if(n%10000==0)System.out.println("PROGRESS inventory "+a[0]+" records="+n+" BANDadc="+adc);
  }
  JSONObject result=new JSONObject().put("source",a[0]).put("indexed_records",src.getSize()).put("tag_filter",a.length>3?a[3]:"all").put("record_limit",max).put("reached_eof",!src.hasEvent()).put("records",n).put("events_with_adc",adc).put("events_with_tdc",tdc).put("adc_rows",ar).put("tdc_rows",tr).put("mc_events",mc).put("rec_events",rec).put("start_time_minus1000",sentinel).put("runs",runs).put("mc_pids",mcPids).put("examples",examples);
  src.close();Files.writeString(Path.of(a[1]),result.toString(2)+"\n");System.out.println("COMPLETE "+result.toString());
 }
}
