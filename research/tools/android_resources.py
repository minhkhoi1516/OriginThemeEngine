"""Minimal offline Android resource-table and binary-XML reader for evidence export."""
import struct,json,zipfile,sys
from pathlib import Path
from xml.sax.saxutils import quoteattr
def u16(b,o):return struct.unpack_from('<H',b,o)[0]
def u32(b,o):return struct.unpack_from('<I',b,o)[0]
def pool(b,o):
 hs=u16(b,o+2);n=u32(b,o+8);flags=u32(b,o+16);start=o+u32(b,o+20);out=[]
 def len8(p):
  v=b[p];return (((v&127)<<8)|b[p+1],p+2) if v&128 else (v,p+1)
 def len16(p):
  v=u16(b,p);return (((v&32767)<<16)|u16(b,p+2),p+4) if v&32768 else (v,p+2)
 for i in range(n):
  p=start+u32(b,o+hs+i*4)
  if flags&256:
   _,p=len8(p);nbytes,p=len8(p);s=b[p:p+nbytes].decode('utf8','replace')
  else:
   nchar,p=len16(p);s=b[p:p+nchar*2].decode('utf-16le','replace')
  out.append(s)
 return out
def complex_value(v):
 mantissa=v&0xffffff00
 if mantissa&0x80000000:mantissa-=0x100000000
 return mantissa*[1/256,1/32768,1/8388608,1/2147483648][(v>>4)&3]
def val(t,v,strings,names=None):
 if t==3:return strings[v]
 if t==1:return '@'+(names or {}).get(v,f'0x{v:08x}')
 if t==2:return '?'+(names or {}).get(v,f'0x{v:08x}')
 if t==4:return str(struct.unpack('<f',struct.pack('<I',v))[0])
 if t==5:return str(complex_value(v))+['px','dp','sp','pt','in','mm','?','?'][v&7]
 if t==6:return str(complex_value(v)*100)+('%p' if v&15 else '%')
 if t==18:return 'true' if v else 'false'
 if t==16:return str(v-0x100000000 if v&0x80000000 else v)
 if t==17:return f'0x{v:x}'
 if 28<=t<=31:return f'#{v:08x}'
 return f'type{t}:0x{v:08x}'
def arsc(b):
 out=[];names={};globalstrings=[];o=u16(b,2)
 while o<len(b):
  typ,hs,size=struct.unpack_from('<HHI',b,o)
  if typ==1:globalstrings=pool(b,o)
  if typ==0x200:
   pkgid=u32(b,o+8);types=pool(b,o+u32(b,o+268));keys=pool(b,o+u32(b,o+276));p=o+hs
   while p<o+size:
    ct,ch,cs=struct.unpack_from('<HHI',b,p)
    if ct==0x201:
     tid=b[p+8];fl=b[p+9];count=u32(b,p+12);start=u32(b,p+16);cfgsize=u32(b,p+20);cfg=b[p+20:p+20+cfgsize]
     for j in range(count):
      if fl&1:idx=u16(b,p+ch+j*4);off=u16(b,p+ch+j*4+2)*4
      elif fl&2:idx=j;off=u16(b,p+ch+j*2);off=0xffffffff if off==0xffff else off*4
      else:idx=j;off=u32(b,p+ch+j*4)
      if off==0xffffffff:continue
      e=p+start+off;es=u16(b,e);ef=u16(b,e+2);key=u32(b,e+4);rid=(pkgid<<24)|(tid<<16)|idx
      if ef&8:raise ValueError('Compact entry unsupported')
      name=types[tid-1]+'/'+keys[key];names[rid]=name
      if ef&1:continue
      t=b[e+es+3];v=u32(b,e+es+4)
      out.append(dict(id=rid,name=name,type=t,data=v,config_hex=cfg.hex(),density=u16(cfg,14) if len(cfg)>=16 else 0,value=val(t,v,globalstrings)))
    p+=cs
  o+=size
 for x in out:x['value']=val(x['type'],x['data'],globalstrings,names)
 return out,names
def axml(b,names):
 if b[:1]==b'<':return b.decode('utf8')
 o=u16(b,2);strings=[];lines=[];depth=0;namespaces={}
 def s(i):return '' if i==0xffffffff else strings[i]
 while o<len(b):
  t,h,size=struct.unpack_from('<HHI',b,o)
  if t==1:strings=pool(b,o)
  elif t==0x100:namespaces[s(u32(b,o+20))]=s(u32(b,o+16))
  elif t==0x102:
   name=s(u32(b,o+20));ast=u16(b,o+24);asz=u16(b,o+26);n=u16(b,o+28);attrs=[]
   if depth==0:attrs.extend('xmlns:'+k+'='+quoteattr(v) for v,k in namespaces.items() if k)
   for j in range(n):
    p=o+16+ast+j*asz;ns,key,raw=struct.unpack_from('<III',b,p);vt=b[p+15];vd=u32(b,p+16);prefix=namespaces.get(s(ns),'');an=(prefix+':' if prefix else '')+s(key);av=s(raw) if raw!=0xffffffff else val(vt,vd,strings,names);attrs.append(an+'='+quoteattr(av))
   lines.append('  '*depth+'<'+name+(' '+' '.join(attrs) if attrs else '')+'>');depth+=1
  elif t==0x103:depth-=1;lines.append('  '*depth+'</'+s(u32(b,o+20))+'>')
  elif t==0x104:lines.append('  '*depth+s(u32(b,o+16)))
  o+=size
 return '\n'.join(lines)+'\n'
if __name__=='__main__':
 z=zipfile.ZipFile(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True);values,names=arsc(z.read('resources.arsc'))
 (out/'resources.json').write_text(json.dumps(values,ensure_ascii=False,indent=2));(out/'names.json').write_text(json.dumps(names,ensure_ascii=False,indent=2))
 for name in z.namelist():
  if name=='AndroidManifest.xml' or name.startswith(('res/layout','res/xml','assets/')) and name.endswith('.xml'):
   p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(axml(z.read(name),names))
 for item in values:
  if item['name'].startswith(('layout/','xml/')) and item['type']==3 and item['value'] in z.namelist():
   suffix='' if not any(bytes.fromhex(item['config_hex'])[4:]) else '__'+item['config_hex']
   p=out/'decoded'/(item['name']+suffix+'.xml');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(axml(z.read(item['value']),names))
 print('Resources',len(values),'XML decoded')
