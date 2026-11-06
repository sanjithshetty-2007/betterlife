// Calendar (Prompts 10, 18) and reminders: pop-up, Reminder Center, Health, Quiet Hours (Prompts 8, 11, 12).
'use strict';

/* ============ CALENDAR ============ */
function calTabs(on) {
  return `<div class="tabs">${['Day', 'Week', 'Month', 'Agenda'].map(t => `<a class="tab ${t === on ? 'on' : ''}" href="?s=calendar-${t.toLowerCase()}">${t}</a>`).join('')}</div>`;
}
function calHeader(title, on) {
  return `${appbar(title, { back: false, actions: ['filter_list', 'search'] })}${calTabs(on)}`;
}
const DOTS = { 2: ['#7E8CFF'], 5: ['#FF6B5E', '#E0A800'], 6: ['#7E8CFF', '#7BC67E', '#FF6B5E'], 7: ['#FF8FB8'], 10: ['#7E8CFF'], 12: ['#5EC4D6', '#E0A800'], 13: ['#FF6B5E'], 17: ['#7E8CFF'], 19: ['#FF8FB8', '#7BC67E'], 21: ['#E0A800'], 24: ['#7E8CFF'], 26: ['#5EC4D6'], 28: ['#FF6B5E', '#FF8FB8'] };
function monthGrid(sel = 6) {
  let s = ['M', 'T', 'W', 'T', 'F', 'S', 'S'].map(d => `<div class="dow">${d}</div>`).join('');
  for (let i = 0; i < 35; i++) {
    const d = i - 5; const out = d < 1 || d > 30; const n = d < 1 ? 31 + d : d > 30 ? d - 30 : d;
    const dots = (!out && DOTS[d]) || [];
    s += `<div class="mday ${out ? 'out' : ''} ${d === 6 ? 'today' : ''} ${d === sel && sel !== 6 ? 'sel' : ''}"><span class="n">${n}</span><span class="dots">${dots.map(c => `<i style="background:${c}"></i>`).join('')}</span>
      ${d === 7 ? '<span style="font-size:10px;line-height:1">🎂</span>' : ''}</div>`;
  }
  return `<div class="mgrid">${s}</div>`;
}
function dayItems() {
  return `<div class="list">${[
    ['9:00 AM', 'Pay electricity bill', 'Task · Pop-up', '#E0A800', 'task_alt'],
    ['10:30 AM', 'Design sync with Arya', 'Meeting · 45 min', '#7E8CFF', 'groups', 1],
    ['10:45 AM', 'Lab: optics demo', 'Event · Uni', '#5EC4D6', 'science', 1],
    ['2:00 PM', 'Gym', 'Task · Repeats daily', '#7BC67E', 'fitness_center'],
    ['All day', "Mom's birthday", 'Birthday · turns 52', '#FF8FB8', 'cake']].map(([t, n, m, c, i, clash], k) => `
    <div class="card tight row g12 in" ${st(k)} style="border-left:4px solid ${c}"><div style="width:58px" class="label-m muted">${t}</div>
    <div class="f1"><div class="title-s ell">${n}</div><div class="body-s muted row g4">${ic(i, 'sm')}${m}</div></div>${clash ? `<span class="status y">${icf('warning')}Clash</span>` : ''}</div>`).join('')}</div>`;
}
screen('calendar-month', 'Calendar', 'Month view', () => device(`<div class="screen">${calHeader('November', 'Month')}
  <div class="row between" style="padding:12px 16px 4px"><span class="iconbtn tonal">${ic('chevron_left')}</span><span class="title-m">November 2026</span><span class="iconbtn tonal">${ic('chevron_right')}</span></div>
  <div class="scroll pb-nav fade">${monthGrid()}
  <div class="row g8 mt8 wrap">${[['Work', '#7E8CFF'], ['Health', '#7BC67E'], ['Personal', '#FF6B5E'], ['Birthdays', '#FF8FB8']].map(([n, c]) => `<span class="chip pill" style="height:28px"><i style="width:8px;height:8px;border-radius:50%;background:${c};display:block"></i>${n}</span>`).join('')}</div>
  <div class="section-h"><span class="title-s">Today · Fri 6 Nov</span><a>+ Add</a></div>${dayItems()}</div>
  <div class="fab pop" ${st(6)}>${ic('add', 'lg')}</div></div>`, { nav: 'calendar' }));
screen('calendar-daysheet', 'Calendar', 'Tap a day: bottom sheet', () => device(`<div class="screen">${calHeader('November', 'Month')}
  <div class="row between" style="padding:12px 16px 4px"><span class="iconbtn tonal">${ic('chevron_left')}</span><span class="title-m">November 2026</span><span class="iconbtn tonal">${ic('chevron_right')}</span></div>
  <div class="scroll">${monthGrid(12)}</div></div>
  <div class="scrim anim soft"></div><div class="sheet anim"><div class="grab"></div>
  <div class="row between"><div><div class="title-l">Thursday, 12 Nov</div><div class="body-s muted">2 events · 1 task</div></div><span class="iconbtn tonal">${ic('close')}</span></div>
  <div class="list mt16">${[['11:00 AM', 'Physio session', 'Event', '#5EC4D6'], ['4:00 PM', 'Club poster due', 'Task · High', '#FF6B5E'], ['7:30 PM', 'Movie night', 'Event', '#E0A800']].map(([t, n, m, c], k) => `
    <div class="card tight row g12 in" ${st(k)} style="border-left:4px solid ${c}"><div style="width:58px" class="label-m muted">${t}</div><div class="f1"><div class="title-s">${n}</div><div class="body-s muted">${m}</div></div></div>`).join('')}</div>
  <div class="row g8 mt16"><a class="btn tonal f1">${ic('event')}Add event</a><a class="btn outline f1">${ic('add_task')}Add task</a></div></div>`, { nav: 'calendar' }));
function timeCol(from, to) {
  let s = ''; for (let h = from; h <= to; h++) s += `<div class="hr">${h > 12 ? h - 12 : h} ${h >= 12 ? 'PM' : 'AM'}</div>`; return s;
}
screen('calendar-week', 'Calendar', 'Week view', () => {
  const days = ['Mon 2', 'Tue 3', 'Wed 4', 'Thu 5', 'Fri 6', 'Sat 7', 'Sun 8'];
  const colW = 51; const ev = (d, h, len, c, t) => `<div class="ev fade" style="left:${44 + d * colW}px;width:${colW - 4}px;top:${(h - 8) * 60 + 1}px;height:${len * 60 - 3}px;background:${c}33;border-color:${c}">${t}</div>`;
  return device(`<div class="screen">${calHeader('Week 45', 'Week')}
  <div class="row" style="padding:12px 16px 4px 60px">${days.map((d, i) => `<div class="f1 tc"><div class="label-s muted">${d.split(' ')[0]}</div><div class="title-m" style="${i === 4 ? 'background:var(--yellow);color:var(--bg);border-radius:50%;width:30px;height:30px;line-height:30px;margin:2px auto 0' : ''}">${d.split(' ')[1]}</div></div>`).join('')}</div>
  <div style="margin:4px 16px 0 60px;display:flex;gap:4px"><span class="f1"></span><span style="flex:2.1;background:#FF8FB833;border-left:3px solid #FF8FB8;border-radius:8px;font-size:11px;padding:3px 6px">🎂 Mom</span><span class="f1"></span></div>
  <div class="scroll pb-nav" style="padding:6px 16px 0"><div class="tgrid">${timeCol(8, 19)}
  ${ev(0, 9, 1, '#7E8CFF', 'Standup')}${ev(1, 11, 1.5, '#5EC4D6', 'Physio')}${ev(2, 9, 1, '#7E8CFF', 'Standup')}${ev(2, 14, 2, '#E0A800', 'Lab')}
  ${ev(4, 10.5, .75, '#7E8CFF', 'Sync')}<div class="ev fade" style="left:${44 + 4 * colW + 24}px;width:${colW / 2 - 2}px;top:${2.75 * 60}px;height:${60 - 3}px;background:#5EC4D633;border-color:#5EC4D6">⚠</div>
  ${ev(4, 14, 1, '#7BC67E', 'Gym')}${ev(5, 12, 2, '#FF6B5E', 'Lunch')}${ev(6, 17, 1.5, '#C77DFF', 'Guitar')}
  <div class="nowline" style="top:${0.4 * 60}px;left:${44 + 4 * colW}px;width:${colW}px;right:auto"></div></div></div></div>`, { nav: 'calendar' });
});
screen('calendar-day', 'Calendar', 'Day view', () => device(`<div class="screen">${calHeader('Fri 6 Nov', 'Day')}
  <div class="row g8" style="padding:12px 16px"><span class="chip pill" style="background:#FF8FB833;border-color:#FF8FB8;flex:1">🎂 Mom's birthday · all day</span></div>
  <div class="scroll pb-nav"><div class="tgrid">${timeCol(8, 18)}
    ${[[9, .5, '#E0A800', 'Pay electricity bill', 'Task · Pop-up', 0, 1], [10.5, .75, '#7E8CFF', 'Design sync with Arya', 'Meeting · Room 4', 0, .5], [10.75, 1, '#5EC4D6', 'Lab: optics demo', '⚠ Clash', .5, .5], [14, 1, '#7BC67E', 'Gym', 'Repeats daily', 0, 1], [16, 1.5, '#C77DFF', 'Guitar practice', 'Event', 0, 1]].map(([h, l, c, t, m, x, w], k) => `
    <div class="ev in" ${st(k)} style="left:calc(48px + (100% - 48px) * ${x});width:calc((100% - 52px) * ${w});top:${(h - 8) * 60 + 1}px;height:${l * 60 - 3}px;background:${c}2e;border-color:${c}"><b>${t}</b><div style="opacity:.75">${m}</div></div>`).join('')}
    <div class="nowline" style="top:${0.4 * 60}px"></div></div></div></div>`, { nav: 'calendar' }));
screen('calendar-agenda', 'Calendar', 'Agenda (sticky headers)', () => device(`<div class="screen">${calHeader('Agenda', 'Agenda')}
  <div class="scroll pb-nav" style="padding-top:4px">${[['Today · Fri 6 Nov', [['9:00 AM', 'Pay electricity bill', '#E0A800', 'Task'], ['10:30 AM', 'Design sync with Arya', '#7E8CFF', 'Meeting', 1], ['10:45 AM', 'Lab: optics demo', '#5EC4D6', 'Event', 1]]], ['Tomorrow · Sat 7 Nov', [['All day', "Mom's birthday 🎂", '#FF8FB8', 'Turns 52'], ['12:00 PM', 'Lunch with cousins', '#FF6B5E', 'Event']]], ['Wed 11 Nov', [['9:00 AM', 'Standup', '#7E8CFF', 'Meeting · weekly']]]].map(([d, items], k) => `
    <div style="position:sticky;top:0;background:var(--bg);padding:14px 4px 8px;z-index:2" class="row between"><span class="title-s ${k === 0 ? 'yellow' : 'muted'}">${d}</span><span class="body-s faint">${items.length}</span></div>
    <div class="list">${items.map(([t, n, c, m, cl], j) => `<div class="card tight row g12 in" ${st(k * 3 + j)} style="border-left:4px solid ${c}"><div style="width:62px" class="label-m muted">${t}</div><div class="f1"><div class="title-s">${n}</div><div class="body-s muted">${m}</div></div>${cl ? `<span class="status y">${icf('warning')}Clash</span>` : ''}</div>`).join('')}</div>`).join('')}</div></div>`, { nav: 'calendar' }));
screen('event-editor', 'Calendar', 'Event editor', () => device(`<div class="screen enter-up">
  <div class="appbar"><span class="iconbtn" style="margin-left:-8px">${ic('close')}</span><span class="t">New event</span><a class="btn primary sm">Save</a></div>
  <div class="scroll"><div class="row g8">${[['event', 'Event'], ['groups', 'Meeting'], ['cake', 'Birthday']].map(([i, t], k) => `<span class="chip f1 center ${k === 1 ? 'sel' : ''}" style="height:44px">${ic(i)}${t}</span>`).join('')}</div>
  <div class="field focus mt16"><span class="lbl">Title</span><span class="v">Design sync with Arya<span class="caret"></span></span></div>
  <div class="card flat mt16"><div class="lrow"><span class="lead">${ic('schedule')}</span><div class="f1"><div>Fri 6 Nov · 10:30 AM</div><div class="body-s muted">to 11:15 AM (45 min)</div></div></div>
  <div class="lrow"><span class="lead">${ic('wb_sunny')}</span><span class="f1">All day</span><span class="switch"></span></div>
  <div class="lrow"><span class="lead">${ic('location_on')}</span><span class="f1 muted">Room 4, design block</span></div>
  <div class="lrow"><span class="lead">${ic('repeat')}</span><span class="f1">Repeat</span><span class="muted">Weekly on Fri</span></div>
  <div class="lrow"><span class="lead">${ic('palette')}</span><span class="f1">Category</span><span class="row g4"><i style="width:10px;height:10px;border-radius:50%;background:#7E8CFF;display:block"></i>Work</span></div></div>
  <div class="card tight row g12 mt12" style="border-color:rgba(224,168,0,.5);background:var(--yellow-soft)">${icf('warning', 'yellow')}<span class="body-s f1">Overlaps <b>Lab: optics demo</b> (10:45 AM)</span></div>
  <div class="label-m muted mt16 mb8">REMINDERS</div>
  <div class="list"><div class="card tight row g12">${ic('notifications', 'yellow')}<span class="f1 title-s">10 min before</span><span class="lvl full">Pop-up</span></div>
  <div class="card tight row g12">${ic('notifications', 'yellow')}<span class="f1 title-s">At time</span><span class="lvl normal">Notification</span></div></div></div></div>`));

/* ============ REMINDER POP-UP (Prompt 8) ============ */
function behindApp() {
  // A stand-in for "some other app" under the overlay.
  return `<div style="position:absolute;inset:0;background:#f2eee9;padding:52px 18px">${[...Array(7)].map((_, i) => `<div style="height:${i % 3 ? 70 : 150}px;border-radius:14px;background:${i % 3 ? '#e2dcd5' : '#d6cdc3'};margin-bottom:12px"></div>`).join('')}</div>`;
}
function popupCard({ title = 'Pay electricity bill', time = '9:00 AM', level = 'full', notes = 'BESCOM · due today', panel = '' } = {}) {
  return `<div class="popup-card anim">
    <div class="row between"><span class="lvl ${level}">${icf(level === 'alarm' ? 'alarm' : 'fullscreen', 'sm')}${level === 'alarm' ? 'ALARM' : 'POP-UP'}</span><span class="iconbtn" style="width:40px;height:40px;margin:-6px -8px 0 0">${ic('info', 'muted')}</span></div>
    <div class="col" style="align-items:center;text-align:center;margin-top:-6px"><div class="pop" ${st(2)}>${mascot({ size: 120, mood: 'curious' })}</div>
    <div class="display-s heavy mono mt8" style="letter-spacing:-1px">${time}</div><div class="headline-s bold mt4">${title}</div><div class="body-m muted mt4">${notes}</div></div>
    <div class="guard mt16"><i></i></div>
    ${panel || `<div class="col g8 mt16"><a class="btn primary block" style="height:56px;font-size:16px">${icf('check_circle')}Done</a>
    <div class="row g8"><a class="btn tonal f1">${ic('snooze')}Snooze</a><a class="btn outline f1">${ic('edit_calendar')}Reschedule</a></div></div>`}</div>`;
}
function popupScreen(card, extra = '') {
  return device(`${behindApp()}<div class="scrim anim" style="backdrop-filter:blur(6px)"></div>
    <div style="position:absolute;inset:0;z-index:80;display:flex;flex-direction:column;justify-content:center;padding:0 18px">${card}</div>${extra}`, { time: '9:00' });
}
screen('popup', 'Reminders', 'Full-screen pop-up over another app', () => popupScreen(popupCard()));
screen('popup-snooze', 'Reminders', 'Pop-up: snooze options', () => popupScreen(popupCard({ panel: `<div class="mt16 fade"><div class="row between"><span class="title-m">Snooze for…</span><span class="body-s muted">Snoozed 3×</span></div>
  <div class="card tight row g12 mt12" style="border-color:rgba(224,168,0,.5);background:var(--yellow-soft)">${ic('event_upcoming', 'yellow')}<span class="body-s f1">You have snoozed this 3 times. Move it to tomorrow instead?</span><a class="btn sm primary">Move</a></div>
  <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px" class="mt12">${[['10 min', ''], ['30 min', ''], ['1 hour', ''], ['Tonight', '8:00 PM'], ['Tomorrow', '9:00 AM'], ['Pick time', '']].map(([t, s], i) => `<span class="card tight tc pop" ${st(i)} style="padding:10px 4px;${i === 0 ? 'background:var(--cherry);border-color:var(--cherry)' : ''}"><div class="title-s">${t}</div><div class="body-s muted">${s || '&nbsp;'}</div></span>`).join('')}</div>
  <a class="btn text block mt8">Back</a></div>` })));
screen('popup-why', 'Reminders', 'Pop-up: "Why am I seeing this?"', () => popupScreen(popupCard({ panel: `<div class="mt16 fade"><div class="title-m">Why am I seeing this?</div>
  <div class="card flat mt12" style="background:var(--surface-sunk)">${[['task_alt', 'Item', 'Pay electricity bill (task)'], ['schedule', 'Due', 'Today 9:00 AM'], ['rule', 'Rule that fired', 'At time'], ['fullscreen', 'Importance', 'Pop-up (full screen)']].map(([i, k, v]) => `<div class="lrow" style="min-height:48px;padding:8px 14px"><span class="muted">${ic(i, 'md')}</span><span class="f1 body-s muted">${k}</span><span class="body-m">${v}</span></div>`).join('')}</div>
  <div class="row g8 mt12"><a class="btn outline f1">${ic('edit')}Edit item</a><a class="btn text">Back</a></div></div>` })));
screen('popup-stack', 'Reminders', 'Pop-up: grouped stack', () => device(`${behindApp()}<div class="scrim anim" style="backdrop-filter:blur(6px)"></div>
  <div style="position:absolute;inset:0;z-index:80;display:flex;flex-direction:column;justify-content:center;padding:0 18px">
  <div class="row g12 mb16 fade">${mascot({ size: 64, mood: 'curious' })}<div><div class="label-m yellow">3 REMINDERS · GROUPED</div><div class="title-l bold">A few things at once</div></div></div>
  ${[['9:00 AM', 'Pay electricity bill', 'full'], ['9:05 AM', 'Take creatine', 'normal'], ['9:10 AM', 'Leave for class', 'alarm']].map(([t, n, l], k) => `
  <div class="popup-card anim" style="animation-delay:${k * 90}ms;padding:14px 14px 14px 16px;margin-bottom:10px;border-radius:24px"><div class="row g12"><div class="f1"><div class="row g8"><span class="lvl ${l}" style="height:20px;font-size:10px">${l === 'alarm' ? 'ALARM' : l === 'full' ? 'POP-UP' : 'NOTIFY'}</span><span class="label-m muted mono">${t}</span></div><div class="title-m mt4">${n}</div></div>
  <span class="iconbtn tonal">${ic('snooze', 'md')}</span><span class="iconbtn" style="background:var(--yellow);color:var(--bg);width:44px;height:44px">${icf('check')}</span></div></div>`).join('')}
  <div class="row g8 mt8"><a class="btn primary f1">All done</a><a class="btn tonal f1">Snooze all</a></div></div>`, { time: '9:10' }));
screen('popup-locked', 'Reminders', 'Locked phone: private ALARM', () => device(`<div style="position:absolute;inset:0;background:radial-gradient(70% 50% at 50% 30%,#5a1016,#120607)"></div>
  <div class="screen" style="align-items:center;text-align:center;padding:60px 24px 0;z-index:5">
  <div class="body-l muted">${ic('lock', 'sm')} Locked</div><div style="font-size:88px;font-weight:300;line-height:1;letter-spacing:-3px" class="mt8">6:30</div>
  <div class="mt24 pop">${mascot({ size: 140, mood: 'cheering' })}</div><span class="lvl alarm mt16 pop" ${st(2)}>${icf('alarm', 'sm')}ALARM</span>
  <div class="headline-s bold mt12 fade">Reminder</div><div class="body-m muted mt4 fade">Title hidden while locked</div>
  <div style="margin-top:auto;padding-bottom:64px;width:100%" class="col g12"><div class="row g8"><a class="btn tonal f1">${ic('snooze')}Snooze 10 min</a></div>
  <a class="btn primary block" style="height:64px;font-size:17px">${icf('check_circle')}I'm up</a></div></div>`, { time: '6:30' }));

/* ============ REMINDER CENTER / HEALTH / QUIET (Prompts 11, 12) ============ */
screen('reminder-center', 'Reminders', 'Reminder Center', () => device(`<div class="screen enter">${appbar('Reminder Center', { actions: ['search'] })}
  <div class="chips" style="padding:0 16px 12px">${['All', 'Scheduled', 'Fired', 'Missed', 'Held', 'Snoozed', 'Failed'].map((c, i) => `<span class="chip ${i === 0 ? 'sel' : ''}">${c}</span>`).join('')}</div>
  <div class="scroll"><div class="label-m muted mb8">TODAY</div><div class="card flat">${[
    ['9:00 AM', 'Pay electricity bill', 'status g', 'check_circle', 'Completed', 'Pop-up'],
    ['8:30 AM', 'Take creatine', 'status y', 'snooze', 'Snoozed ×2', 'Notification'],
    ['7:00 AM', 'Meditation', 'status n', 'pause_circle', 'Held', 'Notification', 'Held because Quiet Hours were active. Delivered in the 7:00 AM digest.'],
    ['6:30 AM', 'Leave for class', 'status g', 'notifications_active', 'Fired', 'Alarm'],
    ['Yesterday', 'Call the bank', 'status r', 'error', 'Missed', 'Pop-up', 'The phone was off when this was due.'],
    ['Yesterday', 'Water the plants', 'status y', 'call', 'Downgraded', 'Notification', 'You were on a call, so it came as a normal notification.']].map(([t, n, sc, i, s, l, why], k) => `
    <div class="lrow in" ${st(k)} style="align-items:flex-start"><div style="width:70px" class="label-m muted mt4">${t}</div><div class="f1"><div class="title-s">${n}</div><div class="body-s muted">${l}</div>${why ? `<div class="body-s mt4" style="color:var(--text);opacity:.85">${why}</div>` : ''}</div><span class="${sc}">${icf(i)}${s}</span></div>`).join('')}</div>
  <p class="body-s faint tc mt16">Kept for 60 days</p></div></div>`));
screen('reminder-health', 'Reminders', 'Reminder Health', () => device(`<div class="screen enter">${appbar('Reminder Health', { actions: ['refresh'] })}
  <div class="scroll"><div class="card hero row g16 in">${ring(78, 72, 8, 'var(--yellow)', `<div class="title-m bold">7/9</div>`)}<div class="f1"><div class="title-m">2 things need you</div><div class="body-s muted">Checked 2 min ago</div><a class="btn primary sm mt8">${ic('build')}Fix all</a></div></div>
  <div class="row g8 mt12"><a class="btn tonal f1 sm">${ic('timer')}Test in 10 s</a><a class="btn outline f1 sm">${ic('swipe_up')}Test after closing</a></div>
  <div class="section-h"><span class="title-s">Android</span></div><div class="card flat">${[['notifications_active', 'Notifications', 'g', 'On'], ['alarm', 'Exact alarms', 'g', 'Allowed'], ['tune', 'Channels not downgraded', 'g', 'OK'], ['layers', 'Draw over other apps', 'r', 'Off', 'Without it, pop-ups only show as notifications.'], ['fullscreen', 'Full-screen intent', 'g', 'Allowed'], ['battery_saver', 'Battery unrestricted', 'y', 'Optimised', 'OriginOS may delay alarms.']].map(([i, t, s, w, why], k) => `
    <div class="lrow in" ${st(k)}><span class="lead c">${ic(i)}</span><div class="f1"><div class="title-s">${t}</div>${why ? `<div class="body-s muted">${why}</div>` : ''}</div>${s === 'g' ? `<span class="status g">${icf('check_circle')}${w}</span>` : `<a class="btn sm ${s === 'r' ? 'primary' : 'outline'}">Fix</a>`}</div>`).join('')}</div>
  <div class="section-h"><span class="title-s">Vivo · ticked by hand</span></div><div class="card flat">${['Auto-start', 'Background pop-ups', 'Show on lock screen', 'Locked in recents'].map((t, k) => `<div class="lrow"><span class="tick ${k < 3 ? 'on' : ''}" style="width:24px;height:24px">${k < 3 ? ic('check', 'sm') : ''}</span><span class="f1">${t}</span>${ic('open_in_new', 'muted md')}</div>`).join('')}</div><div style="height:24px"></div></div></div>`));
screen('quiet-hours', 'Reminders', 'Quiet Hours, Pause & fatigue settings', () => device(`<div class="screen enter">${appbar('Quiet & calm')}
  <div class="scroll"><div class="card in"><div class="row between"><div class="row g12">${icf('bedtime', 'yellow')}<span class="title-m">Quiet Hours</span></div><span class="switch on"></span></div>
  <div class="row g8 mt16"><div class="card tight f1 tc raised"><div class="label-s muted">FROM</div><div class="headline-s mono">22:00</div></div>${ic('arrow_forward', 'muted')}<div class="card tight f1 tc raised"><div class="label-s muted">TO</div><div class="headline-s mono">07:00</div></div></div>
  <div class="row g4 mt12 center">${['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => `<span style="width:38px;height:38px;border-radius:50%;display:grid;place-items:center;font-weight:500;${i < 5 ? 'background:var(--cherry)' : 'border:1px solid var(--border);color:var(--muted)'}">${d}</span>`).join('')}</div>
  <p class="body-s muted mt12">Held reminders arrive as one digest at 7:00.</p></div>
  <div class="card flat mt12 in" ${st(1)}><div class="lrow"><span class="lead">${ic('alarm')}</span><span class="f1">ALARM level ignores Quiet Hours</span><span class="switch on"></span></div>
  <div class="lrow"><span class="lead">${ic('call')}</span><div class="f1"><div>During calls</div><div class="body-s muted">Pop-ups become notifications</div></div>${ic('lock', 'muted md')}</div></div>
  <div class="section-h"><span class="title-s">Fatigue protection</span></div>
  <div class="card flat in" ${st(2)}><div class="lrow"><span class="lead">${ic('speed')}</span><span class="f1">Max pop-ups per hour</span><span class="row g8"><span class="iconbtn tonal" style="width:32px;height:32px">${ic('remove', 'md')}</span><b>3</b><span class="iconbtn tonal" style="width:32px;height:32px">${ic('add', 'md')}</span></span></div>
  <div class="lrow"><span class="lead">${ic('stacks')}</span><span class="f1">Group reminders within</span><span class="yellow">10 min</span></div>
  <div class="lrow"><span class="lead">${ic('trending_down')}</span><div class="f1"><div>Ignored 3× → calm for 24 h</div><div class="body-s muted">You'll be told once</div></div><span class="switch on"></span></div></div></div></div>`));
