'use strict';
const M=window.IronModel, $=id=>document.getElementById(id), today=()=>M.dateStr(new Date());
const RUN_MAN='iron_manual_runs_v4',RUN_AUTO='iron_auto_runs_v4',SLEEP_MAN='iron_manual_sleep_v4',SLEEP_AUTO='iron_auto_sleep_v4',GOAL='iron_week_goal_v4';
function read(k,d){try{const v=JSON.parse(localStorage.getItem(k));return v == null ? d : v;}catch(_){return d;}}
function write(k,v){try{localStorage.setItem(k,JSON.stringify(v));return true;}catch(_){alert('저장 공간을 확인해 주세요. 기록을 저장하지 못했습니다.');return false;}}
let autoRuns=read(RUN_AUTO,[]),manualRuns=read(RUN_MAN,[]),autoSleep=read(SLEEP_AUTO,[]),manualSleep=read(SLEEP_MAN,{}),goal=+localStorage.getItem(GOAL)||160;
autoRuns=Array.isArray(autoRuns)?autoRuns:[];manualRuns=Array.isArray(manualRuns)?manualRuns:[];autoSleep=Array.isArray(autoSleep)?autoSleep:[];
manualSleep=manualSleep&&typeof manualSleep==='object'&&!Array.isArray(manualSleep)?manualSleep:{};
if(!M.numberIn(goal,30,1000))goal=160;
const nativeReady=()=>!!window.AndroidIron;
function setStatus(message,c=''){ $('healthStatus').textContent=message;$('healthStatus').className='status '+c; }
function connectHealth(){if(nativeReady())window.AndroidIron.requestHealthPermissions();else setStatus('Android 앱에서 Health Connect를 연결해 주세요.','warn');}
function syncHealth(){if(nativeReady())window.AndroidIron.syncRunningData();else setStatus('Android 앱에서 Health Connect를 연결해 주세요.','warn');}
window.onHealthStatus=(s,m)=>setStatus(m||s,s==='connected'?'good':['error','partial'].includes(s)?'warn':'');
function receiveSnapshot(text,start,end,kind){
  try {
    const incoming=JSON.parse(text);
    const validate=kind==='run'?M.validRun:M.validSleep;
    if(!Array.isArray(incoming)||!incoming.every(r=>validate(r)&&Number.isFinite(+r.startEpoch)))throw Error();
    const next=M.mergeSnapshot(kind==='run'?autoRuns:autoSleep,incoming,start,end);
    if(write(kind==='run'?RUN_AUTO:SLEEP_AUTO,next)){
      if(kind==='run')autoRuns=next;else autoSleep=next;renderAll();
    }
  } catch(_){setStatus('기록 표시 실패: 기존 기록을 유지합니다.','warn');}
}
window.receiveHealthData=(t,s,e)=>receiveSnapshot(t,s,e,'run');
window.receiveSleepData=(t,s,e)=>receiveSnapshot(t,s,e,'sleep');
const allRuns=()=>M.allRuns(manualRuns,autoRuns);
const weekStats=()=>M.stats(allRuns(),goal,today());
function renderGoal(){
 const s=weekStats();
 for(const id of ['homeGoal','goalTitleMin'])$(id).textContent=goal+'분';
 for(const id of ['homeDone','goalDone'])$(id).textContent=M.fmtMin(s.mins);
 for(const id of ['homeRemain','goalRemain'])$(id).textContent=M.fmtMin(s.remain);
 for(const id of ['homePct','goalPct'])$(id).textContent=s.pct+'%';
 for(const id of ['homeRing','goalRing']){$(id).style.setProperty('--p',s.pct);$(id).setAttribute('aria-label',`주간 목표 ${s.pct}% 완료`);}
 $('customGoal').value=goal;$('weekTotal').textContent=M.fmtMin(s.mins);$('weekCount').textContent=s.a.length+'회';
 $('weekDistance').textContent=s.dist.toFixed(1)+'km';$('weekHr').textContent=s.hr?Math.round(s.hr):'-';$('weekRemainSmall').textContent=M.fmtMin(s.remain);
 const m=M.mondayOf(new Date()),sun=new Date(m);sun.setDate(m.getDate()+6);$('weekRange').textContent=`${M.dateStr(m).slice(5)} ~ ${M.dateStr(sun).slice(5)} · 분:초`;
}
function renderLatest(){
 const r=allRuns()[0];if(!r){$('latestRun').innerHTML='<div class="empty">아직 러닝 기록이 없어요.</div>';return;}
 $('latestRun').innerHTML=`<div class="muted" style="margin-top:12px">${M.escape(r.date)} · 분:초</div><div class="grid4" style="margin-top:8px">${[
 ['시간',M.fmtMin(r.minutes)],['거리',r.distanceKm==null?'-':(+r.distanceKm).toFixed(2)+'km'],['평균 심박',r.avgHr?Math.round(r.avgHr):'-'],['출처',r.manual?'수기':r.source]
 ].map(([label,value])=>`<div class="metric"><span>${label}</span><b style="font-size:17px">${M.escape(value)}</b></div>`).join('')}</div>`;
}
function renderRecords(){
 const runs=allRuns().slice(0,100);
 $('recordList').innerHTML=runs.length?runs.map(r=>`<div class="record"><div class="recordDate">${M.escape(r.date.slice(5).replace('-','/'))}</div><div class="recordMain"><b>${r.distanceKm==null?'-':(+r.distanceKm||0).toFixed(2)} km · ${M.fmtMin(r.minutes)}</b><small>${r.avgHr?Math.round(r.avgHr)+' bpm · ':''}${M.escape(r.memo)}</small></div><div><span class="recordSource">${M.escape(r.manual?'수기':r.source)}</span>${r.manual?`<button class="btn danger small" data-delete-run="${M.escape(r.id)}">삭제</button>`:''}</div></div>`).join(''):'<div class="empty">기록이 쌓이면 여기에 보여요.</div>';
 $('recordList').querySelectorAll('[data-delete-run]').forEach(b=>b.onclick=()=>deleteManual(b.dataset.deleteRun));
 const sleeps=M.sleepByDate(autoSleep,manualSleep);
 $('sleepList').innerHTML=Object.keys(sleeps).sort().reverse().slice(0,30).map(date=>{const s=sleeps[date];return `<div class="record"><div class="recordDate">${date.slice(5)}</div><div class="recordMain"><b>${s.hours.toFixed(1)}시간</b><small>${M.escape(s.source)}${s.manual?'':' · 세션 시간'}</small></div>${s.manual?`<button class="btn danger small" data-delete-sleep="${date}">수기 삭제</button>`:'<span class="muted">자동</span>'}</div>`;}).join('')||'<div class="empty">수면 기록이 없어요.</div>';
 $('sleepList').querySelectorAll('[data-delete-sleep]').forEach(b=>b.onclick=()=>deleteSleep(b.dataset.deleteSleep));
}
function renderBars(){
 const monday=M.mondayOf(new Date()),runs=allRuns();
 $('weekBars').innerHTML=Array.from({length:7},(_,i)=>{
 const d=new Date(monday);d.setDate(d.getDate()+i);const ds=M.dateStr(d),mins=runs.filter(r=>r.date===ds).reduce((s,r)=>s+(+r.minutes),0);
 return `<div class="daybar ${ds===today()?'today':''}"><div class="barTrack"><div class="barFill" style="height:${Math.max(3,Math.min(100,mins/60*100))}%"></div></div><div>${['월','화','수','목','금','토','일'][i]}<br>${Math.round(mins)}분</div></div>`;}).join('');
 $('weeklyTrend').innerHTML=Array.from({length:8},(_,i)=>{
 const d=new Date(monday);d.setDate(d.getDate()-(7-i)*7);const date=M.dateStr(d),s=M.stats(runs,goal,date);
 return `<div style="margin:12px 0"><div class="row between"><span class="muted">${date.slice(5)} 주</span><b>${M.fmtMin(s.mins)} / ${goal}분</b></div><div style="height:8px;background:#e8eef3;border-radius:8px;margin-top:5px"><div style="height:100%;width:${s.pct}%;background:linear-gradient(90deg,var(--green),var(--blue));border-radius:8px"></div></div></div>`;}).join('');
}
function renderSleep(){const map=M.sleepByDate(autoSleep,manualSleep),dates=Object.keys(map).sort().reverse(),s=map[today()]||map[dates[0]];$('todaySleep').textContent=s?s.hours.toFixed(1)+'시간':'-';$('sleepSource').textContent=s?`${s.date} · ${s.source}${s.manual?'':' · 깨어 있는 시간을 포함한 세션 시간'}`:'수면 기록이 없어요';}
function renderAll(){renderGoal();renderLatest();renderRecords();renderBars();renderSleep();}
function setGoal(value){if(!M.numberIn(value,30,1000)){alert('목표는 30~1000분으로 입력해 주세요.');return;}try{localStorage.setItem(GOAL,value);goal=+value;renderAll();}catch(_){alert('목표를 저장하지 못했습니다.');}}
function saveCustomGoal(){setGoal($('customGoal').value);}
function openRunModal(){for(const id of ['runMinutes','runDistance','runHr','runMemo','runTime'])$(id).value='';$('runDate').value=today();$('runModal').classList.add('on');}
function openSleepModal(){$('sleepDate').value=today();$('sleepHours').value='';$('sleepModal').classList.add('on');}
function closeModal(id){$(id).classList.remove('on');}
function saveManualRun(){
 const d=$('runDate').value,m=$('runMinutes').value,km=$('runDistance').value,hr=$('runHr').value,time=$('runTime').value;
 if(!M.validDate(d)||d>today()||!M.numberIn(m,0.01,1440)||(km!==''&&!M.numberIn(km,0,500))||(hr!==''&&!M.numberIn(hr,30,250))){alert('날짜·운동시간(0~1440분)·거리·심박을 확인해 주세요.');return;}
 const row={id:'m'+Date.now()+Math.random().toString(36).slice(2,7),date:d,startEpoch:Date.parse(d+'T'+(time||'12:00')+':00'),minutes:+m,distanceKm:km===''?null:+km,avgHr:hr===''?null:+hr,source:'수기',memo:$('runMemo').value.slice(0,300),timeKnown:!!time};
 if(autoRuns.some(r=>r.date===d&&Math.abs(r.minutes-row.minutes)<=1)&&!confirm('같은 날짜에 비슷한 자동 러닝이 있습니다. 별도 기록으로 추가할까요?'))return;
 const next=[...manualRuns,row];if(write(RUN_MAN,next)){manualRuns=next;closeModal('runModal');renderAll();}
}
function deleteManual(id){if(!confirm('이 수기 러닝을 삭제할까요?'))return;const next=manualRuns.filter(r=>r.id!==id);if(write(RUN_MAN,next)){manualRuns=next;renderAll();}}
function saveManualSleep(){const d=$('sleepDate').value,h=$('sleepHours').value;if(!M.validDate(d)||d>today()||!M.numberIn(h,0.001,24)){alert('날짜와 수면시간(0~24시간)을 확인해 주세요.');return;}const next={...manualSleep,[d]:+h};if(write(SLEEP_MAN,next)){manualSleep=next;closeModal('sleepModal');renderAll();}}
function deleteSleep(date){if(!confirm('수기 수면을 삭제하고 자동 기록을 표시할까요?'))return;const next={...manualSleep};delete next[date];if(write(SLEEP_MAN,next)){manualSleep=next;renderAll();}}
function go(page){document.querySelectorAll('.page').forEach(e=>e.classList.toggle('on',e.id===page));document.querySelectorAll('.tabBtn').forEach(e=>e.classList.toggle('on',e.dataset.page===page));window.scrollTo(0,0);renderAll();}
function exportData(){$('backupText').value=JSON.stringify({schema:1,goal,manualRuns,manualSleep,autoRuns,autoSleep},null,2);$('backupModal').classList.add('on');}
function importData(){try{const d=JSON.parse($('backupText').value);if(d.schema!==1||!M.numberIn(d.goal,30,1000)||!Array.isArray(d.manualRuns)||!d.manualRuns.every(r=>M.validRun(r)&&typeof r.id==='string')||!d.manualSleep||typeof d.manualSleep!=='object'||Array.isArray(d.manualSleep)||!Object.entries(d.manualSleep).every(([date,h])=>M.validDate(date)&&M.numberIn(h,0.001,24)))throw Error();if(!confirm('백업의 수기 기록과 목표로 교체할까요? 자동 기록은 다시 동기화할 수 있습니다.'))return;
 const original=[RUN_MAN,SLEEP_MAN,GOAL].map(k=>[k,localStorage.getItem(k)]);
 try{localStorage.setItem(RUN_MAN,JSON.stringify(d.manualRuns));localStorage.setItem(SLEEP_MAN,JSON.stringify(d.manualSleep));localStorage.setItem(GOAL,d.goal);}catch(e){for(const[k,v]of original){if(v===null)localStorage.removeItem(k);else localStorage.setItem(k,v);}throw e;}
 manualRuns=d.manualRuns;manualSleep=d.manualSleep;goal=+d.goal;closeModal('backupModal');renderAll();
 }catch(_){alert('백업 형식 또는 저장 공간을 확인해 주세요.');}}
document.querySelectorAll('.modal').forEach(m=>m.addEventListener('click',e=>{if(e.target===m)m.classList.remove('on');}));
renderAll();
// Native onPageFinished/onResume triggers Health Connect reads after this script is ready.
setInterval(()=>{if(!document.querySelector('.modal.on'))renderAll();},60000);
