import java.nio.file.*;
import org.jlab.io.base.*;
import org.jlab.io.hipo.*;
/** A derived test input; original frozen events are never changed. */
public class RemoveRecTiming {
 public static void main(String[] a)throws Exception {
  if(Files.exists(Path.of(a[1])))throw new IllegalArgumentException("Output exists");
  HipoDataSource in=new HipoDataSource();in.open(a[0]);HipoDataSync out=in.createWriter();out.open(a[1]+".partial");int n=0;
  while(in.hasEvent()){DataEvent ev=in.getNextEvent();ev.removeBanks("REC::Event","BAND::neutrons","BAND::neutronHits");out.writeEvent(ev);if(++n%1000==0)System.out.println("PROGRESS strip timing records="+n);}
  out.close();in.close();Files.move(Path.of(a[1]+".partial"),Path.of(a[1]));System.out.println("COMPLETE strip timing records="+n);
 }
}
