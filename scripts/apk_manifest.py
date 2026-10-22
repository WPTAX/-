"""Small dependency-free binary Android manifest reader for build assertions.

This parses metadata only; apksigner is still required to verify signatures.
"""
import struct
import sys
import json
import zipfile

def manifest(path):
    with zipfile.ZipFile(path) as z:
        if z.testzip():
            raise ValueError('Corrupt ZIP entry')
        data=z.read('AndroidManifest.xml')
        native=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
    strings=[]
    tags=[]
    pos=8
    def length8(p):
        n=data[p]
        return (((n&127)<<8)|data[p+1],p+2) if n&128 else (n,p+1)
    def length16(p):
        n=struct.unpack_from('<H',data,p)[0]
        return (((n&32767)<<16)|struct.unpack_from('<H',data,p+2)[0],p+4) if n&32768 else (n,p+2)
    def text(i): return strings[i] if i != 0xffffffff else None
    while pos<len(data):
        kind,header,size=struct.unpack_from('<HHI',data,pos)
        if size<8: raise ValueError('Invalid chunk')
        if kind==1:
            count,_,flags,start,_=struct.unpack_from('<IIIII',data,pos+8)
            for i in range(count):
                offset=struct.unpack_from('<I',data,pos+header+i*4)[0]
                p=pos+start+offset
                if flags&256:
                    _,p=length8(p);n,p=length8(p);strings.append(data[p:p+n].decode('utf-8'))
                else:
                    n,p=length16(p);strings.append(data[p:p+n*2].decode('utf-16le'))
        if kind==0x102:
            _,name,attr_start,attr_size,count=struct.unpack_from('<IIHHH',data,pos+16)
            attrs={}
            for i in range(count):
                a=pos+16+attr_start+i*attr_size
                _,key,raw,_,_,typ,value=struct.unpack_from('<IIIHBBI',data,a)
                attrs[text(key)]=text(raw) if raw!=0xffffffff else text(value) if typ==3 else bool(value) if typ==18 else value
            tags.append({'tag':text(name),'attributes':attrs})
        pos+=size
    return {'tags':tags,'nativeLibraries':native}

if __name__=='__main__':
    result=manifest(sys.argv[1])
    if '--assert-release' in sys.argv:
        tags=result['tags']
        app=next(t['attributes'] for t in tags if t['tag']=='application')
        info=next(t['attributes'] for t in tags if t['tag']=='manifest')
        sdk=next(t['attributes'] for t in tags if t['tag']=='uses-sdk')
        assert not app.get('testOnly',False),'testOnly APK cannot be installed normally'
        assert not app.get('debuggable',False),'Expected release APK'
        assert info['package']=='com.ironproject.woonghee.v42'
        assert info['versionCode']==44
        assert sdk['minSdkVersion']==28 and sdk['targetSdkVersion']==35
        activities=[t['attributes'].get('name') for t in tags if t['tag']=='activity']
        assert 'com.ironproject.woonghee.MainActivity' in activities
        assert not result['nativeLibraries'],'Expected universal, no-ABI-restriction APK'
    print(json.dumps(result,ensure_ascii=False,indent=2))
