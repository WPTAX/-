/* Shared by the WebView and dependency-free regression tests. */
(function (root) {
  'use strict';
  const dateStr = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const validDate = s => typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && dateStr(new Date(s+'T12:00:00')) === s;
  const mondayOf = value => { const d = new Date(value); d.setHours(0,0,0,0); d.setDate(d.getDate()-(d.getDay()+6)%7); return d; };
  const weekKey = s => validDate(s) ? dateStr(mondayOf(s+'T12:00:00')) : '';
  const numberIn = (v,min,max) => v !== '' && v != null && Number.isFinite(+v) && +v >= min && +v <= max;
  const escape = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmtMin = value => {const sec=Math.round((+value||0)*60);return `${Math.floor(sec/60)}:${String(sec%60).padStart(2,'0')}`;};
  const validRun = r => r && validDate(r.date) && numberIn(r.minutes,0.01,1440);
  const validSleep = r => r && validDate(r.date) && numberIn(r.hours,0.001,24);
  const recordKey = r => r.id ? `${r.sourcePackage||r.source}:${r.id}` : `${r.sourcePackage||r.source}:${r.startEpoch}:${r.minutes}`;

  function mergeSnapshot(old, incoming, start, end) {
    // Read requests include intersecting intervals. Drop records in the successful
    // read window, but preserve cache older than the granted history scope.
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) throw Error('Invalid sync window');
    const keep = old.filter(r => {
      const a=+r.startEpoch, b=+r.endEpoch || a+(+r.minutes||0)*60000;
      return !(a < end && b > start);
    });
    const map = new Map(keep.map(r => [recordKey(r),r]));
    for (const r of incoming) map.set(recordKey(r),r);
    return [...map.values()];
  }

  function allRuns(manual, automatic) {
    const auto = automatic.filter(validRun).map(r=>({...r,manual:false}));
    auto.sort((a,b)=>(a.source==='Garmin Connect'?0:1)-(b.source==='Garmin Connect'?0:1));
    const selected=[];
    for (const r of auto) {
      // Same-origin sessions and manually entered records must never be silently discarded.
      const duplicate=selected.some(q=>recordKey(q)===recordKey(r) ||
        (q.sourcePackage && r.sourcePackage && q.sourcePackage!==r.sourcePackage &&
         Math.abs(+q.startEpoch-(+r.startEpoch))<=60000 && Math.abs(+q.minutes-(+r.minutes))<=1));
      if (!duplicate) selected.push(r);
    }
    return [...manual.filter(validRun).map(r=>({...r,manual:true})),...selected]
      .sort((a,b)=>(+b.startEpoch||Date.parse(b.date+'T12:00:00'))-(+a.startEpoch||Date.parse(a.date+'T12:00:00')));
  }

  function stats(runs, goal, date) {
    const a=runs.filter(r=>weekKey(r.date)===weekKey(date));
    const mins=a.reduce((s,r)=>s+(+r.minutes),0),dist=a.reduce((s,r)=>s+(+r.distanceKm||0),0);
    const withHr=a.filter(r=>numberIn(r.avgHr,1,250));
    const denominator=withHr.reduce((s,r)=>s+(+r.minutes),0);
    const hr=denominator?withHr.reduce((s,r)=>s+(+r.avgHr)*(+r.minutes),0)/denominator:0;
    return {a,mins,dist,hr,remain:Math.max(0,goal-mins),pct:Math.min(100,Math.round(mins/goal*100))};
  }

  function sleepByDate(automatic,manual) {
    const grouped={};
    for (const r of automatic.filter(validSleep)) {
      if (!grouped[r.date]) grouped[r.date] = [];
      grouped[r.date].push(r);
    }
    const out={};
    for (const [date,rows] of Object.entries(grouped)) {
      const garmin=rows.filter(r=>r.source==='Garmin Connect');
      const source=garmin.length?garmin[0].sourcePackage:rows.slice().sort((a,b)=>b.hours-a.hours)[0].sourcePackage;
      const chosen=rows.filter(r=>r.sourcePackage===source).sort((a,b)=>a.startEpoch-b.startEpoch);
      let total=0,lastEnd=-Infinity;
      for (const r of chosen) {
        const start=+r.startEpoch,end=+r.endEpoch || start+r.hours*3600000;
        total+=Math.max(0,end-Math.max(start,lastEnd));lastEnd=Math.max(lastEnd,end);
      }
      const hours=Math.min(24,total/3600000);
      out[date]={date,hours,source:chosen[0].source,manual:false};
    }
    // An explicit manual entry is an override. Removing it restores the automatic value.
    for (const [date,hours] of Object.entries(manual)) if(validDate(date)&&numberIn(hours,0.001,24)) out[date]={date,hours:+hours,source:'수기',manual:true};
    return out;
  }
  const api={dateStr,validDate,mondayOf,weekKey,numberIn,escape,fmtMin,validRun,validSleep,mergeSnapshot,allRuns,stats,sleepByDate};
  if (typeof module!=='undefined') module.exports=api;
  root.IronModel=api;
})(typeof window!=='undefined'?window:globalThis);
