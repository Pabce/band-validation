package org.jlab.detector.decode;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.json.*;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.evio.*;
import org.jlab.utils.groups.IndexedTable;

/** Audit CODA hardware translation and waveform fitting, independently of RCDB headers. */
public class LaserDecodeAudit {
    static JSONObject table(IndexedTable t) {
        JSONArray columns=new JSONArray(), rows=new JSONArray();
        for(int c=0;c<t.getColumnCount();c++) columns.put(t.getColumnName(c));
        for(int r=0;r<t.getRowCount();r++) {
            JSONArray row=new JSONArray();
            for(int c=0;c<t.getColumnCount();c++) row.put(t.getValueAt(r,c).toString());
            rows.put(row);
        }
        return new JSONObject().put("columns",columns).put("rows",rows);
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("input.evio output.json count CCDB-timestamp");
        Path dest=Path.of(args[1]);
        if(Files.exists(dest)) throw new IllegalArgumentException("Output exists");
        CodaEventDecoder coda=new CodaEventDecoder();
        DetectorEventDecoder decoder=new DetectorEventDecoder();
        decoder.setVariation("default"); decoder.setTimestamp(args[3]);
        EvioSource source=new EvioSource(); source.open(args[0]);
        Map<String,JSONObject> channels=new TreeMap<>();
        Set<Integer> runs=new TreeSet<>(), times=new TreeSet<>();
        JSONObject snapshots=new JSONObject();
        int n=0, adc=0, tdc=0, unmappedADC=0, unmappedTDC=0, missingFadc=0, pulses=0;
        while(source.hasEvent() && n<Integer.parseInt(args[2])) {
            EvioDataEvent event=(EvioDataEvent)source.getNextEvent();
            List<DetectorDataDgtz> data=coda.getDataEntries(event);
            List<FADCData> packed=coda.getADCEntries(event);
            if(packed!=null) data.addAll(FADCData.convert(packed));
            int run=coda.getRunNumber(); runs.add(run); times.add(coda.getUnixTime());
            if(run<=0) {n++;continue;}
            decoder.setRunNumber(run);
            IndexedTable tt=decoder.translationManager.getConstants(run,"/daq/tt/band");
            IndexedTable fadc=decoder.fitterManager.getConstants(run,"/daq/fadc/band");
            if(!snapshots.has(Integer.toString(run))) snapshots.put(Integer.toString(run),new JSONObject().put("/daq/tt/band",table(tt)).put("/daq/fadc/band",table(fadc)));
            decoder.translate(data);
            for(DetectorDataDgtz d:data) {
                var dd=d.getDescriptor();
                if(dd.getType()==DetectorType.BAND && d.getADCSize()>0 && !fadc.hasEntry(dd.getCrate(),dd.getSlot(),dd.getChannel())) missingFadc+=d.getADCSize();
                for(int k=0;k<d.getADCSize();k++) if(d.getADCData(k).getPulseSize()>0) pulses++;
            }
            decoder.fitPulses(data);
            for(DetectorDataDgtz d:data) {
                if(d.getADCSize()==0 && d.getTDCSize()==0) continue;
                var dd=d.getDescriptor(); String key=dd.getCrate()+":"+dd.getSlot()+":"+dd.getChannel();
                JSONObject row=channels.computeIfAbsent(key,k->new JSONObject().put("crate",dd.getCrate()).put("slot",dd.getSlot()).put("channel",dd.getChannel()).put("type",dd.getType().toString()).put("sector",dd.getSector()).put("layer",dd.getLayer()).put("component",dd.getComponent()).put("order",dd.getOrder()).put("adc_rows",0).put("tdc_rows",0));
                row.put("adc_rows",row.getInt("adc_rows")+d.getADCSize()).put("tdc_rows",row.getInt("tdc_rows")+d.getTDCSize());
                adc+=d.getADCSize(); tdc+=d.getTDCSize();
                if(dd.getType()==DetectorType.UNDEFINED) {unmappedADC+=d.getADCSize();unmappedTDC+=d.getTDCSize();}
                if(d.getADCSize()>0 && !row.has("first_adc")) {
                    var a=d.getADCData(0);
                    row.put("first_adc",a.getADC()).put("first_amplitude",a.getHeight()-a.getPedestal()).put("first_pedestal",a.getPedestal()).put("first_time",a.getTime()).put("pulse_samples",a.getPulseSize());
                }
            }
            if(++n%100==0) System.out.println("PROGRESS laser hardware audit events="+n+" channels="+channels.size());
        }
        source.close();
        JSONArray dates=new JSONArray();for(int t:times) dates.put(Instant.ofEpochSecond(t).toString());
        JSONObject result=new JSONObject().put("source",args[0]).put("records",n).put("run_numbers",runs).put("unix_times_utc",dates).put("variation","default").put("ccdb_timestamp",args[3]).put("adc_rows",adc).put("tdc_rows",tdc).put("waveform_rows",pulses).put("unmapped_adc_rows",unmappedADC).put("unmapped_tdc_rows",unmappedTDC).put("band_adc_without_fadc_config",missingFadc).put("channels",channels).put("tables",snapshots);
        Files.writeString(dest,result.toString(2)+"\n");
        System.out.println("COMPLETE laser hardware audit records="+n+" ADC="+adc+" TDC="+tdc+" unmapped="+unmappedADC+"/"+unmappedTDC+" missingFadc="+missingFadc);
    }
}
