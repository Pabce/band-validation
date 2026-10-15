#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>
#include "QADB.h"
int main(int argc,char**argv){try{
 if(argc!=4)throw std::runtime_error("cook input.jsonl output.tsv");
 QA::QADB qa(argv[1]);std::ifstream in(argv[2]);std::ofstream out(argv[3]);std::string line;long n=0;
 while(std::getline(in,line)){
  rapidjson::Document d;d.Parse(line.c_str());if(d.HasParseError())throw std::runtime_error("JSON error");if(d.HasMember("input_status"))continue;
  int run=d["run"].GetInt(),event=d["event"].GetInt();bool golden=qa.Golden(run,event);
  if(golden!=d["electron"]["golden"].GetBool())throw std::runtime_error("Golden replay mismatch");
  out<<d["ordinal"].GetInt()<<'\t'<<qa.GetBinnum()<<'\t'<<qa.GetDefect()<<'\t'<<golden<<'\n';
  if(++n%10000==0)std::cout<<"PROGRESS native QA records="<<n<<std::endl;
 }
 std::cout<<"COMPLETE native QA records="<<n<<std::endl;
}catch(const std::exception&e){std::cerr<<e.what()<<std::endl;return 1;}}
