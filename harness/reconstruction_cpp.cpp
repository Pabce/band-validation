// Instrument the frozen C++ reconstruction without applying analysis skim cuts.
#include <algorithm>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <memory>
#include <stdexcept>
#include "reader.h"
#include "bandreco.h"
#include "readhipo_helper.h"

static void number(std::ostream& out,double x){if(std::isfinite(x))out<<std::setprecision(17)<<x;else out<<"null";}
static void kv(std::ostream& out,const char* key,double x){out<<",\""<<key<<"\":";number(out,x);}
static void requireOrder(hipo::schema &s, std::initializer_list<const char*> fields){int i=0;for(auto f:fields){if(i>=s.getEntries()||s.getEntryName(i++)!=f)throw std::runtime_error("Unexpected positional schema for "+s.getName());}}
int main(int argc,char**argv){try{
 if(argc!=7)throw std::runtime_error("usage: baseline_cpp input.hipo output.jsonl period start count mc");
 const bool simulation=std::stoi(argv[6])!=0;const int period=std::stoi(argv[3]);const long start=std::stol(argv[4]),count=std::stol(argv[5]);
 if(start<0||count<1||period<0||period>3)throw std::runtime_error("Initial baseline supports RGB data periods 0..3 only");
 if(std::filesystem::exists(argv[2]))throw std::runtime_error("Refusing to overwrite completed output");
 std::string partial=std::string(argv[2])+".partial";
 std::ofstream out(partial);if(!out)throw std::runtime_error("Cannot create output");
 hipo::reader reader;reader.open(argv[1]);hipo::dictionary dict;reader.readDictionary(dict);
 const auto names=dict.getSchemaList();for(const char* name:{"RUN::config","REC::Event","BAND::adc","BAND::tdc"})
  if(std::find(names.begin(),names.end(),name)==names.end())throw std::runtime_error(std::string("Missing schema ")+name);
 const bool hasMCSchema=std::find(names.begin(),names.end(),"MC::Event")!=names.end();
 std::unique_ptr<hipo::bank> mc;if(hasMCSchema)mc=std::make_unique<hipo::bank>(dict.getSchema("MC::Event"));
 requireOrder(dict.getSchema("BAND::adc"),{"sector","layer","component","order","ADC","amplitude","time","ped"});
 requireOrder(dict.getSchema("BAND::tdc"),{"sector","layer","component","order","TDC"});
 requireOrder(dict.getSchema("RUN::config"),{"run","event","unixtime","trigger","timestamp"});
 hipo::bank run(dict.getSchema("RUN::config")),rec(dict.getSchema("REC::Event")),adc(dict.getSchema("BAND::adc")),tdc(dict.getSchema("BAND::tdc"));
 std::unique_ptr<BANDReco> band;int oldRun=-1;long ordinal=0,written=0;hipo::event ev;
 while(reader.next()&&written<count){reader.read(ev);if(ordinal++<start)continue;
  ev.getStructure(run);ev.getStructure(rec);ev.getStructure(adc);ev.getStructure(tdc);
  if(mc){ev.getStructure(*mc);if((mc->getRows()>0)!=simulation)throw std::runtime_error("MC mode mismatch");}
  if(run.getRows()==1 && run.getInt("run",0)==0 && rec.getRows()==0 && adc.getRows()==0 && tdc.getRows()==0){
   out<<"{\"ordinal\":"<<ordinal-1<<",\"run\":0,\"event\":"<<run.getInt("event",0)<<",\"input_status\":\"metadata\",\"start_time\":null,\"adc_rows\":0,\"tdc_rows\":0,\"pmts\":[],\"bars\":[],\"hits\":[],\"candidate\":null}\n";++written;continue;
  }
  if(run.getRows()!=1||rec.getRows()!=1)throw std::runtime_error("Missing run/start-time row at ordinal "+std::to_string(ordinal-1));
  int r=run.getInt("run",0),e=run.getInt("event",0);double st=rec.getFloat("startTime",0);
  int inferred=(r>6100&&r<6400)?0:(r>=6400&&r<6800)?1:(r>11320&&r<11580)?2:(r>=11286&&r<11304)?3:-1;
  if(inferred!=period||!std::isfinite(st))throw std::runtime_error("Run/period or start-time mismatch");
  if(r!=oldRun){band=std::make_unique<BANDReco>();if(simulation)band->setMC();band->setRunno(r);band->setPeriod(period);
   band->readTW();band->readLROffset();band->readPaddleOffset();band->readLayerOffset();band->readGeometry();band->readEnergyCalib();band->readStatus();band->readGlobalOffset();oldRun=r;}
  band->Clear();band->createPMTs(&adc,&tdc,&run);band->createBars();
  auto pmts=band->getCandidatePMTs();auto bars=band->getCandidateBars();
  if(bars.size()>maxNeutrons)throw std::runtime_error("Too many bars for reference hit buffer");
  bandhit hits[maxNeutrons];int mult=0,lead=-1,survivors=0;
  band->storeHits(mult,hits,st,band->getRGBVertexOffset());bool good=goodNeutronEvent(hits,mult,lead,1,survivors);
  out<<"{\"ordinal\":"<<ordinal-1<<",\"run\":"<<r<<",\"event\":"<<e;kv(out,"start_time",st);kv(out,"vertex_z",band->getRGBVertexOffset());
  out<<",\"adc_rows\":"<<adc.getRows()<<",\"tdc_rows\":"<<tdc.getRows()<<",\"pmts\":[";bool first=true;
  for(auto &[id,p]:pmts){if(!first)out<<',';first=false;out<<"{\"id\":"<<id<<",\"adc_row\":"<<p.idx_ftdc<<",\"tdc_row\":"<<p.idx_tdc;
   kv(out,"adc",p.adc);kv(out,"amplitude",p.amp);kv(out,"time_phase",p.tdc);kv(out,"time_tw",p.tdc_corr);kv(out,"time_fadc",p.ftdc);out<<'}';}
  out<<"],\"bars\":[";first=true;
  for(auto &[id,b]:bars){if(!first)out<<',';first=false;out<<"{\"id\":"<<id<<",\"layer\":"<<b.layer<<",\"left_id\":"<<b.left.PMT_ID<<",\"right_id\":"<<b.right.PMT_ID;
   kv(out,"time",b.Tof);kv(out,"time_fadc",b.TofFtdc);kv(out,"diff",b.Tdiff);kv(out,"diff_fadc",b.TdiffFtdc);kv(out,"x",b.X);kv(out,"y",b.Y);kv(out,"z",b.Z);kv(out,"energy",b.Edep);
   out<<",\"eligible\":"<<((b.Edep >= (b.layer==6?(2./7.2)*2:2))?"true":"false")<<'}';}
  out<<"],\"hits\":[";for(int i=0;i<mult;i++){if(i)out<<',';out<<"{\"id\":"<<hits[i].getBarID();kv(out,"tof",hits[i].getTof());kv(out,"z_relative",hits[i].getZ());out<<'}';}
  out<<"],\"candidate\":{\"accepted\":"<<(good?"true":"false")<<",\"survivors\":"<<survivors<<",\"lead_id\":"<<(lead>=0?hits[lead].getBarID():-1)<<"}}\n";
  ++written;if(written%1000==0){out.flush();std::cout<<"PROGRESS cpp events="<<written<<" ordinal="<<ordinal-1<<std::endl;}
 }
 out.close();if(written==0)throw std::runtime_error("No events in requested range");std::filesystem::rename(partial,argv[2]);
 std::cout<<"COMPLETE cpp events="<<written<<" output="<<argv[2]<<std::endl;return 0;
}catch(const std::exception&e){std::cerr<<"ERROR "<<e.what()<<std::endl;return 1;}}
