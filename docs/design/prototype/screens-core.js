// Onboarding, lock, Today, Tasks, Habits.
'use strict';

/* ============ ONBOARDING & LOCK (Prompts 27, 28, S1) ============ */
function obShell(step, body, cta = 'Continue', skip = false) {
  const dots = [1, 2, 3, 4, 5].map(i => `<i style="width:${i === step ? 22 : 8}px;height:8px;border-radius:4px;background:${i <= step ? 'var(--yellow)' : 'rgba(245,233,224,.2)'};display:block;transition:width .3s"></i>`).join('');
  return device(`<div class="screen enter-up" style="padding:36px 24px 0">
    <div class="row between" style="height:56px"><div class="row g4">${dots}</div>${skip ? '<span class="label-l muted">Skip</span>' : ''}</div>
    <div class="col f1">${body}</div>
    <div style="padding:16px 0 44px"><a class="btn primary block">${cta}</a></div></div>`);
}
screen('onboarding-welcome', 'Onboarding & lock', 'Welcome', () => obShell(1, `
  <div class="col f1 center" style="align-items:center;text-align:center">
    <div style="position:relative;display:grid;place-items:center;width:260px;height:260px">
      <div style="position:absolute;inset:20px;border-radius:50%;background:radial-gradient(circle,rgba(224,168,0,.22),transparent 65%)"></div>
      <div class="pop">${mascot({ size: 170, mood: 'cheering', stage: 2 })}</div></div>
    <div class="display-s heavy fade" ${st(2)} style="letter-spacing:-1px">Hey, I'm Blip.</div>
    <p class="body-l muted mt12 fade" ${st(3)} style="max-width:300px">I'll make sure you never miss what matters, and cheer when you show up.</p>
    <div class="card tight mt24 row g12 fade" ${st(4)} style="text-align:left">${icf('phone_android', 'yellow')}<span class="body-s muted">Everything lives on this phone. Uninstalling deletes it, so set up backups later.</span></div>
  </div>`, "Let's go"));
screen('onboarding-name', 'Onboarding & lock', 'Your name', () => obShell(2, `
  <div class="row g12 mt24 in">${mascot({ size: 64, mood: 'curious' })}<div class="card tight body-m" style="border-radius:20px 20px 20px 6px">What should I call you?</div></div>
  <div class="headline-m mt24 in" ${st(1)}>Your name</div>
  <div class="field focus mt16 in" ${st(2)}><span class="lbl">Name</span><span class="v">Sanjith<span class="caret"></span></span></div>
  <div class="headline-s mt24 in" ${st(3)}>Name your buddy</div>
  <div class="field mt16 in" ${st(4)}><span class="lbl">Mascot name</span><span class="v">Blip</span>${ic('casino', 'muted')}</div>
  <p class="body-s muted mt8 in" ${st(5)}>You can change both later in Settings.</p>`));
screen('onboarding-permissions', 'Onboarding & lock', 'Permission walkthrough', () => obShell(3, `
  <div class="headline-m mt8">Let reminders reach you</div>
  <p class="body-m muted mt8">Five switches decide whether a pop-up can appear over other apps. We'll fix them one by one.</p>
  <div class="card flat mt16">${[
    ['notifications_active', 'Notifications', 'g', 'Allowed'], ['alarm', 'Exact alarms', 'g', 'Allowed'],
    ['layers', 'Draw over other apps', 'r', 'Needed'], ['fullscreen', 'Full-screen pop-ups', 'y', 'Check'],
    ['battery_saver', 'Battery: unrestricted', 'r', 'Needed']].map(([i, t, s, w], k) => `
    <div class="lrow in" ${st(k)}><span class="lead c">${ic(i)}</span><span class="f1 title-s">${t}</span>
    <span class="status ${s}">${icf(s === 'g' ? 'check_circle' : s === 'y' ? 'error' : 'cancel')}${w}</span></div>`).join('')}</div>
  <div class="card tight mt12 row g12" style="border-style:dashed">${ic('smartphone', 'yellow')}<span class="body-s muted">Vivo: 4 more manual ticks (auto-start, background pop-ups, lock screen, lock in recents).</span></div>`, 'Fix all (2 left)', true));
screen('onboarding-habits', 'Onboarding & lock', 'Pick starter habits', () => obShell(4, `
  <div class="headline-m mt8">Start small</div><p class="body-m muted mt8">Pick the habits you want on your grid. You can add more anytime.</p>
  <div class="list mt16">${[['💊', 'Creatine', 'Health', '#FF6B5E', 1], ['🧘', 'Meditation', 'Self-care', '#7E8CFF', 1], ['🧴', 'Skincare routine', 'Self-care', '#FF8FB8', 1], ['💇', 'Haircare routine', 'Self-care', '#5EC4D6', 1], ['📖', 'Read 10 pages', 'Other', '#7BC67E', 0]].map(([e, n, c, col, on], k) => `
    <div class="card tight row g12 in" ${st(k)} style="${on ? 'border-color:' + col + '66' : ''}"><span class="avatar" style="background:${col}26">${e}</span>
    <div class="f1"><div class="title-m">${n}</div><div class="body-s muted">${c}</div></div><span class="tick round ${on ? 'on' : ''}" data-tick>${on ? ic('check') : ''}</span></div>`).join('')}</div>`));
screen('onboarding-sync', 'Onboarding & lock', 'Optional sync sign-in', () => obShell(5, `
  <div class="col center" style="align-items:center;margin-top:12px"><div class="pop" style="width:96px;height:96px;border-radius:32px;background:var(--cherry);display:grid;place-items:center">${icf('cloud_sync', 'xl yellow')}</div></div>
  <div class="headline-m mt24 tc">Sync to your cloud?</div><p class="body-m muted mt8 tc">Optional. The app works fully offline and signed out.</p>
  <div class="row g8 mt16"><div class="card tight f1"><div class="label-m yellow">SYNCS</div><div class="body-s mt4">Tasks · Projects · Events · Reminders · Habits · Habit logs · Pauses</div></div>
  <div class="card tight f1"><div class="label-m muted">STAYS ON PHONE</div><div class="body-s mt4">Notes · Journal · Body · Photos · Voice · XP · Vault</div></div></div>
  <div class="field mt24"><span class="lbl">Email</span><span class="v muted">you@example.com</span></div>
  <div class="field mt16"><span class="lbl">Password</span><span class="v muted">••••••••</span>${ic('visibility', 'muted')}</div>`, 'Sign in', true));
screen('lock', 'Onboarding & lock', 'App lock', () => device(`<div class="screen" style="align-items:center;justify-content:center;text-align:center;padding:0 32px">
  <div class="pop">${mascot({ size: 110, mood: 'sleepy' })}</div>
  <div class="headline-s mt16 fade">BetterLife is locked</div><p class="body-m muted mt8 fade">Use your fingerprint or screen lock.</p>
  <div class="pop" ${st(4)} style="margin-top:48px;width:84px;height:84px;border-radius:50%;border:2px solid var(--yellow);display:grid;place-items:center;box-shadow:0 0 0 10px rgba(224,168,0,.08),0 0 40px rgba(224,168,0,.25)">${ic('fingerprint', 'xl yellow')}</div>
  <a class="btn text mt24">Use PIN instead</a></div>`));

/* ============ TODAY (Prompt 17) ============ */
function todaySkeleton() {
  return `<div class="scroll pb-nav" style="padding-top:12px">
    <div class="row g16"><div class="f1"><div class="sk" style="width:60%;height:14px"></div><div class="sk mt12" style="width:85%;height:28px"></div></div><div class="sk c" style="width:72px;height:72px"></div></div>
    <div class="sk-card mt24 row g16"><div class="sk c" style="width:84px;height:84px"></div><div class="f1"><div class="sk" style="height:16px;width:70%"></div><div class="sk mt12" style="height:12px;width:90%"></div><div class="sk mt8" style="height:12px;width:50%"></div></div></div>
    <div class="row g8 mt16">${[1, 2, 3].map(() => `<div class="sk r f1" style="height:76px"></div>`).join('')}</div>
    <div class="sk mt24" style="width:30%;height:12px"></div>
    ${[1, 2, 3, 4].map(() => `<div class="sk-card mt8 row g12"><div class="sk" style="width:28px;height:28px"></div><div class="f1"><div class="sk" style="height:14px;width:75%"></div><div class="sk mt8" style="height:10px;width:40%"></div></div></div>`).join('')}
  </div>`;
}
function todayRow(i, { icon, title, meta, chip, top, ticked, color }) {
  return `<div class="card tight row g12 in ${top ? 'top' : ''}" ${st(i)}>
    ${icon ? `<span class="avatar" style="width:36px;height:36px;border-radius:12px;font-size:18px;background:${color || 'var(--cherry-soft)'}">${icon}</span>` : `<span class="tick ${ticked ? 'on' : ''}" data-tick>${ticked ? ic('check') : ''}</span>`}
    <div class="f1"><div class="title-m ell">${title}</div><div class="row g8 body-s muted mt4">${meta}</div></div>${chip || ''}</div>`;
}
function todayBody(extra = '') {
  return `<div class="scroll pb-nav" style="padding-top:8px">
    <div class="row g12 in">
      <div class="f1"><div class="body-m muted">Friday, 6 November</div><div class="headline-s bold" style="letter-spacing:-.3px">Good morning, Sanjith</div>
        <div class="row g8 mt8"><span class="status g">${icf('cloud_done')}Synced</span><span class="status n">${icf('local_fire_department')}Lv 7</span></div></div>
      <div style="margin:-6px -6px 0 0">${mascot({ size: 82, mood: 'cheering', stage: 2 })}</div></div>
    ${extra}
    <div class="card hero mt16 row g16 in" ${st(1)}>${ring(62, 88, 9, 'var(--yellow)', `<div class="title-l bold">62%</div><div class="label-s muted">today</div>`)}
      <div class="f1"><div class="row g8"><span class="title-l bold">84%</span><span class="body-s muted">consistency</span></div>
      <div class="row g4 mt4">${icf('local_fire_department', 'md yellow')}<span class="title-s">37-day streak</span></div>
      <div class="body-s muted mt8">3 of 5 tasks · 2 of 4 habits done</div></div></div>
    <div class="row g8 mt12">${[['add_task', 'Task'], ['sticky_note_2', 'Note'], ['mic', 'Voice']].map(([i, l], k) => `
      <a class="card tight f1 col g4 in" ${st(2 + k)} style="align-items:center;padding:12px 4px">${ic(i, 'yellow')}<span class="label-m">${l}</span></a>`).join('')}
      <a class="card tight col g4 in" ${st(5)} style="align-items:center;padding:12px 10px;background:var(--cherry);border-color:transparent">${ic('self_improvement')}<span class="label-m">Bad day?</span></a></div>
    <div class="section-h"><span class="title-s">Right now</span><a>See all</a></div>
    <div class="list">
      ${todayRow(0, { icon: '⏰', title: 'Pay electricity bill', meta: `<span class="lvl full" style="height:20px;font-size:10px">${icf('fullscreen', 'sm')}POP-UP</span><span>Due 9:00 AM</span>`, top: true })}
      ${todayRow(1, { title: 'Submit lab report', meta: `<span class="bad row g4">${icf('flag', 'sm')}High</span><span class="bad">Overdue · yesterday</span>` })}
      ${todayRow(2, { icon: '👥', title: 'Design sync with Arya', color: 'rgba(126,140,255,.2)', meta: `<span>10:30 – 11:15 AM · in 2 h</span>`, chip: `<span class="status y">${icf('warning')}Clash</span>` })}
      ${todayRow(3, { title: '2:00 PM, 2 items', meta: `<span>Gym · Call mom</span>`, chip: ic('expand_more', 'muted') })}
      ${todayRow(4, { title: 'Meditation', ticked: false, meta: `<span style="color:#7E8CFF">● Self-care</span><span>12-day streak</span>` })}
    </div></div>`;
}
screen('today', 'Today', 'Today (home)', s => device(`<div class="screen">${s === 'loading' ? todaySkeleton() : todayBody()}</div>`, { nav: 'today', navOpts: { badge: 4, anim: true } }));
screen('today-loading', 'Today', 'Today: skeleton loading', () => device(`<div class="screen">${todaySkeleton()}</div>`, { nav: 'today' }));
screen('today-paused', 'Today', 'Today: paused banner + undo', () => device(`<div class="screen">${todayBody(`
  <div class="card tight row g12 mt16 in" style="background:linear-gradient(90deg,rgba(126,140,255,.18),var(--surface));border-color:rgba(126,140,255,.4)">${icf('do_not_disturb_on', 'md')}<div class="f1"><div class="title-s">Paused until 6:00 PM</div><div class="body-s muted">2 reminders held · they'll arrive as one digest</div></div><a class="btn sm outline">Resume</a></div>`)}
  ${snackbar('Meditation ticked for today')}</div>`, { nav: 'today' }));
screen('today-pause-sheet', 'Today', 'Pause sheet ("I am busy")', () => device(`<div class="screen">${todayBody()}</div>
  <div class="scrim anim soft"></div><div class="sheet anim"><div class="grab"></div>
  <div class="row g12"><span class="lead" style="width:44px;height:44px;border-radius:14px;display:grid;place-items:center;background:var(--cherry)">${icf('do_not_disturb_on', 'yellow')}</span><div><div class="title-l">I'm busy</div><div class="body-s muted">Hold task and habit reminders for a while</div></div></div>
  <div class="row wrap g8 mt24">${['30 min', '1 hour', '3 hours', 'Until tonight'].map((t, i) => `<span class="chip pill pop ${i === 1 ? 'sel' : ''}" ${st(i)} style="height:44px;padding:0 18px">${t}</span>`).join('')}</div>
  <div class="card tight mt24 row g12">${ic('event', 'muted')}<span class="f1 body-m">Also hold calendar events & birthdays</span><span class="switch"></span></div>
  <div class="card tight mt8 row g12">${ic('alarm', 'muted')}<span class="f1 body-m">Also hold ALARM level</span><span class="switch"></span></div>
  <a class="btn primary block mt24">Pause for 1 hour</a></div>`, { nav: 'today' }));
screen('today-minimum', 'Today', 'Minimum Day', () => device(`<div class="screen enter-up" style="background:radial-gradient(100% 60% at 50% 0%,#3a1219,var(--bg))">
  ${appbar('', { actions: ['close'] })}
  <div class="scroll"><div class="col" style="align-items:center;text-align:center">
    <div class="pop">${mascot({ size: 130, mood: 'proud' })}</div>
    <div class="headline-m bold mt8 fade">Just these three.</div><p class="body-m muted mt8 fade">That's enough for today. Finishing them keeps every streak alive.</p></div>
    <div class="list mt24">${[['task_alt', 'Most important task', 'Submit lab report', 1, '#FF6B5E'], ['self_improvement', 'Essential habit', 'Creatine', 1, '#FF6B5E'], ['spa', 'A bit of self-care', 'Skincare routine', 0, '#FF8FB8']].map(([i, k, t, on, c], n) => `
      <div class="card row g12 in" ${st(n)} style="${on ? 'border-color:rgba(224,168,0,.5)' : ''}"><span class="avatar" style="background:${c}26;color:${c}">${icf(i)}</span>
      <div class="f1"><div class="label-m muted">${k.toUpperCase()}</div><div class="title-m ${on ? 'muted' : ''}" style="${on ? 'text-decoration:line-through' : ''}">${t}</div></div>
      <span class="tick round ${on ? 'on pop' : ''}" data-tick>${on ? ic('check') : ''}</span></div>`).join('')}</div>
    <div class="mt24"><div class="row between body-s muted"><span>2 of 3</span><span>Counts as a KEPT day</span></div><div class="bar mt8"><i style="width:66%"></i></div></div>
  </div></div>`));
screen('today-comeback', 'Today', 'Comeback mission', () => device(`<div class="screen enter-up" style="background:radial-gradient(90% 55% at 50% 10%,#4a1a10,var(--bg))">
  ${appbar('', { actions: ['close'] })}
  <div class="scroll"><div class="col" style="align-items:center;text-align:center">
    <div style="position:relative">${confetti(14).replace('z-index:85', 'z-index:0')}<div class="pop">${mascot({ size: 140, mood: 'cheering', stage: 2 })}</div></div>
    <span class="chip y pill mt8 pop" ${st(3)}>${icf('bolt')}Comeback · 3× XP</span>
    <div class="headline-m bold mt12 fade">Welcome back!</div><p class="body-m muted mt8 fade" style="max-width:300px">Fresh start. Three small wins and we're rolling again.</p></div>
    <div class="list mt24">${[['💊', 'Creatine', 'Habit'], ['🧘', 'Meditation · 5 min', 'Habit'], ['📝', 'Submit lab report', 'Important task']].map(([e, t, k], n) => `
      <div class="card row g12 in" ${st(n + 2)}><span class="avatar" style="background:var(--cherry-soft)">${e}</span><div class="f1"><div class="title-m">${t}</div><div class="body-s muted">${k}</div></div><span class="tick round" data-tick></span></div>`).join('')}</div>
    <a class="btn primary block mt24 in" ${st(6)}>Start mission</a></div></div>`));

/* ============ TASKS (Prompt 9) ============ */
function taskRow(i, { title, pr, due, dueBad, proj, rep, sub, top, done }) {
  const prc = { High: 'bad', Medium: 'yellow', Low: 'good' }[pr];
  return `<div class="card tight row g12 in ${top ? 'top' : ''}" ${st(i)} style="padding:12px 4px 12px 12px">
    <span class="tick ${done ? 'on' : ''}" data-tick>${done ? ic('check') : ''}</span>
    <div class="f1"><div class="title-m ell" style="${done ? 'text-decoration:line-through;color:var(--muted)' : ''}">${title}</div>
    <div class="row g8 body-s muted mt4 wrap">${top ? '<span class="yellow label-m">★ TOP 3</span>' : ''}${pr ? `<span class="${prc} row g4">${icf('flag', 'sm')}${pr}</span>` : ''}
    ${due ? `<span class="${dueBad ? 'bad' : ''}">${due}</span>` : ''}${rep ? ic('repeat', 'sm') : ''}${proj ? `<span>@${proj}</span>` : ''}${sub ? `<span class="row g4">${ic('checklist', 'sm')}${sub}</span>` : ''}</div></div>
    <span class="iconbtn" style="width:40px;height:40px">${ic('delete', 'md muted')}</span></div>`;
}
function quickAdd({ text = 'call mom tomorrow 5pm !high #family', chips = true, warn, ask } = {}) {
  return `<div style="position:absolute;left:12px;right:12px;bottom:104px;z-index:30" class="in" ${st(5)}>
    ${chips ? `<div class="card raised tight" style="border-radius:20px 20px 8px 8px;margin-bottom:4px;padding:10px">
      <div class="label-s muted mb8">PREVIEW</div><div class="row wrap g8">
      <span class="chip pop">${ic('title')}call mom</span><span class="chip y pop" ${st(1)}>${ic('event')}Sat 7 Nov</span><span class="chip y pop" ${st(2)}>${ic('schedule')}5:00 PM</span>
      <span class="chip pop" ${st(3)} style="color:var(--bad);border-color:rgba(255,107,94,.5)">${icf('flag')}High</span><span class="chip pop" ${st(4)}>${ic('tag')}family</span></div>
      ${warn ? `<div class="chip warn mt8" style="width:100%">${ic('info')}${warn}</div>` : ''}
      ${ask ? `<div class="row g8 mt8"><span class="body-m f1">${ask}</span><span class="chip sel pill">AM</span><span class="chip pill">PM</span></div>` : ''}</div>` : ''}
    <div class="row g8 card raised" style="border-radius:${chips ? '8px 8px 22px 22px' : '22px'};padding:6px 6px 6px 16px;border-color:rgba(224,168,0,.45)">
      ${ic('add', 'yellow')}<span class="f1 body-l">${text}<span class="caret"></span></span><span class="iconbtn" style="background:var(--yellow);color:var(--bg);width:44px;height:44px">${icf('arrow_upward')}</span></div></div>`;
}
function tasksScreen(state) {
  const loading = state === 'loading';
  const tabs = `<div class="tabs mt4">${[['Today', 5], ['Upcoming', 12], ['Overdue', 2], ['Inbox', 4]].map(([t, n], i) => `<span class="tab ${i === 0 ? 'on' : ''}">${t}<span class="n">${n}</span></span>`).join('')}</div>`;
  const body = loading
    ? [1, 2, 3, 4, 5].map(() => `<div class="sk-card row g12"><div class="sk" style="width:28px;height:28px"></div><div class="f1"><div class="sk" style="height:14px;width:70%"></div><div class="sk mt8" style="height:10px;width:45%"></div></div></div>`).join('')
    : `${taskRow(0, { title: 'Submit lab report', pr: 'High', due: '11:59 PM', proj: 'Uni', sub: '2/4', top: true })}
       ${taskRow(1, { title: 'Pay electricity bill', pr: 'Medium', due: '9:00 AM', rep: 1, top: true })}
       ${taskRow(2, { title: 'Book dentist appointment', pr: 'Low', due: 'Today', top: true })}
       ${taskRow(3, { title: 'Reply to Arya about the poster', due: '3:00 PM', proj: 'Club' })}
       ${taskRow(4, { title: 'Water the plants', due: 'Today', rep: 1, done: true })}`;
  return device(`<div class="screen">${appbar('Tasks', { back: false, actions: ['folder_open', 'search'] })}${tabs}
    <div class="scroll pb-nav" style="padding-top:12px"><div class="list">${body}</div></div>
    ${loading ? '' : quickAdd(state === 'ambiguous' ? { text: 'gym at 5', ask: '"at 5": morning or evening?' } : state === 'typing' ? { warn: '5 PM already passed today, so this is tomorrow' } : { chips: false, text: '<span class="muted">Add a task… "gym 7am !high"</span>' })}
    </div>`, { nav: 'tasks' });
}
screen('tasks', 'Tasks', 'Tasks: Today tab', () => tasksScreen(''));
screen('tasks-loading', 'Tasks', 'Tasks: skeleton loading', () => tasksScreen('loading'));
screen('tasks-quickadd', 'Tasks', 'Quick-add live preview', () => tasksScreen('typing'));
screen('tasks-ambiguous', 'Tasks', 'Quick-add AM/PM question', () => tasksScreen('ambiguous'));
screen('task-editor', 'Tasks', 'Task editor', () => device(`<div class="screen enter">
  <div class="appbar"><span class="iconbtn" style="margin-left:-8px">${ic('close')}</span><span class="t">Edit task</span><a class="btn primary sm">Save</a></div>
  <div class="scroll" style="padding-top:8px">
    <div class="field focus"><span class="lbl">Title</span><span class="v">Submit lab report<span class="caret"></span></span></div>
    <div class="field mt16" style="align-items:flex-start;min-height:72px"><span class="lbl">Notes</span><span class="v body-m muted">Upload PDF to the portal, cc Prof. Rao</span></div>
    <div class="row g8 mt16"><div class="field f1"><span class="lbl">Due date</span>${ic('event', 'muted')}<span class="v">Fri 6 Nov</span></div><div class="field f1"><span class="lbl">Time</span>${ic('schedule', 'muted')}<span class="v">11:59 PM</span></div></div>
    <div class="label-m muted mt16 mb8">PRIORITY</div>
    <div class="row g8">${[['None', ''], ['Low', 'good'], ['Medium', 'yellow'], ['High', 'bad']].map(([p, c]) => `<span class="chip f1 center ${p === 'High' ? 'sel' : ''}"><span class="${c}">${icf('flag', 'sm')}</span>${p}</span>`).join('')}</div>
    <div class="card flat mt16">
      <div class="lrow"><span class="lead">${ic('folder')}</span><span class="f1">Project</span><span class="muted">Uni</span>${ic('chevron_right', 'muted')}</div>
      <div class="lrow"><span class="lead">${ic('repeat')}</span><div class="f1"><div>Repeat</div><div class="body-s muted">A repeating task makes new tasks. Tracking a daily routine? Use a habit.</div></div><span class="muted">Never</span></div>
      <div class="lrow"><span class="lead">${ic('fullscreen')}</span><span class="f1">Importance</span><span class="lvl full">${icf('fullscreen', 'sm')}POP-UP</span></div></div>
    <div class="label-m muted mt16 mb8">REMINDERS</div>
    <div class="list">${[['1 day before', 'Notification', 'normal'], ['At time', 'Pop-up', 'full']].map(([o, l, c]) => `<div class="card tight row g12">${ic('notifications', 'yellow')}<span class="f1 title-s">${o}</span><span class="lvl ${c}">${l}</span>${ic('close', 'muted')}</div>`).join('')}
    <div class="row g8 wrap">${['5 min', '30 min', '1 hour', '1 week', 'Custom…'].map(t => `<span class="chip">${ic('add', 'sm')}${t}</span>`).join('')}</div></div>
    <div class="label-m muted mt16 mb8">SUBTASKS · 2/4</div>
    <div class="card flat">${[['Write conclusion', 1], ['Plot graphs', 1], ['Proofread', 0], ['Upload', 0]].map(([t, d]) => `<div class="lrow" style="min-height:48px"><span class="tick ${d ? 'on' : ''}" style="width:22px;height:22px;border-radius:7px">${d ? ic('check', 'sm') : ''}</span><span class="f1 ${d ? 'muted' : ''}" style="${d ? 'text-decoration:line-through' : ''}">${t}</span></div>`).join('')}</div>
    <div style="height:40px"></div></div></div>`));
screen('projects', 'Tasks', 'Projects', () => device(`<div class="screen enter">${appbar('Projects', { actions: ['add'] })}
  <div class="scroll"><div class="list">${[['Uni', 7, 12, '#7E8CFF', 'school'], ['Club', 3, 9, '#FF8FB8', 'groups'], ['Home', 5, 6, '#7BC67E', 'home'], ['Side project', 1, 10, '#5EC4D6', 'rocket_launch']].map(([n, d, t, c, i], k) => `
    <div class="card in" ${st(k)}><div class="row g12"><span class="avatar" style="background:${c}26;color:${c}">${icf(i)}</span><div class="f1"><div class="title-m">${n}</div><div class="body-s muted">${d} of ${t} done</div></div>${ring(Math.round(d / t * 100), 48, 5, c, `<span class="label-s">${Math.round(d / t * 100)}%</span>`)}</div>
    <div class="bar mt12"><i style="width:${d / t * 100}%;background:${c}"></i></div></div>`).join('')}</div></div></div>`, { nav: 'tasks' }));
screen('checkin', 'Tasks', 'Morning check-in (top 3)', () => device(`<div class="screen enter-up" style="background:radial-gradient(100% 50% at 80% 0%,#4a2a0a,var(--bg) 70%)">
  ${appbar('', { actions: ['close'] })}<div class="scroll">
  <div class="row g12">${mascot({ size: 72, mood: 'cheering' })}<div><div class="label-m yellow">MORNING CHECK-IN</div><div class="headline-s bold">Pick today's top 3</div></div></div>
  <div class="row g8 mt16">${[1, 2, 3].map(n => `<div class="f1 card tight tc pop" ${st(n)} style="${n < 3 ? 'border-color:var(--yellow);background:var(--yellow-soft)' : 'border-style:dashed'}"><div class="title-l bold ${n < 3 ? 'yellow' : 'faint'}">${n}</div><div class="body-s ${n < 3 ? '' : 'faint'} ell">${n === 1 ? 'Lab report' : n === 2 ? 'Electricity' : 'Pick one'}</div></div>`).join('')}</div>
  <div class="list mt16">${[['Submit lab report', 'High', 1], ['Pay electricity bill', 'Medium', 2], ['Book dentist appointment', 'Low', 0], ['Reply to Arya', '', 0], ['Clean desk', '', 0]].map(([t, p, n], k) => `
    <div class="card tight row g12 in ${n ? 'top' : ''}" ${st(k)}><span class="tick round ${n ? 'on' : ''}" style="${n ? '' : ''}">${n ? `<b style="font-size:13px">${n}</b>` : ''}</span><span class="f1 title-m">${t}</span>${p ? `<span class="body-s ${p === 'High' ? 'bad' : p === 'Medium' ? 'yellow' : 'good'} row g4">${icf('flag', 'sm')}${p}</span>` : ''}</div>`).join('')}</div>
  <a class="btn primary block mt24">Lock in my day</a></div></div>`));

/* ============ HABITS (Prompts 14-16) ============ */
const HABITS = [
  { e: '💊', n: 'Creatine', c: '#FF6B5E', cat: 'Health', ess: 1, cons: 92, streak: 37, seed: 3 },
  { e: '🧘', n: 'Meditation', c: '#7E8CFF', cat: 'Self-care', cons: 76, streak: 12, seed: 5 },
  { e: '🧴', n: 'Skincare routine', c: '#FF8FB8', cat: 'Self-care', cons: 81, streak: 0, best: 21, seed: 7 },
  { e: '💇', n: 'Haircare routine', c: '#5EC4D6', cat: 'Self-care', cons: null, streak: 4, seed: 2, start: 2 },
];
function habitGrid(h, { today = 6, days = 30, tickAnim } = {}) {
  let s = '';
  for (let d = 1; d <= 33; d++) {
    if (d > days) { s += `<span class="hbox void"></span>`; continue; }
    if (h.start && d < h.start) { s += `<span class="hbox void"></span>`; continue; }
    if (d > today) { s += `<span class="hbox future"></span>`; continue; }
    const done = h.streak > 0 && d > today - h.streak ? 1 : ((d * h.seed) % 7 > 1 ? 1 : 0);
    const kept = d === 3 && h.seed === 5;
    const paused = d === 2 && h.seed === 3;
    const isT = d === today;
    if (paused) s += `<span class="hbox paused"></span>`;
    else if (kept) s += `<span class="hbox done kept" style="background:${h.c}99" data-c="${h.c}">${icf('check')}</span>`;
    else if (done && !(isT && h.seed === 5)) s += `<span class="hbox done ${isT ? 'today' : ''} ${isT && tickAnim ? 'popin' : ''}" style="background:${h.c}" data-c="${h.c}">${icf('check')}</span>`;
    else s += `<span class="hbox ${isT ? 'today' : ''}" data-c="${h.c}"></span>`;
  }
  return `<div class="hgrid">${s}</div>`;
}
function habitCard(h, i) {
  const streak = h.streak ? `${icf('local_fire_department', 'sm yellow')}<span>${h.streak}-day streak</span>` : `${ic('restart_alt', 'sm')}<span>Best: ${h.best}. Restarting.</span>`;
  return `<div class="card in" ${st(i)}><div class="row g12 mb16"><span class="avatar" style="background:${h.c}26">${h.e}</span>
    <div class="f1"><div class="row g8"><span class="title-m">${h.n}</span>${h.ess ? '<span class="tag" style="color:var(--yellow)">ESSENTIAL</span>' : ''}</div>
    <div class="row g4 body-s muted mt4">${streak}</div></div>
    <div class="tc"><div class="title-l bold" style="color:${h.cons === null ? 'var(--muted)' : 'var(--text)'}">${h.cons === null ? 'New' : h.cons + '%'}</div><div class="label-s muted">consistency</div></div></div>
    ${habitGrid(h, { tickAnim: i === 1 })}</div>`;
}
screen('habits', 'Habits', 'Habit grids (month)', s => device(`<div class="screen">${appbar('Habits', { back: false, actions: ['insights', 'add'] })}
  <div class="row between" style="padding:0 16px 8px"><span class="iconbtn tonal">${ic('chevron_left')}</span><div class="tc"><div class="title-m">November 2026</div><div class="body-s muted">2 of 4 done today</div></div><span class="iconbtn tonal" style="opacity:.4">${ic('chevron_right')}</span></div>
  <div class="scroll pb-nav"><div class="list">${s === 'loading'
    ? [1, 2, 3].map(() => `<div class="sk-card"><div class="row g12"><div class="sk" style="width:44px;height:44px;border-radius:16px"></div><div class="f1"><div class="sk" style="height:14px;width:50%"></div><div class="sk mt8" style="height:10px;width:35%"></div></div><div class="sk" style="width:48px;height:28px"></div></div><div class="hgrid mt16">${Array(33).fill('<span class="sk" style="aspect-ratio:1;border-radius:7px"></span>').join('')}</div></div>`).join('')
    : HABITS.map(habitCard).join('')}</div></div>
  ${s === 'loading' ? '' : snackbar('Meditation · 6 Nov ticked')}</div>`, { nav: 'habits' }));
screen('habits-loading', 'Habits', 'Habit grids: skeleton', () => SCREENS.habits.render('loading'));
function heatmap(c) {
  let cells = '';
  for (let w = 0; w < 26; w++) for (let d = 0; d < 7; d++) {
    const v = ((w * 7 + d) * 2654435761 % 100) / 100; const lvl = w > 23 && d > 3 ? -1 : v > .82 ? 0 : v > .25 ? 1 : 0.5;
    cells += `<rect x="${w * 13}" y="${d * 13}" width="10.5" height="10.5" rx="3" fill="${lvl < 0 ? 'transparent' : lvl === 0 ? 'rgba(245,233,224,.07)' : c}" opacity="${lvl === .5 ? .45 : 1}"/>`;
  }
  return `<svg width="100%" viewBox="0 0 338 91">${cells}</svg>`;
}
screen('habit-detail', 'Habits', 'Habit detail + yearly heatmap', () => {
  const h = HABITS[0];
  return device(`<div class="screen enter">${appbar('', { actions: ['edit', 'more_vert'] })}<div class="scroll">
    <div class="row g16 in"><span class="avatar" style="width:64px;height:64px;font-size:32px;border-radius:22px;background:${h.c}26">${h.e}</span><div><div class="headline-s bold">Creatine</div><div class="body-s muted">Health · Essential · reminder 8:00 AM</div></div></div>
    <div class="row g8 mt16">${[['92%', 'consistency', '30 days'], ['37', 'streak', 'days'], ['41', 'best', 'days']].map(([v, l, s], i) => `<div class="card tight f1 tc in" ${st(i + 1)}><div class="title-l bold ${i === 1 ? 'yellow' : ''}">${v}</div><div class="label-s muted">${l.toUpperCase()}</div></div>`).join('')}</div>
    <div class="section-h"><span class="title-s">Last 6 months</span><span class="body-s muted">2026</span></div>
    <div class="card in" ${st(4)}>${heatmap(h.c)}<div class="row between body-s faint mt8"><span>Jun</span><span>Jul</span><span>Aug</span><span>Sep</span><span>Oct</span><span>Nov</span></div>
    <div class="row g8 body-s muted mt12"><span>Less</span>${[.07, .45, 1].map(o => `<i style="width:11px;height:11px;border-radius:3px;background:${o < .1 ? 'rgba(245,233,224,.07)' : h.c};opacity:${o < .1 ? 1 : o};display:block"></i>`).join('')}<span>More</span><span class="f1"></span><span class="row g4"><i class="hbox paused" style="width:11px;display:block"></i>Paused</span></div></div>
    <div class="section-h"><span class="title-s">Milestones</span></div>
    <div class="row g8">${[7, 10, 15, 30].map((m, i) => `<div class="card tight f1 tc pop" ${st(i)} style="border-color:rgba(224,168,0,.45);background:var(--yellow-soft)">${icf('military_tech', 'yellow')}<div class="title-s">${m} days</div></div>`).join('')}</div>
    <div class="section-h"><span class="title-s">This month</span></div><div class="card">${habitGrid(h)}</div><div style="height:30px"></div></div></div>`);
});
screen('habit-editor', 'Habits', 'Create / edit habit', () => device(`<div class="screen enter-up">
  <div class="appbar"><span class="iconbtn" style="margin-left:-8px">${ic('close')}</span><span class="t">New habit</span><a class="btn primary sm">Create</a></div>
  <div class="scroll"><div class="col" style="align-items:center"><span class="avatar pop" style="width:88px;height:88px;font-size:44px;border-radius:30px;background:#E0A80033;border:2px solid #E0A800">📖</span></div>
  <div class="field focus mt24"><span class="lbl">Name</span><span class="v">Read 10 pages<span class="caret"></span></span></div>
  <div class="label-m muted mt24 mb8">COLOR</div><div class="row g8 wrap">${['#FF6B5E', '#E0A800', '#7BC67E', '#5EC4D6', '#7E8CFF', '#C77DFF', '#FF8FB8', '#B0A49C'].map((c, i) => `<span class="pop" ${st(i)} style="width:38px;height:38px;border-radius:50%;background:${c};display:grid;place-items:center;${i === 1 ? 'box-shadow:0 0 0 3px var(--bg),0 0 0 5px ' + c : ''}">${i === 1 ? icf('check', 'md') : ''}</span>`).join('')}</div>
  <div class="label-m muted mt24 mb8">ICON</div><div class="row g8 wrap">${['📖', '💊', '🧘', '🧴', '🏃', '💧', '🎸', '🥗', '😴', '✍️'].map((e, i) => `<span style="width:44px;height:44px;border-radius:14px;display:grid;place-items:center;font-size:22px;background:${i ? 'var(--surface)' : '#E0A80033'};border:1px solid ${i ? 'var(--border)' : 'var(--yellow)'}">${e}</span>`).join('')}</div>
  <div class="label-m muted mt24 mb8">CATEGORY</div><div class="row g8">${['Health', 'Self-care', 'Other'].map((c, i) => `<span class="chip pill f1 center ${i === 2 ? 'sel' : ''}">${c}</span>`).join('')}</div>
  <div class="card flat mt24"><div class="lrow"><span class="lead">${ic('star')}</span><div class="f1"><div>Essential</div><div class="body-s muted">Picked first on a Minimum Day</div></div><span class="switch on"></span></div>
  <div class="lrow"><span class="lead">${ic('notifications')}</span><span class="f1">Daily reminder</span><span class="yellow">9:30 PM</span></div></div></div></div>`));
screen('celebration', 'Habits', 'Milestone celebration', () => device(`<div class="screen" style="background:radial-gradient(80% 50% at 50% 40%,#5a1823,var(--bg));align-items:center;justify-content:center;text-align:center;padding:0 28px">
  ${confetti()}<div style="position:relative;z-index:86" class="col center">
  <div style="position:relative;display:grid;place-items:center"><div style="position:absolute;width:260px;height:260px;border-radius:50%;background:radial-gradient(circle,rgba(224,168,0,.35),transparent 65%)" class="pop"></div>
  <div class="pop">${mascot({ size: 180, mood: 'cheering', stage: 2 })}</div></div>
  <div class="pop" ${st(4)} style="font-size:84px;line-height:1;font-weight:900;color:var(--yellow);letter-spacing:-3px;margin-top:8px;text-shadow:0 8px 40px rgba(224,168,0,.5)">30</div>
  <div class="headline-s bold fade" ${st(6)}>days of Creatine!</div>
  <p class="body-l muted mt12 fade" ${st(8)}>"Future you is already flexing. Keep stacking."</p>
  <div class="row g8 mt24 fade" ${st(10)}><span class="chip y pill">${icf('bolt')}+150 XP</span><span class="chip pill">${icf('military_tech')}30-day badge</span></div>
  <a class="btn primary mt24 fade" ${st(12)} style="width:240px">Let's keep going</a></div></div>`));
screen('habits-past', 'Habits', 'Habit grids: a full past month', () => device(`<div class="screen">${appbar('Habits', { back: false, actions: ['insights', 'add'] })}
  <div class="row between" style="padding:0 16px 8px"><span class="iconbtn tonal">${ic('chevron_left')}</span><div class="tc"><div class="title-m">October 2026</div><a class="chip y pill" style="height:24px;margin-top:2px">${ic('today', 'sm')}Back to this month</a></div><span class="iconbtn tonal">${ic('chevron_right')}</span></div>
  <div class="scroll pb-nav"><div class="list">${HABITS.map((h, i) => {
    const past = { ...h, streak: 0, seed: h.seed + 1, start: h.start ? 9 : 0 };
    const head = `<div class="row g12 mb16"><span class="avatar" style="background:${h.c}26">${h.e}</span><div class="f1"><div class="title-m">${h.n}</div><div class="body-s muted">${[27, 22, 25, 14][i]} of 31 days</div></div></div>`;
    return `<div class="card in" ${st(i)}>${head}${habitGrid(past, { today: 32, days: 31 })}</div>`;
  }).join('')}</div></div></div>`, { nav: 'habits' }));
