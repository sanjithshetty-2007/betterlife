// BetterLife design reference: shared pieces (icons, mascot, shell, router, freeze-frame control).
'use strict';
const Q = new URLSearchParams(location.search);
const SCREENS = {};       // id -> { title, group, render }
const ORDER = [];
function screen(id, group, title, render) { SCREENS[id] = { id, group, title, render }; ORDER.push(id); }

const ic = (name, cls = '') => `<span class="ms ${cls}">${name}</span>`;
const icf = (name, cls = '') => ic(name, 'fill ' + cls);
const st = (i) => `data-i="${i}"`;  // applied as --i in boot(), so elements can keep their own style attribute

// ---------- Mascot (drawn shapes only, mirrors the planned Compose Canvas mascot) ----------
let mascotSeq = 0;
function mascot({ size = 120, mood = 'curious', stage = 2, anim = true } = {}) {
  const id = 'm' + (mascotSeq++);
  const cls = `mascot ${anim ? mood : ''}`;
  if (stage === 1) return egg(size, id, cls);
  const sit = mood === 'disappointed' ? 8 : 0;
  const eyesOpen = !['sleepy', 'proud'].includes(mood);
  const eye = (cx) => eyesOpen ? `
      <ellipse cx="${cx}" cy="${66 + sit}" rx="11.5" ry="13" fill="#FFF6EE"/>
      <circle cx="${cx + (mood === 'disappointed' ? 0 : 2)}" cy="${(mood === 'disappointed' ? 71 : 68) + sit}" r="7.5" fill="#1A0A0D"/>
      <circle cx="${cx + 4.5}" cy="${63 + sit}" r="3" fill="#fff"/>
      <circle cx="${cx - 1}" cy="${72 + sit}" r="1.4" fill="#fff" opacity=".8"/>
      ${mood === 'disappointed' ? `<rect x="${cx - 12}" y="${52 + sit}" width="24" height="9" fill="url(#${id}b)"/>` : ''}
      <rect class="eye-lid" x="${cx - 12.5}" y="${52 + sit}" width="25" height="28" rx="12" fill="url(#${id}b)"/>`
    : `<path d="M${cx - 9} ${68 + sit} q9 ${mood === 'proud' ? -10 : 6} 18 0" stroke="#1A0A0D" stroke-width="3.2" fill="none" stroke-linecap="round"/>`;
  const mouth = {
    cheering: `<path d="M50 ${84 + sit} q10 14 20 0 z" fill="#1A0A0D"/><path d="M55 ${89 + sit} q5 4 10 0" fill="#FF8FB8"/>`,
    proud: `<path d="M50 ${84 + sit} q10 9 20 0" stroke="#1A0A0D" stroke-width="3" fill="none" stroke-linecap="round"/>`,
    curious: `<ellipse cx="61" cy="${87 + sit}" rx="3.5" ry="4" fill="#1A0A0D"/>`,
    sleepy: `<ellipse cx="60" cy="${87 + sit}" rx="4" ry="2.5" fill="#1A0A0D"/>`,
    disappointed: `<path d="M53 ${90 + sit} q7 -5 14 0" stroke="#1A0A0D" stroke-width="3" fill="none" stroke-linecap="round"/>`,
  }[mood];
  const armsUp = mood === 'cheering';
  const arms = armsUp
    ? `<ellipse class="arm-l" cx="18" cy="${58}" rx="7" ry="12" transform="rotate(30 18 58)" fill="url(#${id}b)"/>
       <ellipse class="arm-r" cx="102" cy="${58}" rx="7" ry="12" transform="rotate(-30 102 58)" fill="url(#${id}b)"/>`
    : mood === 'proud'
      ? `<ellipse cx="20" cy="${86}" rx="7" ry="11" transform="rotate(-35 20 86)" fill="url(#${id}b)"/>
         <ellipse cx="100" cy="${86}" rx="7" ry="11" transform="rotate(35 100 86)" fill="url(#${id}b)"/>`
      : `<ellipse cx="20" cy="${88 + sit}" rx="7" ry="11" transform="rotate(25 20 ${88 + sit})" fill="url(#${id}b)"/>
         <ellipse cx="100" cy="${88 + sit}" rx="7" ry="11" transform="rotate(-25 100 ${88 + sit})" fill="url(#${id}b)"/>`;
  const legs = stage >= 3 ? `<ellipse cx="46" cy="118" rx="9" ry="6" fill="#5a1520"/><ellipse cx="74" cy="118" rx="9" ry="6" fill="#5a1520"/>` : '';
  const cape = stage === 4 ? `<path d="M28 70 Q60 40 92 70 L104 122 Q60 110 16 122 Z" fill="#E0A800" opacity=".95"/><path d="M32 72 Q60 46 88 72 L98 118 Q60 108 22 118 Z" fill="#4d1119"/>` : '';
  const marks = stage >= 4 ? `<path d="M34 92 q6 -6 12 0 M74 92 q6 -6 12 0" stroke="#E0A800" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".9"/>` : '';
  const aura = stage === 5 ? `<g class="aura"><circle cx="60" cy="72" r="58" fill="none" stroke="#E0A800" stroke-width="1.5" stroke-dasharray="3 9" opacity=".7"/></g>
      <circle cx="60" cy="72" r="56" fill="url(#${id}a)"/>` : '';
  const antH = stage >= 3 ? 0 : 6;
  const zz = mood === 'sleepy' ? `<text class="zz" x="92" y="30" fill="#F5E9E0" font-size="14" font-weight="700" font-family="Roboto">z</text><text class="zz" style="animation-delay:1.2s" x="100" y="20" fill="#F5E9E0" font-size="10" font-weight="700" font-family="Roboto">z</text>` : '';
  return `<svg class="${cls}" width="${size}" height="${size * 130 / 120}" viewBox="0 0 120 130" aria-label="Blip, ${mood}">
    <defs>
      <radialGradient id="${id}b" cx="38%" cy="32%" r="75%"><stop offset="0" stop-color="#B3394F"/><stop offset=".55" stop-color="#7A1F2B"/><stop offset="1" stop-color="#4d1119"/></radialGradient>
      <radialGradient id="${id}g"><stop offset="0" stop-color="#FFE27A"/><stop offset="1" stop-color="#E0A800" stop-opacity="0"/></radialGradient>
      <radialGradient id="${id}a"><stop offset=".55" stop-color="#E0A800" stop-opacity="0"/><stop offset="1" stop-color="#E0A800" stop-opacity=".35"/></radialGradient>
    </defs>
    ${aura}${cape}
    <g class="body-g">
      <path d="M60 ${36 + sit} C${stage >= 3 ? 52 : 56} ${22 + antH} 62 ${12 + antH} 68 ${8 + antH}" stroke="#5a1520" stroke-width="${stage >= 3 ? 4.5 : 3.5}" fill="none" stroke-linecap="round"/>
      <circle class="bulb-glow" cx="68" cy="${8 + antH}" r="${stage >= 3 ? 14 : 11}" fill="url(#${id}g)"/>
      <circle cx="68" cy="${8 + antH}" r="${stage >= 3 ? 6.5 : 5.5}" fill="${['sleepy', 'disappointed'].includes(mood) ? '#8a6a10' : '#FFD23F'}"/>
      <circle cx="66.5" cy="${6.5 + antH}" r="1.8" fill="#fff" opacity=".9"/>
      ${legs}
      <path d="M60 ${34 + sit} C88 ${34 + sit} 104 ${52 + sit} 104 ${80 + sit / 2} C104 ${104} 86 116 60 116 C34 116 16 104 16 ${80 + sit / 2} C16 ${52 + sit} 32 ${34 + sit} 60 ${34 + sit} Z" fill="url(#${id}b)"/>
      <ellipse cx="42" cy="${48 + sit}" rx="13" ry="7" fill="#fff" opacity=".13" transform="rotate(-25 42 ${48 + sit})"/>
      ${marks}
      ${arms}
      <ellipse cx="34" cy="${84 + sit}" rx="6" ry="3.5" fill="#FF8FB8" opacity=".35"/><ellipse cx="86" cy="${84 + sit}" rx="6" ry="3.5" fill="#FF8FB8" opacity=".35"/>
      ${eye(45)}${eye(75)}
      ${mouth}
    </g>${zz}
  </svg>`;
}
function egg(size, id, cls) {
  return `<svg class="${cls}" width="${size}" height="${size * 130 / 120}" viewBox="0 0 120 130">
    <defs><radialGradient id="${id}e" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#C24A60"/><stop offset=".6" stop-color="#7A1F2B"/><stop offset="1" stop-color="#3d0d14"/></radialGradient>
    <radialGradient id="${id}g"><stop offset=".3" stop-color="#E0A800" stop-opacity=".45"/><stop offset="1" stop-color="#E0A800" stop-opacity="0"/></radialGradient></defs>
    <circle class="bulb-glow" cx="60" cy="72" r="56" fill="url(#${id}g)"/>
    <g class="body-g"><path d="M60 18 C86 18 98 60 98 82 C98 106 82 120 60 120 C38 120 22 106 22 82 C22 60 34 18 60 18 Z" fill="url(#${id}e)"/>
    <path d="M40 70 l8 6 l7 -7 l7 7 l8 -6 l8 6" stroke="#E0A800" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
    <ellipse cx="46" cy="44" rx="9" ry="13" fill="#fff" opacity=".14" transform="rotate(-20 46 44)"/>
    <circle cx="60" cy="96" r="4" fill="#FFD23F"/></g></svg>`;
}

// ---------- Shell ----------
function statusBar(time = '8:24') {
  return `<div class="statusbar"><span>${time}</span><span class="ic">${icf('signal_cellular_alt')}${icf('wifi')}${icf('battery_5_bar')}<span style="font-size:12px">82%</span></span></div>`;
}
const NAV = [
  ['today', 'Today', 'wb_sunny'], ['tasks', 'Tasks', 'task_alt'], ['habits', 'Habits', 'grid_view'],
  ['calendar', 'Calendar', 'calendar_month'], ['more', 'More', 'apps'],
];
function navBar(active, opts = {}) {
  return `<nav class="navbar ${opts.anim ? 'anim' : ''}">${NAV.map(([k, label, icon]) => `
    <a class="it ${k === active ? 'on' : ''}" href="?s=${k}${k === 'calendar' ? '-month' : ''}">
      <span class="pillbg">${ic(icon)}${k === 'more' && opts.badge ? `<span class="nb">${opts.badge}</span>` : ''}</span>${label}</a>`).join('')}</nav>`;
}
function device(inner, { time, nav, navOpts, cls = "", darkStatus } = {}) {
  return `<div class="device ${cls}">${inner}${nav ? navBar(nav, navOpts || { badge: 4 }) : ''}${statusBar(time).replace('class="statusbar"', darkStatus ? 'class="statusbar" style="color:#1A0A0D"' : 'class="statusbar"')}<div class="gesturebar"></div></div>`;
}
function appbar(title, { back, actions = [], sub } = {}) {
  if (back === undefined) back = !actions.includes('close'); // a full-screen flow closes; it never also shows Back
  return `<div class="appbar">${back ? `<a class="iconbtn" href="javascript:history.back()" style="margin-left:-8px">${ic('arrow_back')}</a>` : ''}
    <div class="t">${title}${sub ? `<div class="body-s muted">${sub}</div>` : ''}</div>${actions.map(a => `<span class="iconbtn">${ic(a)}</span>`).join('')}</div>`;
}
function ring(pct, size = 76, stroke = 8, color = 'var(--yellow)', inner = '') {
  const r = (size - stroke) / 2, len = 2 * Math.PI * r;
  return `<div class="ring" style="width:${size}px;height:${size}px"><svg width="${size}" height="${size}">
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" stroke="rgba(245,233,224,.1)" stroke-width="${stroke}" fill="none"/>
    <circle class="p" style="--len:${len}" cx="${size / 2}" cy="${size / 2}" r="${r}" stroke="${color}" stroke-width="${stroke}" fill="none" stroke-linecap="round"
      stroke-dasharray="${len}" stroke-dashoffset="${len * (1 - pct / 100)}"/></svg><div class="val">${inner}</div></div>`;
}
function snackbar(text, action = 'Undo') {
  return `<div class="snackbar anim"><span class="f1">${text}</span><span class="act">${action}</span><i class="timer"></i></div>`;
}
function confetti(n = 46) {
  const cols = ['#E0A800', '#FF6B5E', '#7BC67E', '#5EC4D6', '#C77DFF', '#FF8FB8', '#F5E9E0'];
  let s = '';
  for (let i = 0; i < n; i++) {
    const x = (i * 37) % 100, d = 2.4 + (i % 5) * .5, dl = -((i * 0.37) % 3);
    s += `<i style="left:${x}%;background:${cols[i % cols.length]};animation-duration:${d}s;animation-delay:${dl}s;${i % 3 === 0 ? 'border-radius:50%;width:9px;height:9px' : ''}"></i>`;
  }
  return `<div class="confetti" style="position:absolute;inset:0;overflow:hidden;z-index:85;pointer-events:none">${s}</div>`;
}

// ---------- Router ----------
async function boot() {
  const id = Q.get('s');
  const app = document.getElementById('app');
  if (!app) return; // loaded by the PDF reference page only for its helpers
  if (!id || !SCREENS[id]) { app.innerHTML = galleryIndex(); return; }
  document.title = 'BetterLife: ' + SCREENS[id].title;
  app.innerHTML = SCREENS[id].render(Q.get('state') || '');
  document.querySelectorAll('[data-i]').forEach(el => el.style.setProperty('--i', el.dataset.i));
  if (Q.get('reduced')) document.querySelector('.device').classList.add('reduced');
  wireInteractions();
  await document.fonts.ready;
  const t = Q.get('t');
  if (t !== null) {
    // Freeze every animation at time t (ms) so a screenshot shows one exact frame.
    requestAnimationFrame(() => document.getAnimations().forEach(a => { a.pause(); a.currentTime = Number(t); }));
  }
  document.body.dataset.ready = '1';
}
function galleryIndex() {
  const groups = {};
  ORDER.forEach(id => (groups[SCREENS[id].group] ||= []).push(SCREENS[id]));
  return `<div style="padding:32px;max-width:900px;margin:auto;color:var(--text)"><h1 class="headline-m">BetterLife prototype</h1>
    <p class="muted mt8">Design reference. Open any screen; tap the nav bar and tick boxes to see motion.</p>
    ${Object.entries(groups).map(([g, list]) => `<h2 class="title-l mt24">${g}</h2><div class="row wrap g8 mt8">${list.map(s => `<a class="chip" href="?s=${s.id}">${s.title}</a>`).join('')}</div>`).join('')}</div>`;
}
function wireInteractions() {
  document.querySelectorAll('[data-tick]').forEach(el => el.addEventListener('click', () => {
    el.classList.toggle('on'); el.classList.remove('pop'); void el.offsetWidth; el.classList.add('pop');
  }));
  document.querySelectorAll('.hbox:not(.void):not(.future)').forEach(el => el.addEventListener('click', () => {
    const c = el.dataset.c || 'var(--yellow)';
    if (el.classList.toggle('done')) { el.style.background = c; el.innerHTML = icf('check'); } else { el.style.background = ''; el.innerHTML = ''; }
    el.classList.remove('popin'); void el.offsetWidth; el.classList.add('popin');
  }));
}
window.addEventListener('DOMContentLoaded', boot);
