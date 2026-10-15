import org.jlab.io.hipo.*;
import org.jlab.io.base.*;
public class InspectHipo {public static void main(String[] a){HipoDataSource s=new HipoDataSource();s.open(a[0]);for(int i=0;i<5&&s.hasEvent();i++){DataEvent e=s.getNextEvent();System.out.println("EVENT "+i);for(String n:new String[]{"RUN::config","REC::Event","BAND::adc","BAND::tdc"}){System.out.println(n+" "+e.hasBank(n));if(e.hasBank(n))e.getBank(n).show();}}s.close();}}
