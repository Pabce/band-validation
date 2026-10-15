import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Raw pair counts are an upper bound before matching/calibration, never reconstructed laser classifications. */
public class BandMultiplicity {
 static int id(DataBank b,int i){return 100*b.getByte("sector",i)+10*b.getByte("layer",i)+b.getShort("component",i);}
 static void add(Map<Integer,Integer> m,int n){m.merge(n,1,Integer::sum);}
 public static void main(String[] a)throws Exception {
  HipoDataSource src=new HipoDataSource();src.open(a[0]);int n=0,rec=0,max=Integer.parseInt(a[2]),blocked=0,vetoBlocked=0;
  Map<Integer,Integer> adcHist=new TreeMap<>(),pairHist=new TreeMap<>(),barHist=new TreeMap<>(),survHist=new TreeMap<>();Set<Integer> ids=new TreeSet<>();JSONArray high=new JSONArray();
  while(src.hasEvent()&&n<max){DataEvent e=src.getNextEvent();int na=0;Set<Integer> adc=new HashSet<>(),tdc=new HashSet<>();
   if(e.hasBank("BAND::adc")){DataBank b=e.getBank("BAND::adc");na=b.rows();for(int i=0;i<b.rows();i++)adc.add(id(b,i)*10+Math.floorMod(b.getByte("order",i),10));}
   if(e.hasBank("BAND::tdc")){DataBank b=e.getBank("BAND::tdc");for(int i=0;i<b.rows();i++)tdc.add(id(b,i)*10+Math.floorMod(b.getByte("order",i),10)-2);}
   adc.retainAll(tdc);Set<Integer> pairs=new HashSet<>();for(int p:adc)if((p/100)%10==6 || adc.contains(p^1))pairs.add(p/10);
   add(adcHist,na);add(pairHist,pairs.size());int nb=0;
   for(String name:new String[]{"BAND::hits","BAND::laser"})if(e.hasBank(name)){DataBank b=e.getBank(name);nb+=b.rows();for(int i=0;i<b.rows();i++)ids.add(id(b,i));}
   add(barHist,nb);if(e.hasBank("REC::Event"))rec++;
   if(pairs.size()>100 && high.length()<10)high.put(new JSONObject().put("ordinal",n).put("raw_pair_upper_bound",pairs.size()));
   if(e.hasBank("BAND::neutrons"))add(survHist,(int)e.getBank("BAND::neutrons").getShort("nSurvivors",0));
   if(e.hasBank("BAND::neutronHits")){DataBank b=e.getBank("BAND::neutronHits");for(int i=0;i<b.rows();i++){int k=b.getShort("blocker",i);if(k>=0){blocked++;if((b.getShort("barId",k)/10)%10==6)vetoBlocked++;}}}
   if(++n%1000==0)System.out.println("PROGRESS multiplicity records="+n);
  }
  JSONObject o=new JSONObject().put("source",a[0]).put("records",n).put("reached_eof",!src.hasEvent()).put("rec_events",rec).put("adc_row_histogram",adcHist).put("raw_pair_upper_bound_histogram",pairHist).put("reconstructed_bar_histogram",barHist).put("bar_ids",ids).put("survivor_histogram",survHist).put("blocked_hit_rows",blocked).put("veto_blocked_hit_rows",vetoBlocked).put("high_raw_pair_examples",high);
  src.close();Files.writeString(Path.of(a[1]),o.toString(2)+"\n");System.out.println("COMPLETE multiplicity "+a[1]);
 }
}
