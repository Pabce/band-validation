import java.nio.file.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Explicit preselection matching the reference skimmer's REC::Event requirement. */
public class FilterTimedHipo {
 public static void main(String[] a)throws Exception {
  Path dest=Path.of(a[1]);if(Files.exists(dest))throw new IllegalArgumentException("Output exists");
  HipoDataSource src=new HipoDataSource();src.open(a[0]);HipoDataSync out=new HipoDataSync(src.getReader().getSchemaFactory());out.open(dest+".partial");
  JSONArray excluded=new JSONArray();int n=0,kept=0;
  while(src.hasEvent()){
   DataEvent ev=src.getNextEvent();boolean hasRun=ev.hasBank("RUN::config")&&ev.getBank("RUN::config").rows()==1;
   int run=hasRun?ev.getBank("RUN::config").getInt("run",0):-1;int event=hasRun?ev.getBank("RUN::config").getInt("event",0):-1;
   boolean metadata=run==0&&!ev.hasBank("REC::Event")&&!ev.hasBank("BAND::adc")&&!ev.hasBank("BAND::tdc");
   boolean timing=hasRun&&ev.hasBank("REC::Event")&&ev.getBank("REC::Event").rows()==1&&Float.isFinite(ev.getBank("REC::Event").getFloat("startTime",0));
   if(metadata||timing){out.writeEvent(ev);kept++;}
   else excluded.put(new JSONObject().put("original_ordinal",n).put("run",run).put("event",event).put("adc_rows",ev.hasBank("BAND::adc")?ev.getBank("BAND::adc").rows():0).put("reason","missing run or finite REC::Event.startTime"));
   n++;if(n%10000==0)System.out.println("PROGRESS timing preselection input="+n+" kept="+kept);
  }
  out.close();src.close();Files.move(Path.of(dest+".partial"),dest);Files.writeString(Path.of(a[2]),new JSONObject().put("source",a[0]).put("input_records",n).put("kept_records",kept).put("excluded",excluded).toString(2)+"\n");
 }
}
