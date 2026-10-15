import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.utils.groups.IndexedTable;
/** External offline CCDB adapter; production reconstruction only sees IndexedTable. */
public class SnapshotConstants extends ConstantsManager {
 private final Map<String,IndexedTable> tables=new HashMap<>();
 private final int minRun,maxRun;
 public SnapshotConstants(String path) {
  try {
   JSONObject data=new JSONObject(Files.readString(Path.of(path)));
   minRun=data.getInt("run_min");maxRun=data.getInt("run_max");
   for(Object obj:data.getJSONArray("tables")) {
    JSONObject item=(JSONObject)obj;JSONArray columns=item.getJSONArray("columns");
    String[] format=new String[columns.length()-3];for(int i=3;i<columns.length();i++)format[i-3]=columns.getString(i)+"/D";
    IndexedTable table=new IndexedTable(3,format);
    for(Object row:item.getJSONArray("rows")) {
     JSONArray v=(JSONArray)row;int[] idx={v.getInt(0),v.getInt(1),v.getInt(2)};table.addEntry(idx);
     for(int i=3;i<columns.length();i++)table.setDoubleValue(v.getDouble(i),columns.getString(i),idx);
    }
    tables.put(item.getString("name"),table);
   }
  } catch(Exception e){throw new IllegalArgumentException("Cannot read calibration snapshot "+path,e);}
 }
 @Override public IndexedTable getConstants(int run,String table) {
  if(run<minRun || run>maxRun)throw new IllegalArgumentException("Run outside frozen snapshot: "+run);
  if(!tables.containsKey(table))throw new IllegalArgumentException("Missing snapshot table "+table);
  return tables.get(table);
 }
}
