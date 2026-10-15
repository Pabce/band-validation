#include <iostream>
#include <stdexcept>
#include "readhipo_helper.h"
static bandhit hit(int sector,int layer,int component,double x,double y,double t){bandhit h;h.setSector(sector);h.setLayer(layer);h.setComponent(component);h.setBarID(sector*100+layer*10+component);h.setTof(t);h.setDL(TVector3(x,y,0));return h;}
static void check(const char* name,std::initializer_list<bandhit> values,bool expected,int expectedLead,int expectedPass){bandhit h[maxNeutrons];int n=0;for(auto v:values)h[n++]=v;int lead=-9,pass=-9;bool result=goodNeutronEvent(h,n,lead,1,pass);if(result!=expected||lead!=expectedLead||pass!=expectedPass)throw std::runtime_error(name);std::cout<<"PASS "<<name<<'\n';}
int main(){try{
 check("empty",{},false,-1,0);
 check("one",{hit(2,3,1,0,0,10)},true,0,1);
 check("veto only",{hit(2,6,1,0,0,10)},false,-1,0);
 check("ordinary blocking inclusive bounds",{hit(2,3,1,0,0,10),hit(2,4,1,15,8,13)},true,1,1);
 check("veto ignores x inclusive time",{hit(2,5,1,0,0,10),hit(2,6,1,999,8,25)},false,-1,0);
 check("veto outside time",{hit(2,5,1,0,0,10),hit(2,6,1,999,8,25.01)},true,0,1);
 check("missing layer sector5",{hit(5,4,1,0,0,10),hit(5,6,1,0,0,10)},false,-1,0);
 check("missing layer sector3",{hit(3,4,6,0,0,10),hit(3,6,6,0,0,10)},false,-1,0);
 check("missing layer sector4",{hit(4,4,6,0,0,10),hit(4,6,6,0,0,10)},false,-1,0);
 check("two same layer",{hit(2,3,1,0,0,10),hit(2,3,2,15,8,12)},true,0,2);
 check("equal time selects second",{hit(2,3,1,0,0,10),hit(2,3,2,15,8,10)},true,1,2);
 check("nearby different layers rejected",{hit(2,1,1,0,0,10),hit(2,3,1,0,0,10)},false,-1,2);
 check("three survivors rejected",{hit(2,1,1,0,0,10),hit(2,3,1,0,0,10),hit(2,5,1,0,0,10)},false,-1,3);
 std::cout<<"COMPLETE 13 selector contract cases\n";return 0;
}catch(const std::exception&e){std::cerr<<"FAIL "<<e.what()<<'\n';return 1;}}
