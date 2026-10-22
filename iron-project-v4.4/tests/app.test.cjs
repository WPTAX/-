const test=require('node:test'), assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const assets=path.join(__dirname,'../app/src/main/assets');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
function boot(initial={}){
 const storage=new Map(Object.entries(initial));
 const elements=Object.fromEntries([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>[m[1],{id:m[1],value:'',textContent:'',innerHTML:'',className:'',style:{setProperty(){}},setAttribute(){},classList:{add(){},remove(){},toggle(){}},querySelectorAll(){return []}}]));
 const errors=[];
 const context=vm.createContext({console,Date,Math,JSON,Number,String,Object,Array,Map,Error,Infinity,globalThis:null,localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,String(v)),removeItem:k=>storage.delete(k)},document:{getElementById:id=>{assert.ok(elements[id],`Missing HTML element ${id}`);return elements[id]},querySelectorAll:()=>[],querySelector:()=>null},setInterval(){},alert:s=>errors.push(s),confirm:()=>true,scrollTo(){}});
 context.window=context;context.globalThis=context;
 vm.runInContext(fs.readFileSync(path.join(assets,'model.js'),'utf8'),context);
 vm.runInContext(fs.readFileSync(path.join(assets,'app.js'),'utf8'),context);
 return {context,elements,storage,errors,run:code=>vm.runInContext(code,context)};
}
test('UI starts with 160-minute target and all required elements',()=>{const b=boot();assert.equal(b.elements.homeGoal.textContent,'160분');assert.equal(b.elements.homeRemain.textContent,'160:00');assert.equal(b.errors.length,0);});
test('Manual running saves, counts toward goal and survives reopening',()=>{const b=boot();b.run("openRunModal();$('runMinutes').value='38.1';$('runDistance').value='5.01';$('runHr').value='146';saveManualRun()");assert.equal(b.elements.homeDone.textContent,'38:06');assert.equal(b.elements.homeRemain.textContent,'121:54');const reopened=boot(Object.fromEntries(b.storage));assert.equal(reopened.elements.homeDone.textContent,'38:06');});
test('Rendering escapes memo HTML',()=>{const b=boot();b.run("openRunModal();$('runMinutes').value='40';$('runMemo').value='<img src=x onerror=alert(1)>';saveManualRun()");assert.ok(b.elements.recordList.innerHTML.includes('&lt;img'));assert.ok(!b.elements.recordList.innerHTML.includes('<img'));});
test('Negative distance and infinite duration cannot be saved',()=>{const b=boot();b.run("openRunModal();$('runMinutes').value='40';$('runDistance').value='-3';saveManualRun()");assert.equal(b.storage.has('iron_manual_runs_v4'),false);assert.equal(b.errors.length,1);});
test('Goal updates survive reopening and max is enforced',()=>{const b=boot();b.run('setGoal(200);setGoal(1001)');assert.equal(b.elements.homeGoal.textContent,'200분');assert.equal(boot(Object.fromEntries(b.storage)).elements.homeGoal.textContent,'200분');});
test('Manual sleep override saves and can be removed',()=>{const b=boot();b.run("openSleepModal();$('sleepHours').value='7.5';saveManualSleep()");assert.equal(b.elements.todaySleep.textContent,'7.5시간');b.run('deleteSleep(today())');assert.equal(b.elements.todaySleep.textContent,'-');});
test('Malformed sync response retains cached automatic records',()=>{const b=boot();b.run("window.receiveHealthData('not-json',1,2)");assert.ok(b.elements.healthStatus.textContent.includes('기존 기록'));assert.equal(b.storage.has('iron_auto_runs_v4'),false);});
test('Successful empty snapshot deletes automatic data only inside its window',()=>{const b=boot();b.run("const start=Date.now()-86400000; const r={id:'a',sourcePackage:'garmin',source:'Garmin Connect',date:today(),startEpoch:Date.now()-3600000,endEpoch:Date.now()-1800000,minutes:30};receiveHealthData(JSON.stringify([r]),start,Date.now());receiveHealthData('[]',start,Date.now());");assert.equal(JSON.parse(b.storage.get('iron_auto_runs_v4')).length,0);});
test('Backup roundtrip restores manual runs and target',()=>{const b=boot();b.run("openRunModal();$('runMinutes').value='42';saveManualRun();setGoal(200);exportData()");const backup=b.elements.backupText.value;const next=boot();next.elements.backupText.value=backup;next.run('importData()');assert.equal(next.elements.homeGoal.textContent,'200분');assert.equal(next.elements.homeDone.textContent,'42:00');});
test('Malformed backup leaves original goal untouched',()=>{const b=boot();b.elements.backupText.value='{"schema":1,"goal":200}';b.run('importData()');assert.equal(b.elements.homeGoal.textContent,'160분');assert.equal(b.errors.length,1);});
