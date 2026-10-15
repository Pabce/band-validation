import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.json.*;
import org.jlab.rec.band.hit.*;
public class NeutronSelectorJava {
 public static void main(String[] args)throws Exception {
  Path dest=Path.of(args[1]);if(Files.exists(dest))throw new IOException("Existing output");int count=0;
  try(BufferedReader in=Files.newBufferedReader(Path.of(args[0]));BufferedWriter out=Files.newBufferedWriter(Path.of(args[1]+".partial"))){
   for(String line;(line=in.readLine())!=null;){
    String[] header=line.split(" ");int n=Integer.parseInt(header[2]);ArrayList<BandHit> hits=new ArrayList<>();
    for(int i=0;i<n;i++){
     String[] v=in.readLine().split(" ");int id=Integer.parseInt(v[0]);BandHit h=new BandHit();
     h.SetSector(id/100);h.SetLayer(id/10%10);h.SetComponent(id%10);h.SetEnergy(Double.parseDouble(v[1]));
     h.SetMeanTime_TDC(Double.parseDouble(v[2]));h.SetX(Double.parseDouble(v[3]));h.SetY(Double.parseDouble(v[4]));hits.add(h);
    }
    var r=BandNeutronSelector.select(hits,Double.parseDouble(header[1]));JSONArray details=new JSONArray();
    for(var h:r.hits())details.put(new JSONArray().put(h.barId()).put(h.eligible()?1:0).put(h.survivor()?1:0).put(h.blocker()));
    out.write(new JSONObject().put("name",header[0]).put("accepted",r.accepted()).put("survivors",r.survivorCount()).put("lead_id",r.leadId()).put("lead_row",r.leadRow()).put("hits",details).toString());out.newLine();
    if(++count%1000==0){out.flush();System.out.println("PROGRESS Java selector cases="+count);}
   }
  }
  Files.move(Path.of(args[1]+".partial"),dest);System.out.println("COMPLETE Java selector cases="+count);
 }
}
