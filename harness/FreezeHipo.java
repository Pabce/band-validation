import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
import org.jlab.jnp.hipo4.data.Schema;
/** Preserve the first N events unchanged, with their original bank dictionary. */
public class FreezeHipo {
 public static void main(String[] args)throws Exception{
  if(args.length!=4)throw new IllegalArgumentException("input output count metadata.json");
  Path dest=Path.of(args[1]);if(Files.exists(dest))throw new IllegalArgumentException("output exists");
  HipoDataSource in=new HipoDataSource();in.open(args[0]);
  HipoDataSync out=new HipoDataSync(in.getReader().getSchemaFactory());String partial=args[1]+".partial";out.open(partial);
  JSONObject info=new JSONObject();info.put("source",args[0]);JSONArray schemas=new JSONArray();
  for(Schema schema:in.getReader().getSchemaFactory().getSchemaList())schemas.put(new JSONObject(schema.getJsonString()));
  info.put("schemas",schemas);int n=0,raw=0,tdc=0;Set<Integer> runs=new TreeSet<>();int first=-1,last=-1;
  while(in.hasEvent()&&n<Integer.parseInt(args[2])){DataEvent ev=in.getNextEvent();out.writeEvent(ev);n++;
   if(ev.hasBank("BAND::adc"))raw++;if(ev.hasBank("BAND::tdc"))tdc++;
   if(ev.hasBank("RUN::config")){DataBank b=ev.getBank("RUN::config");runs.add(b.getInt("run",0));last=b.getInt("event",0);if(first<0)first=last;}
   if(n%1000==0)System.out.println("PROGRESS freeze events="+n+" with_adc="+raw);
  }
  out.close();in.close();Files.move(Path.of(partial),dest);info.put("events",n);info.put("events_with_adc",raw);info.put("events_with_tdc",tdc);info.put("runs",runs);info.put("first_event",first);info.put("last_event",last);
  Files.writeString(Path.of(args[3]),info.toString(2)+"\n");System.out.println("COMPLETE freeze "+n+" events, BAND adc="+raw+", tdc="+tdc);
 }
}
