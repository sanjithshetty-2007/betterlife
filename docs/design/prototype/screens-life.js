// Second brain (21-23), body (24-25), rewards (26-27), More/settings/safety (27-29, S1-S2), widgets (19-20), states.
'use strict';

/* ============ MORE HUB ============ */
screen('more', 'More & settings', 'More (hub)', () => device(`<div class="screen">${appbar('More', { back: false, actions: ['search'] })}
  <div class="scroll pb-nav"><div class="card cherry row g16 in">${mascot({ size: 70, mood: 'proud', stage: 3 })}<div class="f1"><div class="title-m">Blip · Teen</div><div class="body-s muted">Level 7 · 1,240 XP</div><div class="bar mt8"><i style="width:64%"></i></div><div class="body-s muted mt4">260 XP to level 8</div></div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px" class="mt12">${[['inbox', 'Inbox', 4], ['sticky_note_2', 'Notes'], ['auto_stories', 'Journal'], ['monitor_weight', 'Body'], ['photo_library', 'Photos'], ['insights', 'Dashboard'], ['notifications_active', 'Reminders'], ['health_and_safety', 'Health', '!'], ['lock', 'Vault']].map(([i, t, b], k) => `
    <a class="card tight col g8 pop" ${st(k)} style="align-items:center;padding:16px 4px;position:relative">${ic(i, 'yellow')}<span class="label-m">${t}</span>${b ? `<span class="badge-dot" style="top:8px;right:10px;${b === '!' ? '' : 'background:var(--yellow)'}">${b}</span>` : ''}</a>`).join('')}</div>
  <div class="card flat mt12">${[['delete', 'Trash', '3 items'], ['backup', 'Backup & restore', 'Today 3:00 AM'], ['cloud_sync', 'Account & sync', 'Synced'], ['settings', 'Settings', '']].map(([i, t, s]) => `<div class="lrow"><span class="lead">${ic(i)}</span><span class="f1">${t}</span><span class="body-s muted">${s}</span>${ic('chevron_right', 'muted')}</div>`).join('')}</div></div></div>`, { nav: 'more' }));

/* ============ SECOND BRAIN ============ */
screen('notes', 'Second brain', 'Notes', s => device(`<div class="screen">${appbar('Notes', { actions: ['search', 'grid_view'] })}
  <div class="chips" style="padding:0 16px 12px">${['All', '#ideas', '#uni', '#recipes', '#books'].map((c, i) => `<span class="chip pill ${i === 0 ? 'sel' : ''}">${c}</span>`).join('')}</div>
  <div class="scroll">${s === 'empty' ? `<div class="col center" style="align-items:center;text-align:center;height:70%">${mascot({ size: 120, mood: 'curious' })}<div class="title-l mt16">No notes yet</div><p class="body-m muted mt8">Ideas, lists, recipes… capture the first one.</p><a class="btn primary mt24">${ic('add')}New note</a></div>`
  : `<div style="columns:2;column-gap:8px">${[
    ['📌 Poster ideas', 'Neon type, cherry bg, mascot peeking from the corner…', '#ideas', '#FF8FB8', 120],
    ['Groceries', '☑ Oats\n☑ Eggs\n☐ Greek yogurt\n☐ Spinach', '', '#7BC67E', 140],
    ['Optics formulas', 'Snell: n₁ sin θ₁ = n₂ sin θ₂ · lens: 1/f = 1/v − 1/u', '#uni', '#7E8CFF', 110],
    ['Books 2026', 'Atomic Habits ✓, Deep Work, Project Hail Mary', '#books', '#E0A800', 90],
    ['Paneer tikka', 'Marinate 2h: curd, chilli, kasuri methi…', '#recipes', '#FF6B5E', 150],
    ['🎙 Voice · song idea', '0:42', '', '#C77DFF', 70]].map(([t, b, tag, c, h], k) => `
    <div class="card tight in" ${st(k)} style="break-inside:avoid;margin-bottom:8px;border-top:3px solid ${c}"><div class="title-s">${t}</div><div class="body-s muted mt4" style="white-space:pre-line;min-height:${h - 70}px">${b}</div>${tag ? `<span class="tag mt8" style="display:inline-block">${tag}</span>` : ''}</div>`).join('')}</div>`}</div>
  <div class="fab ext pop" ${st(5)} style="bottom:40px">${ic('edit')}New note</div></div>`));
screen('notes-empty', 'Second brain', 'Empty state (mascot)', () => SCREENS.notes.render('empty'));
screen('note-editor', 'Second brain', 'Note editor', () => device(`<div class="screen enter">
  <div class="appbar"><a class="iconbtn" style="margin-left:-8px">${ic('arrow_back')}</a><span class="t"></span><span class="iconbtn">${icf('push_pin', 'yellow')}</span><span class="iconbtn">${ic('link')}</span><span class="iconbtn">${ic('more_vert')}</span></div>
  <div class="scroll"><div class="headline-s bold">Poster ideas<span class="caret"></span></div><div class="row g8 mt8"><span class="tag">#ideas</span><span class="tag">#club</span><span class="tag">+ tag</span></div>
  <p class="body-l mt16" style="opacity:.92">Neon type on cherry background, mascot peeking from the corner. Keep it bold and readable from 5 m away.</p>
  <div class="mt16 col g8">${[['Pick 2 fonts', 1], ['Draft 3 layouts', 1], ['Get feedback from Arya', 0]].map(([t, d]) => `<div class="row g12"><span class="tick ${d ? 'on' : ''}" style="width:22px;height:22px;border-radius:7px">${d ? ic('check', 'sm') : ''}</span><span class="body-l ${d ? 'muted' : ''}" style="${d ? 'text-decoration:line-through' : ''}">${t}</span></div>`).join('')}</div>
  <div class="row g8 mt16"><div style="flex:1;height:120px;border-radius:18px;background:linear-gradient(135deg,#7A1F2B,#E0A800)"></div><div style="flex:1;height:120px;border-radius:18px;background:linear-gradient(135deg,#1A0A0D,#C77DFF)"></div></div>
  <div class="section-h"><span class="title-s">Related</span></div><div class="list">${[['task_alt', 'Club poster due', 'Linked task'], ['event', 'Club meeting · Thu', 'Same project']].map(([i, t, m]) => `<div class="card tight row g12">${ic(i, 'yellow')}<div class="f1"><div class="title-s">${t}</div><div class="body-s muted">${m}</div></div>${ic('chevron_right', 'muted')}</div>`).join('')}</div></div>
  <div style="position:absolute;left:12px;right:12px;bottom:28px" class="card raised row between" style="padding:6px">${['format_bold', 'format_list_bulleted', 'checklist', 'image', 'mic', 'tag'].map(i => `<span class="iconbtn">${ic(i)}</span>`).join('')}</div></div>`));
screen('inbox', 'Second brain', 'Inbox (process captures)', () => device(`<div class="screen enter">${appbar('Inbox', { sub: '4 to process', actions: ['done_all'] })}
  <div class="scroll"><div class="list">${[
    ['link', 'How to design a habit tracker', 'medium.com · shared from Chrome', 1],
    ['image', '2 images', 'Shared from Instagram'],
    ['mic', 'Voice note · 0:18', 'Quick capture'],
    ['notes', 'buy charger cable', 'Quick capture · 8:02 AM']].map(([i, t, m, prev], k) => `
    <div class="card in" ${st(k)}><div class="row g12">${prev ? `<div style="width:56px;height:56px;border-radius:14px;background:linear-gradient(135deg,#5EC4D6,#7E8CFF);flex:none"></div>` : `<span class="lead c" style="width:44px;height:44px;border-radius:14px;display:grid;place-items:center">${ic(i)}</span>`}
    <div class="f1"><div class="title-s">${t}</div><div class="body-s muted">${m}</div></div></div>
    <div class="row g8 mt12">${[['sticky_note_2', 'Keep'], ['task_alt', 'Task'], ['archive', ''], ['delete', '']].map(([a, l]) => `<span class="chip ${l ? 'f1 center' : ''}">${ic(a)}${l}</span>`).join('')}</div></div>`).join('')}</div></div>
  ${snackbar('Archived "buy charger cable"')}</div>`));
screen('share-saved', 'Second brain', 'Share-to-app: "Saved to Inbox"', () => device(`${behindApp()}<div class="toast anim">${icf('check_circle', 'good')}Saved to Inbox<span class="body-s muted" style="margin-left:6px">BetterLife</span></div>`, { time: '8:31', darkStatus: true }));
screen('trash', 'Second brain', 'Trash (30-day)', () => device(`<div class="screen enter">${appbar('Trash', { actions: ['delete_sweep'] })}
  <div class="scroll"><div class="card tight row g12" style="background:var(--surface-sunk)">${ic('info', 'muted')}<span class="body-s muted">Items are deleted forever after 30 days.</span></div>
  <div class="list mt12">${[['task_alt', 'Old poster draft', 'Task · deleted 2 days ago', 28], ['sticky_note_2', 'Random thoughts', 'Note · deleted 9 days ago', 21], ['grid_view', 'Cold shower', 'Habit · deleted 26 days ago', 4]].map(([i, t, m, d], k) => `
    <div class="card tight row g12 in" ${st(k)}><span class="lead" style="width:40px;height:40px;border-radius:12px;display:grid;place-items:center;background:rgba(245,233,224,.06)">${ic(i, 'muted')}</span><div class="f1"><div class="title-s">${t}</div><div class="body-s muted">${m} · <span class="${d < 7 ? 'bad' : ''}">${d} days left</span></div></div><a class="btn sm text">Restore</a></div>`).join('')}</div></div></div>`));
screen('voice', 'Second brain', 'Voice note recorder', () => {
  const bars = [...Array(34)].map((_, i) => { const h = 10 + Math.abs(Math.sin(i * 1.7) * 46) + (i % 4) * 4; return `<i style="width:5px;height:${h}px;border-radius:3px;background:${i < 24 ? 'var(--yellow)' : 'rgba(245,233,224,.2)'};display:block;animation:pop 400ms var(--ease-bounce) both;animation-delay:${i * 20}ms"></i>`; }).join('');
  return device(`<div class="screen enter-up" style="background:radial-gradient(80% 50% at 50% 35%,#4a1520,var(--bg))">${appbar('', { actions: ['close'] })}
  <div class="col f1" style="align-items:center;padding:0 24px"><span class="status r mt24">${icf('fiber_manual_record')}Recording</span>
  <div class="display-s mono mt16">00:42</div><div class="body-s muted">max 10:00</div>
  <div class="row g4 center mt24" style="height:90px">${bars}</div>
  <div class="field mt24" style="width:100%"><span class="lbl">Title</span><span class="v">Song idea</span></div>
  <div class="row g8 mt12" style="width:100%"><span class="tag">#music</span><span class="tag">+ tag</span></div>
  <div class="row g24 center" style="margin-top:auto;margin-bottom:64px;align-items:center"><span class="iconbtn tonal" style="width:56px;height:56px">${ic('delete')}</span>
  <span style="width:88px;height:88px;border-radius:50%;background:var(--bad);display:grid;place-items:center;box-shadow:0 0 0 12px rgba(255,107,94,.15),0 0 0 26px rgba(255,107,94,.06)" class="pop">${icf('stop', 'xl')}</span>
  <span class="iconbtn tonal" style="width:56px;height:56px">${ic('pause')}</span></div></div></div>`);
});
screen('search', 'Second brain', 'Global search', () => device(`<div class="screen enter">
  <div class="row g8" style="padding:12px 16px"><a class="iconbtn" style="margin-left:-8px">${ic('arrow_back')}</a><div class="field focus f1" style="min-height:48px;border-radius:24px">${ic('search', 'yellow')}<span class="v">poster<span class="caret"></span></span>${ic('close', 'muted')}</div></div>
  <div class="scroll">${[['Notes', [['sticky_note_2', '<mark>Poster</mark> ideas', 'Neon type, cherry bg…']]], ['Tasks', [['task_alt', 'Club <mark>poster</mark> due', 'Thu 12 Nov · High'], ['task_alt', 'Reply to Arya about the <mark>poster</mark>', 'Today 3:00 PM']]], ['Journal', [['auto_stories', '2 Nov', '…finally liked the <mark>poster</mark> colours…']]]].map(([g, items], k) => `
    <div class="label-m muted mt16 mb8">${g.toUpperCase()} · ${items.length}</div><div class="card flat in" ${st(k)}>${items.map(([i, t, m]) => `<div class="lrow"><span class="lead">${ic(i, 'yellow')}</span><div class="f1"><div class="title-s">${t}</div><div class="body-s muted ell">${m}</div></div></div>`).join('')}</div>`).join('')}</div></div>
  <style>mark{background:rgba(224,168,0,.3);color:var(--text);border-radius:4px;padding:0 2px}</style>`));
screen('journal', 'Second brain', 'Journal', () => device(`<div class="screen enter">${appbar('Journal', { actions: ['calendar_month'] })}
  <div class="row g8" style="padding:0 16px 12px">${['M 2', 'T 3', 'W 4', 'T 5', 'F 6', 'S 7', 'S 8'].map((d, i) => `<div class="f1 tc card tight" style="padding:8px 0;${i === 4 ? 'background:var(--cherry);border-color:var(--cherry)' : ''}${i > 4 ? ';opacity:.4' : ''}"><div class="label-s muted">${d.split(' ')[0]}</div><div class="title-m">${d.split(' ')[1]}</div>${i < 4 && i !== 1 ? '<i style="width:5px;height:5px;border-radius:50%;background:var(--yellow);display:block;margin:2px auto 0"></i>' : ''}</div>`).join('')}</div>
  <div class="scroll"><div class="card hero in"><div class="label-m yellow">TODAY'S PROMPT</div><div class="title-l mt4">What's one small thing that went right?</div><span class="body-s muted mt8 row g4">${ic('shuffle', 'sm')}Another prompt</span></div>
  <div class="mt16 body-l" style="opacity:.92">Finally finished the lab report draft before lunch. Felt lighter all afternoon<span class="caret"></span></div>
  <div class="section-h"><span class="title-s">Earlier</span></div>${[['Wed 4 Nov', 'Long day but the gym session saved it…'], ['Mon 2 Nov', 'Finally liked the poster colours…']].map(([d, t], k) => `<div class="card tight in mb8" ${st(k + 1)}><div class="label-m muted">${d}</div><div class="body-m mt4 ell">${t}</div></div>`).join('')}</div></div>`));

/* ============ BODY ============ */
function weightChart() {
  const pts = [72.4, 72.1, null, 72.3, 71.9, 71.8, null, 72.0, 71.6, 71.7, 71.4, null, 71.5, 71.2];
  const X = i => 30 + i * 22, Y = v => 150 - (v - 70.8) * 70;
  const trend = [72.4, 72.25, 72.2, 72.2, 72.1, 72.0, 71.95, 71.9, 71.8, 71.75, 71.65, 71.6, 71.5, 71.4];
  return `<svg class="chart" viewBox="0 0 330 180" width="100%">${[71, 71.5, 72, 72.5].map(v => `<line x1="30" x2="325" y1="${Y(v)}" y2="${Y(v)}" stroke="rgba(245,233,224,.07)"/><text x="0" y="${Y(v) + 3}">${v}</text>`).join('')}
    <path class="draw" d="${trend.map((v, i) => `${i ? 'L' : 'M'}${X(i)} ${Y(v)}`).join(' ')}" stroke="var(--yellow)" stroke-width="3" fill="none" stroke-linecap="round"/>
    ${pts.map((v, i) => v ? `<circle class="pop" style="--i:${i}" cx="${X(i)}" cy="${Y(v)}" r="4" fill="var(--bg)" stroke="var(--text)" stroke-width="2"/>` : '').join('')}
    ${['24 Oct', '31 Oct', '6 Nov'].map((d, i) => `<text x="${20 + i * 140}" y="176">${d}</text>`).join('')}</svg>`;
}
function bodyTabs(on) { return `<div class="tabs">${['Weight', 'Calories', 'Photos'].map(t => `<a class="tab ${t === on ? 'on' : ''}" href="?s=body-${t.toLowerCase()}">${t}</a>`).join('')}</div>`; }
screen('body-weight', 'Body', 'Weight (7-day trend)', () => device(`<div class="screen">${appbar('Body')}${bodyTabs('Weight')}
  <div class="scroll" style="padding-top:12px"><div class="card in"><div class="row between"><div><div class="label-m muted">TODAY</div><div class="display-s bold">71.2 <span class="title-m muted">kg</span></div></div><div class="tc"><div class="label-m muted">7-DAY AVG</div><div class="title-l">71.4</div></div></div>
  <div class="mt16">${weightChart()}</div><div class="row g16 body-s muted mt8"><span class="row g4"><i style="width:14px;height:3px;background:var(--yellow);display:block;border-radius:2px"></i>7-day average</span><span class="row g4"><i style="width:8px;height:8px;border-radius:50%;border:2px solid var(--text);display:block"></i>Entry</span></div></div>
  <div class="card tight row g12 mt12 in" ${st(1)}>${icf('local_fire_department', 'yellow')}<span class="body-m f1">Logged 11 of the last 14 days</span><span class="chip y pill">+10 XP</span></div>
  <div class="section-h"><span class="title-s">Entries</span></div><div class="card flat">${[['Today', '71.2'], ['Thu 5 Nov', '71.5'], ['Tue 3 Nov', '71.4']].map(([d, v]) => `<div class="lrow"><span class="f1">${d}</span><span class="title-s">${v} kg</span>${ic('more_vert', 'muted')}</div>`).join('')}</div></div>
  <div class="fab ext pop" style="bottom:40px">${ic('add')}Log weight</div></div>`));
screen('body-calories', 'Body', 'Calories (daily + weekly)', () => {
  const vals = [2100, 1850, 0, 2300, 1950, 2050, 1600]; const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  return device(`<div class="screen">${appbar('Body')}${bodyTabs('Calories')}
  <div class="scroll" style="padding-top:12px"><div class="card in"><div class="row between"><div><div class="label-m muted">THIS WEEK AVG</div><div class="display-s bold">1,975</div><div class="body-s muted">6 of 7 days logged</div></div><div class="tc"><div class="label-m muted">LAST WEEK</div><div class="title-l">2,040</div></div></div>
  <svg class="chart mt16" viewBox="0 0 330 170" width="100%"><line x1="0" x2="330" y1="${150 - 2000 / 20}" y2="${150 - 2000 / 20}" stroke="var(--yellow)" stroke-dasharray="5 5" opacity=".7"/><text x="282" y="${142 - 2000 / 20}" style="fill:var(--yellow)">goal 2,000</text>
  ${vals.map((v, i) => v ? `<rect class="barv" style="--i:${i}" x="${12 + i * 46}" y="${150 - v / 20}" width="30" height="${v / 20}" rx="8" fill="${i === 6 ? 'var(--yellow)' : 'rgba(245,233,224,.32)'}"/>` : `<rect x="${12 + i * 46}" y="138" width="30" height="12" rx="6" fill="none" stroke="rgba(245,233,224,.2)" stroke-dasharray="3 3"/>`).join('')}
  ${days.map((d, i) => `<text x="${23 + i * 46}" y="166">${d}</text>`).join('')}</svg>
  <p class="body-s muted">Unlogged days are skipped, never counted as zero.</p></div>
  <div class="section-h"><span class="title-s">Today · 1,600 kcal</span></div><div class="card flat">${[['Breakfast · oats', '420'], ['Lunch · rice & dal', '780'], ['Snack', '400']].map(([d, v]) => `<div class="lrow"><span class="f1">${d}</span><span class="title-s">${v}</span></div>`).join('')}</div></div>
  <div class="fab ext pop" style="bottom:40px">${ic('add')}Add calories</div></div>`);
});
function photo(h, hue, label) {
  return `<div style="height:${h}px;border-radius:18px;position:relative;overflow:hidden;background:linear-gradient(180deg,hsl(${hue} 25% 30%),hsl(${hue} 30% 16%))">
    <svg viewBox="0 0 100 140" style="position:absolute;inset:0;width:100%;height:100%" preserveAspectRatio="xMidYMax meet"><ellipse cx="50" cy="38" rx="13" ry="15" fill="rgba(245,233,224,.25)"/><path d="M22 140 Q24 66 50 60 Q76 66 78 140 Z" fill="rgba(245,233,224,.2)"/></svg>
    ${label ? `<span class="tag" style="position:absolute;left:8px;bottom:8px;background:rgba(26,10,13,.7);color:var(--text)">${label}</span>` : ''}</div>`;
}
screen('body-photos', 'Body', 'Progress photos timeline', () => device(`<div class="screen">${appbar('Body', { actions: ['compare'] })}${bodyTabs('Photos')}
  <div class="scroll" style="padding-top:12px"><div class="card tight row g12" style="background:var(--surface-sunk)">${ic('lock', 'muted')}<span class="body-s muted">Private. Never in your gallery, never uploaded.</span></div>
  ${[['Week 45 · this week', 2, 340], ['Week 44', 1, 330], ['Week 43', 2, 320]].map(([w, n, hue], k) => `<div class="section-h"><span class="title-s">${w}</span><span class="body-s muted">${n} photo${n > 1 ? 's' : ''}</span></div>
  <div class="row g8 in" ${st(k)}>${[...Array(3)].map((_, i) => i < n ? `<div class="f1">${photo(150, hue + i * 8)}</div>` : `<div class="f1" style="height:150px;border-radius:18px;border:1.5px dashed rgba(245,233,224,.2);display:grid;place-items:center">${k === 0 ? ic('add_a_photo', 'muted') : ''}</div>`).join('')}</div>`).join('')}</div>
  <div class="fab ext pop" style="bottom:40px">${ic('photo_camera')}Take photo</div></div>`));
screen('body-compare', 'Body', 'Photo compare slider', () => device(`<div class="screen enter">${appbar('Compare', { actions: ['swap_horiz'] })}
  <div class="tabs"><span class="tab">Side by side</span><span class="tab on">Slider</span></div>
  <div style="padding:16px;position:relative;flex:1"><div style="position:relative;height:560px;border-radius:24px;overflow:hidden">
    <div style="position:absolute;inset:0">${photo(560, 330, '')}</div><div style="position:absolute;inset:0;clip-path:inset(0 45% 0 0)">${photo(560, 20, '')}</div>
    <div style="position:absolute;top:0;bottom:0;left:55%;width:3px;background:var(--text);margin-left:-1.5px"></div>
    <div style="position:absolute;top:50%;left:55%;width:48px;height:48px;margin:-24px;border-radius:50%;background:var(--text);color:var(--bg);display:grid;place-items:center" class="pop">${ic('unfold_more', '')}</div>
    <span class="chip pill" style="position:absolute;top:12px;left:12px;background:rgba(26,10,13,.75)">3 Aug</span><span class="chip pill" style="position:absolute;top:12px;right:12px;background:rgba(26,10,13,.75)">6 Nov</span></div>
  <div class="row g8 mt12">${[1, 2, 3, 4, 5].map(i => `<div class="f1" style="${i === 1 || i === 5 ? 'outline:2px solid var(--yellow);border-radius:12px' : 'opacity:.6'}">${photo(64, 300 + i * 12)}</div>`).join('')}</div></div></div>`));

/* ============ REWARDS ============ */
screen('dashboard', 'Rewards', 'Dashboard', () => device(`<div class="screen enter">${appbar('Dashboard', { actions: ['calendar_view_week'] })}
  <div class="scroll"><div class="row g8"><div class="card f1 tc in">${ring(72, 96, 10, 'var(--yellow)', '<div class="title-l bold">72%</div><div class="label-s muted">today</div>')}<div class="body-s muted mt8">Daily score</div></div>
  <div class="card f1 tc in" ${st(1)}>${ring(84, 96, 10, '#7BC67E', '<div class="title-l bold">84%</div><div class="label-s muted">30 days</div>')}<div class="body-s muted mt8">Consistency</div></div></div>
  <div class="row g8 mt8">${[['local_fire_department', '37', 'best streak'], ['event_available', 'Tue', 'best weekday'], ['bolt', 'Lv 7', '1,240 XP']].map(([i, v, l], k) => `<div class="card tight f1 tc in" ${st(k + 2)}>${icf(i, 'yellow')}<div class="title-m bold">${v}</div><div class="label-s muted">${l}</div></div>`).join('')}</div>
  <div class="card mt8 in" ${st(5)}><div class="row between"><span class="title-s">Last 7 days</span><span class="body-s muted">habits + tasks</span></div>
  <svg class="chart mt12" viewBox="0 0 330 120" width="100%">${[60, 85, 92, 70, 40, 88, 72].map((v, i) => `<rect class="barv" style="--i:${i}" x="${8 + i * 47}" y="${100 - v}" width="32" height="${v}" rx="9" fill="${i === 6 ? 'var(--yellow)' : 'var(--cherry)'}"/><text x="${18 + i * 47}" y="116">${'MTWTFSS'[i]}</text>`).join('')}</svg></div>
  <div class="card tight row g12 mt8 in" ${st(6)} style="border-color:rgba(126,140,255,.4)">${icf('lightbulb', 'yellow')}<div class="f1"><div class="label-m muted">INSIGHT</div><div class="body-m">Meditation sticks best on days you do it before 9 AM.</div></div></div>
  <div class="card tight row g12 mt8 in" ${st(7)}>${ic('grid_view', 'muted')}<span class="f1 body-m">Most skipped: Haircare routine</span><span class="body-s muted">4 of 7</span></div></div></div>`));
screen('report-card', 'Rewards', 'Weekly report card', () => device(`<div class="screen enter-up" style="background:radial-gradient(100% 40% at 50% 0%,#4a1520,var(--bg))">${appbar('Week 45', { actions: ['share'] })}
  <div class="scroll"><div class="row g16 in">${mascot({ size: 88, mood: 'proud', stage: 3 })}<div><div class="label-m yellow">REPORT CARD</div><div class="headline-s bold">Solid week.</div><div class="body-s muted">2 – 8 Nov</div></div></div>
  <div class="label-m good mt24 mb8">WINS</div><div class="list">${['Creatine every single day (37 and counting)', '14 tasks done, 3 of them overdue rescues', 'Weight logged 6 of 7 days'].map((t, k) => `<div class="card tight row g12 in" ${st(k + 1)}>${icf('check_circle', 'good')}<span class="body-m">${t}</span></div>`).join('')}</div>
  <div class="label-m yellow mt16 mb8">ONE THING TO WATCH</div><div class="card tight row g12 in" ${st(4)}>${ic('visibility', 'yellow')}<span class="body-m">Haircare slipped on weekends.</span></div>
  <div class="label-m muted mt16 mb8">ONE SMALL IDEA</div><div class="card tight row g12 in" ${st(5)}>${ic('lightbulb', 'yellow')}<span class="body-m">Move haircare to Saturday morning with a 10 AM reminder?</span></div>
  <a class="btn primary block mt24 in" ${st(6)}>${icf('bolt')}Complete review · +50 XP</a></div></div>`));
screen('level-up', 'Rewards', 'Level up', () => device(`<div class="screen" style="background:radial-gradient(70% 45% at 50% 42%,#6a2a10,var(--bg));align-items:center;justify-content:center;text-align:center">${confetti(30)}
  <div style="position:relative;z-index:86" class="col center"><div class="label-l yellow fade" style="letter-spacing:4px">LEVEL UP</div>
  <div class="row g16 center mt16" style="align-items:center"><span class="title-l faint fade">7</span>${ic('arrow_forward', 'muted')}<span class="pop" ${st(3)} style="font-size:96px;font-weight:900;line-height:1;color:var(--yellow);text-shadow:0 0 50px rgba(224,168,0,.6)">8</span></div>
  <div class="pop mt16" ${st(5)}>${mascot({ size: 150, mood: 'cheering', stage: 3 })}</div><div class="title-l bold mt16 fade" ${st(7)}>Blip is proud of you</div>
  <div class="row g8 mt16 center fade" ${st(8)}><span class="chip y pill">${icf('bolt')}1,500 XP</span></div><a class="btn primary mt24 fade" ${st(9)} style="width:220px">Nice!</a></div></div>`));
screen('evolution', 'Rewards', 'Mascot moods & evolution', () => device(`<div class="screen enter">${appbar('Blip')}
  <div class="scroll"><div class="label-m muted mb8">EVOLUTION · never goes down</div>
  <div class="row between card" style="padding:16px 8px;align-items:flex-end">${[[1, 'Pod', '0'], [2, 'Baby', '7'], [3, 'Teen', '30'], [4, 'Adult', '90'], [5, 'Legend', '200']].map(([s, n, d], k) => `<div class="col tc pop" ${st(k)} style="align-items:center;${s > 3 ? 'opacity:.45;filter:grayscale(.6)' : ''}">${mascot({ size: 34 + s * 6, mood: s === 3 ? 'proud' : 'curious', stage: s, anim: s === 3 })}<div class="label-s mt4">${n}</div><div class="body-s faint">${d}d</div></div>`).join('')}</div>
  <div class="bar mt12"><i style="width:48%"></i></div><div class="body-s muted mt4">43 of 90 consistent days to Adult</div>
  <div class="label-m muted mt24 mb8">MOODS · last 24–72 h</div>
  <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px">${[['cheering', 'Cheering'], ['proud', 'Proud'], ['curious', 'Curious (idle)'], ['sleepy', 'Sleepy (late)'], ['disappointed', 'Down (never blames)']].map(([m, l], k) => `<div class="card tight col tc in" ${st(k)} style="align-items:center">${mascot({ size: 70, mood: m })}<div class="label-s mt4">${l}</div></div>`).join('')}
  <div class="card tight col tc in" ${st(5)} style="align-items:center">${mascot({ size: 70, mood: 'curious', stage: 5 })}<div class="label-s mt4">Legend aura</div></div></div></div></div>`));
screen('year-review', 'Rewards', 'Year in review', () => device(`<div class="screen enter-up" style="background:linear-gradient(180deg,#5a1823 0%,var(--bg) 55%)">${appbar('', { actions: ['close'] })}
  <div class="scroll"><div class="label-l yellow fade" style="letter-spacing:3px">YOUR 2026</div><div class="display-s heavy fade" ${st(1)} style="letter-spacing:-1.5px;line-height:40px">A year of<br>showing up.</div>
  <div class="row g8 mt24">${[['1,284', 'habit ticks'], ['412', 'tasks done'], ['41', 'best streak']].map(([v, l], k) => `<div class="card tight f1 pop" ${st(k + 2)}><div class="headline-s bold yellow">${v}</div><div class="label-s muted">${l}</div></div>`).join('')}</div>
  <div class="card mt8 in" ${st(5)}><div class="title-s mb8">Consistency by habit</div>${HABITS.map(h => `<div class="row g8 mt8"><span style="width:24px">${h.e}</span><span class="body-s f1">${h.n}</span><div class="bar" style="width:120px"><i style="width:${h.cons || 50}%;background:${h.c}"></i></div><span class="label-m" style="width:34px;text-align:right">${h.cons || 50}%</span></div>`).join('')}</div>
  <div class="card row g16 mt8 in" ${st(6)}>${mascot({ size: 64, mood: 'proud', stage: 3 })}<div><div class="title-m">Blip became a Teen</div><div class="body-s muted">on 14 August · 52 progress photos</div></div></div></div></div>`));

/* ============ SETTINGS & SAFETY ============ */
const setRow = (i, t, v, sw) => `<div class="lrow"><span class="lead">${ic(i)}</span><span class="f1">${t}</span>${sw !== undefined ? `<span class="switch ${sw ? 'on' : ''}"></span>` : `<span class="body-s muted">${v}</span>`}</div>`;
screen('settings', 'More & settings', 'Settings', () => device(`<div class="screen enter">${appbar('Settings', { actions: ['search'] })}
  <div class="scroll">${[['You', [['person', 'Name', 'Sanjith'], ['pets', 'Mascot name', 'Blip'], ['schedule', 'Day starts at', '3:00 AM'], ['calendar_view_week', 'Week starts on', 'Monday']]],
  ['Reminders', [['snooze', 'Snooze "Tonight"', '8:00 PM'], ['wb_twilight', 'Snooze "Tomorrow"', '9:00 AM'], ['nights_stay', 'Evening nudge', '9:00 PM'], ['visibility_off', 'Hide titles on lock screen', null, 1]]],
  ['Look & feel', [['animation', 'Reduced motion', null, 0], ['vibration', 'Haptics', null, 1], ['palette', 'Accent', 'Yellow']]],
  ['Privacy', [['widgets', 'Hide details on widgets', null, 1], ['fingerprint', 'App lock', 'After 1 min']]]].map(([g, rows], k) => `<div class="section-h"><span class="title-s">${g}</span></div><div class="card flat in" ${st(k)}>${rows.map(r => setRow(r[0], r[1], r[2], r[3] === undefined ? undefined : !!r[3])).join('')}</div>`).join('')}<div style="height:24px"></div></div></div>`));
screen('sync', 'More & settings', 'Account & sync', () => device(`<div class="screen enter">${appbar('Account & sync')}
  <div class="scroll"><div class="card hero row g16 in"><span style="width:56px;height:56px;border-radius:20px;background:var(--cherry);display:grid;place-items:center">${icf('cloud_done', 'lg good')}</span><div class="f1"><div class="title-m">Synced</div><div class="body-s muted">2 min ago · 0 pending</div><div class="body-s muted">you@example.com</div></div></div>
  <a class="btn primary block mt12">${ic('sync')}Sync now</a>
  <div class="label-m muted mt24 mb8">STATES</div><div class="row g8 wrap"><span class="status g">${icf('cloud_done')}Synced</span><span class="status y">${icf('cloud_upload')}3 pending</span><span class="status n">${icf('cloud_off')}Offline</span><span class="status r">${icf('pause_circle')}Server paused</span><span class="status n">${icf('person_off')}Signed out</span></div>
  <div class="card flat mt24">${setRow('sync_alt', 'Sync automatically', null, true)}${setRow('logout', 'Sign out', 'Keeps local data')}</div>
  <div class="card tight row g12 mt12" style="border-color:rgba(255,107,94,.35)">${ic('delete_forever', 'bad')}<div class="f1"><div class="title-s bad">Delete my cloud data</div><div class="body-s muted">Asks twice. Phone data stays.</div></div></div></div></div>`));
screen('backup', 'More & settings', 'Backup & restore', () => device(`<div class="screen enter">${appbar('Backup')}
  <div class="scroll"><div class="card in"><div class="row g12">${icf('verified', 'lg good')}<div class="f1"><div class="title-m">Last backup: today 3:00 AM</div><div class="body-s muted">48.2 MB · 7 kept · Documents/BetterLife</div></div></div>
  <div class="row g8 mt16"><a class="btn primary f1">${ic('backup')}Back up now</a><a class="btn outline f1">${ic('settings_backup_restore')}Restore</a></div></div>
  <div class="card tight row g12 mt8 in" ${st(1)} style="border-color:rgba(255,107,94,.35)">${ic('error', 'bad')}<div class="f1"><div class="body-s">Failed Tue 3 Nov: folder permission revoked</div></div><a class="btn sm text">Fix</a></div>
  <div class="section-h"><span class="title-s">Restore preview</span></div>
  <div class="card in" ${st(2)} style="border-color:rgba(224,168,0,.45)"><div class="title-s">Backup from Sun 1 Nov</div><div class="row wrap g8 mt8">${['1,842 tasks', '34 habits', '217 notes', '12 photos'].map(t => `<span class="tag">${t}</span>`).join('')}</div>
  <div class="body-s muted mt12">${ic('shield', 'sm')} A safety backup of today's data is made first. Restore <b>replaces</b> everything.</div><a class="btn tonal block mt12">Restore this backup</a></div>
  <div class="card flat mt12">${setRow('schedule', 'Auto-backup', 'Daily 3:00 AM')}${setRow('enhanced_encryption', 'Encrypt with passphrase', null, false)}${setRow('table_view', 'Export CSV', '')}</div></div></div>`));
screen('vault', 'More & settings', 'Private vault (second lock)', () => device(`<div class="screen" style="align-items:center;justify-content:center;text-align:center;padding:0 32px;background:radial-gradient(60% 40% at 50% 40%,#2a1a30,var(--bg))">
  <div class="pop" style="width:110px;height:110px;border-radius:36px;background:var(--surface);border:1px solid rgba(199,125,255,.4);display:grid;place-items:center;box-shadow:0 0 60px rgba(199,125,255,.25)">${icf('lock', 'xl')}</div>
  <div class="headline-s bold mt24 fade">Private vault</div><p class="body-m muted mt8 fade">Hidden from search, widgets, notifications and share lists.</p>
  <a class="btn primary mt24 fade" style="width:240px">${ic('fingerprint')}Unlock vault</a><p class="body-s faint mt16">Uses your phone's screen lock</p></div>`));

/* ============ WIDGETS & DIALOG ============ */
screen('widgets', 'Widgets', 'Home-screen widgets', () => device(`<div class="wall"></div><div class="screen" style="padding:52px 16px 0;gap:12px">
  <div class="widget in"><div class="row between"><span class="label-m muted">HABITS · 84%</span>${mascot({ size: 30, mood: 'curious', anim: false })}</div>
  <div class="row g8 mt8">${HABITS.map((h, i) => `<div class="f1 col g4 tc" style="align-items:center"><span class="hbox ${i < 2 ? 'done' : ''}" style="width:46px;height:46px;border-radius:14px;${i < 2 ? 'background:' + h.c : ''}">${i < 2 ? icf('check') : ''}</span><span class="label-s muted">Habit ${i + 1}</span></div>`).join('')}</div><div class="body-s faint mt8">Details hidden · 2 of 4 done</div></div>
  <div class="row g12"><div class="widget f1 in" ${st(1)}><div class="row between"><span class="label-m muted">TODAY</span><span style="width:30px;height:30px;border-radius:10px;background:var(--yellow);color:var(--bg);display:grid;place-items:center">${ic('add', 'md')}</span></div>
  ${[['Lab report', 'bad', 1], ['Electricity', 'yellow', 0], ['Dentist', 'good', 0]].map(([t, c, d]) => `<div class="row g8 mt8"><span class="tick ${d ? 'on' : ''}" style="width:20px;height:20px;border-radius:6px">${d ? ic('check', 'sm') : ''}</span><span class="body-s f1 ell" style="${d ? 'text-decoration:line-through;opacity:.6' : ''}">${t}</span><span class="${c}">${icf('flag', 'sm')}</span></div>`).join('')}</div>
  <div class="widget f1 in" ${st(2)}><div class="label-m muted">NOVEMBER</div><div style="display:grid;grid-template-columns:repeat(7,1fr);gap:2px;margin-top:6px">${[...Array(35)].map((_, i) => { const d = i - 5; return `<span style="font-size:9px;text-align:center;height:16px;line-height:16px;border-radius:5px;${d === 6 ? 'background:var(--yellow);color:var(--bg);font-weight:700' : d < 1 || d > 30 ? 'opacity:.2' : ''}">${d < 1 ? '' : d > 30 ? '' : d}${DOTS[d] ? '<i style="display:block;width:3px;height:3px;border-radius:50%;background:' + DOTS[d][0] + ';margin:-3px auto 0"></i>' : ''}</span>`; }).join('')}</div></div></div>
  <div class="widget row g8 in" ${st(3)} style="padding:10px">${[['add_task', 'Task'], ['sticky_note_2', 'Note'], ['mic', 'Voice']].map(([i, l]) => `<span class="f1 row g8 center" style="height:48px;border-radius:16px;background:var(--cherry)">${ic(i, 'yellow')}<span class="label-l">${l}</span></span>`).join('')}</div>
  <div class="row g16 center" style="margin-top:auto;margin-bottom:60px">${['#3DDC84', '#4285F4', '#E0A800', '#FF6B5E', '#C77DFF'].map(c => `<span style="width:56px;height:56px;border-radius:18px;background:${c};opacity:.85"></span>`).join('')}</div></div>`, { time: '8:24' }));
screen('quickadd-dialog', 'Widgets', 'Quick-add dialog (from widget)', () => device(`<div class="wall"></div><div class="scrim anim soft"></div>
  <div class="dialog popup-card anim" style="position:absolute;top:auto;bottom:330px;transform:none"><div class="row g8">${icf('add_task', 'yellow')}<span class="title-l">Quick add</span></div>
  <div class="field focus mt16 on-surface"><span class="lbl" style="background:#341219">Task</span><span class="v">gym tomorrow 7am #health<span class="caret"></span></span></div>
  <div class="row wrap g8 mt12"><span class="chip y pop">${ic('event')}Sat 7 Nov</span><span class="chip y pop" ${st(1)}>${ic('schedule')}7:00 AM</span><span class="chip pop" ${st(2)}>${ic('tag')}health</span></div>
  <div class="row g8 mt24" style="justify-content:flex-end"><a class="btn text">Cancel</a><a class="btn primary">Save</a></div></div>
  <div style="position:absolute;left:0;right:0;bottom:0;height:300px;background:#2b2326;border-radius:20px 20px 0 0;z-index:85;padding:12px">${['qwertyuiop', 'asdfghjkl', 'zxcvbnm'].map(r => `<div class="row g4 center mt8">${r.split('').map(k => `<span style="width:34px;height:44px;border-radius:8px;background:#433a3d;display:grid;place-items:center;font-size:18px">${k}</span>`).join('')}</div>`).join('')}</div>`));

/* ============ ERROR STATE ============ */
screen('error-state', 'States', 'Error state', () => device(`<div class="screen">${appbar('Backup')}
  <div class="col f1 center" style="align-items:center;text-align:center;padding:0 32px;margin-top:-60px">${mascot({ size: 120, mood: 'disappointed' })}
  <div class="title-l mt16">Couldn't reach the backup folder</div><p class="body-m muted mt8">The folder permission was removed. Your data is safe on this phone.</p>
  <a class="btn primary mt24">${ic('folder_open')}Choose folder again</a><a class="btn text mt8">Details</a></div></div>`));
screen('offline-state', 'States', 'Offline / sync paused banner', () => device(`<div class="screen">${todayBody(`<div class="card tight row g12 mt16 in" style="background:var(--surface-sunk)">${icf('cloud_off', 'muted')}<div class="f1"><div class="title-s">Offline</div><div class="body-s muted">Everything still works. 3 changes will sync later.</div></div></div>`)}</div>`, { nav: 'today' }));
