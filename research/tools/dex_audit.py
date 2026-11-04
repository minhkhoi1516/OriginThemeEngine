"""Offline DEX instruction/index reader for static AOD research (not a decompiler)."""
import struct, json, re, sys
from pathlib import Path

OPS={}
def op(code,name,fmt): OPS[code]=(name,fmt)
for c,n,f in [(0,'nop','10x'),(1,'move','12x'),(2,'move/from16','22x'),(3,'move/16','32x'),(4,'move-wide','12x'),(5,'move-wide/from16','22x'),(6,'move-wide/16','32x'),(7,'move-object','12x'),(8,'move-object/from16','22x'),(9,'move-object/16','32x'),(10,'move-result','11x'),(11,'move-result-wide','11x'),(12,'move-result-object','11x'),(13,'move-exception','11x'),(14,'return-void','10x'),(15,'return','11x'),(16,'return-wide','11x'),(17,'return-object','11x'),(18,'const/4','11n'),(19,'const/16','21s'),(20,'const','31i'),(21,'const/high16','21h'),(22,'const-wide/16','21s'),(23,'const-wide/32','31i'),(24,'const-wide','51l'),(25,'const-wide/high16','21h'),(26,'const-string','21c'),(27,'const-string/jumbo','31c'),(28,'const-class','21c'),(29,'monitor-enter','11x'),(30,'monitor-exit','11x'),(31,'check-cast','21c'),(32,'instance-of','22c'),(33,'array-length','12x'),(34,'new-instance','21c'),(35,'new-array','22c'),(36,'filled-new-array','35c'),(37,'filled-new-array/range','3rc'),(38,'fill-array-data','31t'),(39,'throw','11x'),(40,'goto','10t'),(41,'goto/16','20t'),(42,'goto/32','30t'),(43,'packed-switch','31t'),(44,'sparse-switch','31t')]:op(c,n,f)
for i,n in enumerate(['cmpl-float','cmpg-float','cmpl-double','cmpg-double','cmp-long']):op(0x2d+i,n,'23x')
for i,n in enumerate(['eq','ne','lt','ge','gt','le']):op(0x32+i,'if-'+n,'22t');op(0x38+i,'if-'+n+'z','21t')
for base,prefix in [(0x44,'aget'),(0x4b,'aput'),(0x52,'iget'),(0x59,'iput'),(0x60,'sget'),(0x67,'sput')]:
 for i,s in enumerate(['','-wide','-object','-boolean','-byte','-char','-short']):op(base+i,prefix+s,'23x' if base<0x52 else '22c' if base<0x60 else '21c')
for base,f in [(0x6e,'35c'),(0x74,'3rc')]:
 for i,n in enumerate(['virtual','super','direct','static','interface']):op(base+i,'invoke-'+n+('/range' if base==0x74 else ''),f)
for i,n in enumerate(['neg-int','not-int','neg-long','not-long','neg-float','neg-double','int-to-long','int-to-float','int-to-double','long-to-int','long-to-float','long-to-double','float-to-int','float-to-long','float-to-double','double-to-int','double-to-long','double-to-float','int-to-byte','int-to-char','int-to-short']):op(0x7b+i,n,'12x')
arith=[n+'-'+t for t,ns in [('int',['add','sub','mul','div','rem','and','or','xor','shl','shr','ushr']),('long',['add','sub','mul','div','rem','and','or','xor','shl','shr','ushr']),('float',['add','sub','mul','div','rem']),('double',['add','sub','mul','div','rem'])] for n in ns]
for i,n in enumerate(arith):op(0x90+i,n,'23x');op(0xb0+i,n+'/2addr','12x')
for i,n in enumerate(['add','rsub','mul','div','rem','and','or','xor']):op(0xd0+i,n+'-int/lit16','22s')
for i,n in enumerate(['add','rsub','mul','div','rem','and','or','xor','shl','shr','ushr']):op(0xd8+i,n+'-int/lit8','22b')
for c,n,f in [(0xfa,'invoke-polymorphic','45cc'),(0xfb,'invoke-polymorphic/range','4rcc'),(0xfc,'invoke-custom','35c'),(0xfd,'invoke-custom/range','3rc'),(0xfe,'const-method-handle','21c'),(0xff,'const-method-type','21c')]:op(c,n,f)
WIDTH={k:int(k[0]) for k in ['10x','12x','22x','32x','11x','11n','21s','31i','21h','51l','21c','31c','22c','35c','3rc','31t','10t','20t','30t','23x','22t','21t','22s','22b','45cc','4rcc']}
def signed(n,b):return n-(1<<b) if n&(1<<(b-1)) else n

class Dex:
 def u2(self,o):return struct.unpack_from('<H',self.b,o)[0]
 def u4(self,o):return struct.unpack_from('<I',self.b,o)[0]
 def uleb(self,o):
  x=s=0
  while True:
   v=self.b[o];o+=1;x|=(v&127)<<s;s+=7
   if v<128:return x,o
 def __init__(self,path):
  self.path=Path(path);self.b=self.path.read_bytes();assert self.b[:4]==b'dex\n';self.strings=[]
  n,o=struct.unpack_from('<II',self.b,56)
  for i in range(n):
   _,p=self.uleb(self.u4(o+i*4));self.strings.append(self.b[p:self.b.index(b'\0',p)].replace(b'\xc0\x80',b'\0').decode('utf8','replace'))
  n,o=struct.unpack_from('<II',self.b,64);self.types=[self.strings[self.u4(o+i*4)] for i in range(n)]
  n,o=struct.unpack_from('<II',self.b,72);self.protos=[]
  for i in range(n):
   _,r,p=struct.unpack_from('<III',self.b,o+i*12);params=[] if not p else [self.types[self.u2(p+4+j*2)] for j in range(self.u4(p))];self.protos.append('('+''.join(params)+')'+self.types[r])
  n,o=struct.unpack_from('<II',self.b,80);self.fields=[]
  for i in range(n):
   c,t,s=struct.unpack_from('<HHI',self.b,o+i*8);self.fields.append(self.types[c]+'->'+self.strings[s]+':'+self.types[t])
  n,o=struct.unpack_from('<II',self.b,88);self.methods=[]
  for i in range(n):
   c,p,s=struct.unpack_from('<HHI',self.b,o+i*8);self.methods.append(self.types[c]+'->'+self.strings[s]+self.protos[p])
  n,o=struct.unpack_from('<II',self.b,96);self.defs={};self.classes={}
  for i in range(n):
   c,flags,sup,inter,source,anno,cd,sv=struct.unpack_from('<8I',self.b,o+i*32);name=self.types[c];self.classes[name]=dict(flags=flags,superclass=self.types[sup] if sup!=0xffffffff else None,fields=[],methods=[],static_values_offset=sv)
   if not cd:continue
   ns,cd=self.uleb(cd);ni,cd=self.uleb(cd);nd,cd=self.uleb(cd);nv,cd=self.uleb(cd)
   for count,kind in [(ns,'static'),(ni,'instance')]:
    idx=0
    for j in range(count):
     diff,cd=self.uleb(cd);fl,cd=self.uleb(cd);idx+=diff;self.classes[name]['fields'].append(dict(name=self.fields[idx],flags=fl,kind=kind))
   for count in [nd,nv]:
    idx=0
    for j in range(count):
     diff,cd=self.uleb(cd);fl,cd=self.uleb(cd);co,cd=self.uleb(cd);idx+=diff;self.defs[idx]=dict(flags=fl,offset=co);self.classes[name]['methods'].append(idx)
 def insns(self,idx):
  co=self.defs[idx]['offset']
  if not co:return
  count=self.u4(co+12);u=struct.unpack_from('<'+'H'*count,self.b,co+16);p=0
  while p<count:
   w=u[p];code=w&255
   if code==0 and w:
    if w==0x100:sz=4+u[p+1]*2
    elif w==0x200:sz=2+u[p+1]*4
    elif w==0x300:sz=4+(u[p+1]*(u[p+2]|u[p+3]<<16)+1)//2
    else:raise ValueError(('unknown payload',idx,p,w))
    yield dict(pc=p,name='payload',fmt='payload',units=u[p:p+sz],ref=None,text=' '.join(f'{v:04x}' for v in u[p:p+sz]));p+=sz;continue
   if code not in OPS:raise ValueError(('unknown opcode',idx,p,hex(code)))
   name,f=OPS[code];size=WIDTH[f];v=u[p:p+size];a=w>>8;b=a>>4;a4=a&15;ref=None;text=''
   if f=='12x':text=f'v{a4}, v{b}'
   elif f=='11x':text=f'v{a}'
   elif f=='11n':text=f'v{a4}, {signed(b,4)}'
   elif f=='22x':text=f'v{a}, v{v[1]}'
   elif f=='32x':text=f'v{v[1]}, v{v[2]}'
   elif f in ['21s','21h','31i','51l']:
    val=sum(x<<(16*j) for j,x in enumerate(v[1:]));bits=16*(size-1)
    if f=='21h':val<<=48 if code==25 else 16;bits=64 if code==25 else 32
    text=f'v{a}, {signed(val,bits)} (0x{val:0{bits//4}x})'
    if bits==32:text+=' float='+str(struct.unpack('<f',struct.pack('<I',val))[0])
   elif f=='23x':text=f'v{a}, v{v[1]&255}, v{v[1]>>8}'
   elif f=='22s':text=f'v{a4}, v{b}, {signed(v[1],16)}'
   elif f=='22b':text=f'v{a}, v{v[1]&255}, {signed(v[1]>>8,8)}'
   elif f in ['10t','20t','30t','21t','22t','31t']:
    delta=signed(a,8) if f=='10t' else signed(v[1]|(v[2]<<16),32) if size==3 else signed(v[1],16)
    text=('' if f in ['10t','20t','30t'] else f'v{a4}, v{b}, ' if f=='22t' else f'v{a}, ')+f'@{p+delta:04x}'
   elif f in ['21c','31c','22c','35c','3rc','45cc','4rcc']:
    ix=v[1]|(v[2]<<16) if f=='31c' else v[1]
    if code in [26,27]:ref=('string',ix);desc=json.dumps(self.strings[ix],ensure_ascii=False)
    elif code in [28,31,32,34,35,36,37]:ref=('type',ix);desc=self.types[ix]
    elif 0x52<=code<=0x6d:ref=('field',ix);desc=self.fields[ix]
    elif 0x6e<=code<=0x78 or code in [0xfa,0xfb]:ref=('method',ix);desc=self.methods[ix]
    else:ref=('other',ix);desc=str(ix)
    if f in ['35c','45cc']:
     regs=[v[2]&15,(v[2]>>4)&15,(v[2]>>8)&15,v[2]>>12,a4][:b];text='{'+', '.join('v'+str(r) for r in regs)+'}, '+desc
    elif f in ['3rc','4rcc']:text='{'+', '.join('v'+str(r) for r in range(v[2],v[2]+a))+'}, '+desc
    else:text=(f'v{a4}, v{b}, ' if f=='22c' else f'v{a}, ')+desc
   yield dict(pc=p,name=name,fmt=f,units=v,ref=ref,text=text);p+=size
  assert p==count,(idx,p,count)
 def dump(self,idx):
  co=self.defs[idx]['offset'];s='METHOD '+self.methods[idx]+f' flags=0x{self.defs[idx]["flags"]:x} code_off=0x{co:x}\n'
  if co:
   regs,ins,outs,tries=struct.unpack_from('<4H',self.b,co);s+=f'registers={regs} inputs={ins} (parameters start v{regs-ins}) outs={outs} tries={tries}\n'
   for i in self.insns(idx):s+=f'{i["pc"]:04x}: {i["name"]:25s} {i["text"]}\n'
  return s
 def index(self):
  out=[]
  for idx,meta in self.defs.items():
   refs=[]
   for x in self.insns(idx):
    if x['ref']:
     kind,k=x['ref'];val={'string':self.strings,'type':self.types,'method':self.methods,'field':self.fields}.get(kind)
     if val is not None:refs.append(dict(pc=x['pc'],kind=kind,value=val[k]))
   out.append(dict(id=idx,name=self.methods[idx],**meta,refs=refs))
  return out

if __name__=='__main__':
 d=Dex(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(exist_ok=True,parents=True)
 (out/'index.json').write_text(json.dumps(d.index(),ensure_ascii=False))
 (out/'classes.json').write_text(json.dumps(d.classes,ensure_ascii=False,indent=2))
 print('Indexed',len(d.defs),'methods',len(d.classes),'classes')
