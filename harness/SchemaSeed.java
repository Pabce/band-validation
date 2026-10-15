import java.nio.file.*;
import org.jlab.io.hipo.HipoDataSync;
/** Create a schema-seed HIPO with one empty event for synthetic fixtures; no experimental data. */
public class SchemaSeed {
 public static void main(String[] args) throws Exception {
  Path output=Path.of(args[0]);
  if(Files.exists(output))throw new IllegalArgumentException("Output exists");
  HipoDataSync writer=new HipoDataSync();
  writer.open(output+".partial");
  // HIPO readers require a data record to scan the trailer reliably.
  writer.writeEvent(writer.createEvent());writer.close();
  Files.move(Path.of(output+".partial"),output);
  System.out.println("COMPLETE synthetic schema seed (one empty event)");
 }
}
