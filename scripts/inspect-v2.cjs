const fs = require('node:fs');
const crypto = require('node:crypto');
const b = fs.readFileSync(process.argv[2]);
const eocd = b.lastIndexOf(Buffer.from('504b0506','hex'));
const cd = b.readUInt32LE(eocd+16);
const size = Number(b.readBigUInt64LE(cd-24));
const start = cd-size-8;
const lp = (buf, at=0) => { const n=buf.readUInt32LE(at); return [buf.subarray(at+4,at+4+n),at+4+n]; };
const list = buf => { const a=[]; for(let i=0;i<buf.length;){const [v,n]=lp(buf,i);a.push(v);i=n;} return a; };
const out={sha256:crypto.createHash('sha256').update(b).digest('hex'),bytes:b.length,signingBlockStart:start,blocks:[]};
for(let p=start+8;p<cd-24;){
 const n=Number(b.readBigUInt64LE(p));p+=8;const id=b.readUInt32LE(p);const value=b.subarray(p+4,p+n);p+=n;
 out.blocks.push(id.toString(16));
 if(id!==0x7109871a)continue;
 const [signers]=lp(value);
 out.v2=list(signers).map(s=>{
   const [signedData,a]=lp(s);const [sigs,c]=lp(s,a);const [pub]=lp(s,c);
   const [digests,d]=lp(signedData);const [certs]=lp(signedData,d);
   const cert=new crypto.X509Certificate(list(certs)[0]);
   const signatures=list(sigs).map(sig=>{const alg=sig.readUInt32LE(0);const [data]=lp(sig,4);return {algorithm:alg.toString(16),valid:alg===0x0103?crypto.verify('sha256',signedData,crypto.createPublicKey({key:pub,format:'der',type:'spki'}),data):null};});
   const records=list(digests).map(x=>{const alg=x.readUInt32LE(0);const [expected]=lp(x,4); if(alg!==0x0103)return {algorithm:alg.toString(16),contentMatches:null};
     const end=Buffer.from(b.subarray(eocd));end.writeUInt32LE(start,16);
     const chunks=[];
     for(const section of [b.subarray(0,start),b.subarray(cd,eocd),end])for(let i=0;i<section.length;i+=1048576){const chunk=section.subarray(i,i+1048576),prefix=Buffer.alloc(5);prefix[0]=0xa5;prefix.writeUInt32LE(chunk.length,1);chunks.push(crypto.createHash('sha256').update(prefix).update(chunk).digest());}
     const prefix=Buffer.alloc(5);prefix[0]=0x5a;prefix.writeUInt32LE(chunks.length,1);
     const actual=crypto.createHash('sha256').update(prefix).update(Buffer.concat(chunks)).digest();
     return {algorithm:alg.toString(16),contentMatches:actual.equals(expected)};
   });
   return {subject:cert.subject,issuer:cert.issuer,validFrom:cert.validFrom,validTo:cert.validTo,fingerprint256:cert.fingerprint256,publicKeyMatches:cert.publicKey.export({format:'der',type:'spki'}).equals(pub),signatures,digests:records};
 });
}
console.log(JSON.stringify(out,null,2));
