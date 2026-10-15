import copy, unittest
from compare import compare,f32
class ComparisonContract(unittest.TestCase):
 def setUp(self):
  self.c={'ordinal':0,'run':6363,'event':1,'start_time':10.,'adc_rows':2,'tdc_rows':2,'pmts':[{'id':2110,'adc_row':0,'tdc_row':0,'adc':500,'amplitude':300,'time_phase':0.1,'time_fadc':30.}],'bars':[],'candidate':{'accepted':False,'survivors':0,'lead_id':-1}}
  self.j=copy.deepcopy(self.c);self.j['candidate']=None;self.j['pmts'][0]['time_phase']=f32(0.1)
 def test_storage_precision(self):self.assertEqual(compare(self.c,self.j)[0],[])
 def test_identity_guard(self):
  self.j['event']=2
  with self.assertRaises(ValueError):compare(self.c,self.j)
 def test_missing_pmt(self):
  self.j['pmts']=[];self.assertEqual(compare(self.c,self.j)[0][0],'pmt_selection')
 def test_source_row_difference(self):
  self.j['pmts'][0]['adc_row']=1;self.assertEqual(compare(self.c,self.j)[0][0],'pmt_selection')
 def test_timing_difference(self):
  self.j['pmts'][0]['time_phase']=0.2;self.assertEqual(compare(self.c,self.j)[0][0],'pmt_values')
 def test_duplicate_identity(self):
  self.j['pmts']*=2
  with self.assertRaises(ValueError):compare(self.c,self.j)
if __name__=='__main__':unittest.main()
