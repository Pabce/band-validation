import java.io.*;
import java.nio.file.*;
import org.json.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** Verify newly written HIPO banks after closing/reopening the file. */
public class NeutronHipoReadback {
 static int id(DataBank b,int i){return b.getByte("sector",i)*100+b.getByte("layer",i)*10+b.getShort("component",i);}
 static void require(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 public static void main(String[] args)throws Exception {
  HipoDataSource source=new HipoDataSource();source.open(args[0]);int count=0;
  try(BufferedReader json=Files.newBufferedReader(Path.of(args[1]))){
   while(source.hasEvent()){
    DataEvent ev=source.getNextEvent();String line=json.readLine();require(line!=null,"extra HIPO record");JSONObject expected=new JSONObject(line);count++;
    require(ev.getBank("RUN::config").getInt("event",0)==expected.getInt("event"),"event identity");
    if(expected.has("input_status")){require(!ev.hasBank("BAND::neutrons"),"metadata selection");continue;}
    DataBank n=ev.getBank("BAND::neutrons");require(n.rows()==1,"summary rows");JSONObject c=expected.getJSONObject("candidate");
    require((n.getByte("decision",0)!=0)==c.getBoolean("accepted") && n.getShort("nSurvivors",0)==c.getInt("survivors") && n.getShort("leadId",0)==c.getInt("lead_id"),"candidate changed in HIPO");
    int eligible=0,survivors=0;String name=n.getByte("source",0)==1?"BAND::laser":"BAND::hits";
    if(ev.hasBank("BAND::neutronHits")){
     DataBank d=ev.getBank("BAND::neutronHits"),h=ev.getBank(name);require(d.rows()==h.rows(),"detail/source count");
     for(int i=0;i<d.rows();i++){
      require(d.getShort("hitIndex",i)==i && d.getShort("barId",i)==id(h,i),"detail/source identity");
      int b=d.getShort("blocker",i);require(b>=-1 && b<d.rows(),"blocker row");
      if(d.getByte("survivor",i)==1){survivors++;require(d.getByte("eligible",i)==1 && h.getByte("layer",i)!=6 && b==-1,"survivor invariants");}
      if(b>=0)require(d.getByte("eligible",i)==1&&d.getByte("eligible",b)==1&&d.getByte("survivor",i)==0,"blocking invariants");
      if(d.getByte("eligible",i)==1){
       JSONObject e=expected.getJSONArray("hits").getJSONObject(eligible++);
       require(e.getInt("id")==id(h,i) && e.getDouble("tof")==d.getDouble("tof",i),"eligible order/TOF changed in HIPO");
      }
      require((float)d.getDouble("energy",i)==h.getFloat("energy",i),"energy source");
     }
     int lead=n.getShort("lead",0);
     if(c.getBoolean("accepted"))require(lead>=0&&lead<h.rows()&&id(h,lead)==c.getInt("lead_id")&&d.getByte("survivor",lead)==1,"lead link");
     else require(lead==-1,"rejected lead");
    }
    require(eligible==n.getShort("nEligible",0)&&survivors==n.getShort("nSurvivors",0),"counts");
    if(count%1000==0)System.out.println("PROGRESS HIPO readback records="+count);
   }
   require(json.readLine()==null,"missing HIPO records");
  }
  source.close();System.out.println("COMPLETE HIPO readback records="+count);
 }
}
