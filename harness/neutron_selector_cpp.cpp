// Exercise the unchanged goodNeutronEvent, including its pairwise blocking
// decisions. The energy prefilter mirrors storeHits; real HIPO comparisons
// separately exercise storeHits itself through the frozen reference driver.
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <numeric>
#include <stdexcept>
#include <vector>
#include "readhipo_helper.h"
struct Input {int id,row;double e,t,x,y;};
static bandhit convert(const Input& in,double start) {
    bandhit h;h.setBarID(in.id);h.setSector(in.id/100);h.setLayer(in.id/10%10);
    h.setComponent(in.id%10);h.setEdep(in.e);h.setTof(in.t-start);h.setDL(TVector3(in.x,in.y,0));return h;
}
int main(int argc,char**argv){try{
    if(argc!=3 || std::filesystem::exists(argv[2]))throw std::runtime_error("usage: cases output (new file)");
    std::ifstream in(argv[1]);std::ofstream out(std::string(argv[2])+".partial");
    std::string name;double start;int n,count=0;
    while(in>>name>>start>>n){
        if(n>maxNeutrons)throw std::runtime_error("multiplicity");
        std::vector<Input> rows(n),eligible;
        for(int i=0;i<n;i++) {auto &h=rows[i];h.row=i;in>>h.id>>h.e>>h.t>>h.x>>h.y;
            if(h.e >= (h.id/10%10==6 ? (2./7.2)*2:2.))eligible.push_back(h);}
        if(!in)throw std::runtime_error("bad input");
        std::sort(eligible.begin(),eligible.end(),[](auto a,auto b){return a.id<b.id;});
        bandhit hits[maxNeutrons];for(size_t i=0;i<eligible.size();i++)hits[i]=convert(eligible[i],start);
        int lead=-1,pass=-1;bool accepted=goodNeutronEvent(hits,eligible.size(),lead,1,pass);
        std::vector<int> blocker(n,-1),survivor(n,0),isEligible(n,0);
        for(size_t i=0;i<eligible.size();i++){
            int row=eligible[i].row;isEligible[row]=1;
            if(hits[i].getLayer()==6)continue;
            for(size_t j=0;j<eligible.size();j++){
                if(i==j)continue;
                bandhit pair[maxNeutrons];pair[0]=hits[i];pair[1]=hits[j];int lp,np;
                goodNeutronEvent(pair,2,lp,1,np);
                if((hits[j].getLayer()==6 && np==0) || (np==1 && lp==1)) {blocker[row]=eligible[j].row;break;}
            }
            survivor[row]=blocker[row]<0;
        }
        out<<"{\"name\":\""<<name<<"\",\"accepted\":"<<(accepted?"true":"false")<<",\"survivors\":"<<pass
           <<",\"lead_id\":"<<(lead<0?-1:eligible[lead].id)<<",\"lead_row\":"<<(lead<0?-1:eligible[lead].row)<<",\"hits\":[";
        for(int i=0;i<n;i++){if(i)out<<',';out<<"["<<rows[i].id<<','<<isEligible[i]<<','<<survivor[i]<<','<<blocker[i]<<"]";}
        out<<"]}\n";
        if(++count%1000==0){out.flush();std::cout<<"PROGRESS C++ selector cases="<<count<<std::endl;}
    }
    if(!count)throw std::runtime_error("empty cases");out.close();std::filesystem::rename(std::string(argv[2])+".partial",argv[2]);
    std::cout<<"COMPLETE C++ selector cases="<<count<<std::endl;return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<std::endl;return 1;}}
