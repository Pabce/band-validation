import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.utils.groups.IndexedTable;
/** External exact-run snapshot router for mixed-run validation. */
public class CampaignConstants extends ConstantsManager {
 private final Map<Integer,SnapshotConstants> runs=new HashMap<>();
 public CampaignConstants(String manifest){
  try {
   JSONObject m=new JSONObject(Files.readString(Path.of(manifest)));
   for(String run:m.keySet())runs.put(Integer.parseInt(run),new SnapshotConstants(m.getString(run)));
  }catch(Exception e){throw new IllegalArgumentException("Invalid run map",e);}
 }
 @Override public IndexedTable getConstants(int run,String table){
  if(!runs.containsKey(run))throw new IllegalArgumentException("Unfrozen campaign run "+run);
  return runs.get(run).getConstants(run,table);
 }
}
