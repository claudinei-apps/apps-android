var PERIODO = 30000;   // um sorteio a cada 30 segundos
var PRIMEIRO = 5000;   // primeiro sorteio 5 s depois de abrir
var H = 96;            // altura de cada algarismo na roleta
var PLANO = [[1500, 1400], [3000, 1400], [4500, 1500], [6200, 2700]];
var FESTA_MS = 3600;

var concurso = 58425;
try { concurso = parseInt(localStorage.getItem('mpx_sorteio_conc') || '58425', 10) || 58425; } catch (e) {}

function $(id) { return document.getElementById(id); }
function pad(n, l) { n = String(n); while (n.length < l) n = '0' + n; return n; }
function r0(n) { return Math.round(n * 10) / 10; }

/* ---------- encaixa o quadro 1568x883 na tela ---------- */
var ultimo = '';
function ajustar() {
  var w = window.innerWidth || document.documentElement.clientWidth;
  var h = window.innerHeight || document.documentElement.clientHeight;
  if (!w || !h) return;
  var chave = w + 'x' + h;
  if (chave === ultimo) return;
  ultimo = chave;
  var s = Math.min(w / 1568, h / 883);
  var st = $('stage');
  st.style.left = Math.floor((w - 1568 * s) / 2) + 'px';
  st.style.top = Math.floor((h - 883 * s) / 2) + 'px';
  st.style.webkitTransform = st.style.transform = 'scale(' + s + ')';
}
window.addEventListener('resize', ajustar);
window.addEventListener('load', ajustar);
setInterval(ajustar, 1000);
ajustar();

/* ---------- raios do fundo ---------- */
(function () {
  var cx = 1330, cy = 680, R = 3200, s = '<defs><radialGradient id="rg" gradientUnits="userSpaceOnUse" cx="1330" cy="680" r="1700">' +
    '<stop offset="0" stop-color="#fff" stop-opacity=".3"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>';
  for (var i = 0; i < 30; i++) {
    var a1 = (i * 12 - 2.5) * Math.PI / 180, a2 = (i * 12 + 2.5) * Math.PI / 180;
    s += '<polygon fill="url(#rg)" points="' + cx + ',' + cy + ' ' + r0(cx + Math.cos(a1) * R) + ',' + r0(cy + Math.sin(a1) * R) + ' ' +
      r0(cx + Math.cos(a2) * R) + ',' + r0(cy + Math.sin(a2) * R) + '"/>';
  }
  $('raiosF').innerHTML = s;
})();

/* ---------- trevos e moedas ---------- */
(function () {
  var s = '<defs>' +
    '<linearGradient id="gS" x1="0" x2="1" y1="0" y2="0"><stop offset="0" stop-color="#7a4c03"/><stop offset=".3" stop-color="#e0a51c"/>' +
    '<stop offset=".5" stop-color="#fff0a0"/><stop offset=".7" stop-color="#d4960f"/><stop offset="1" stop-color="#7a4c03"/></linearGradient>' +
    '<radialGradient id="gT" cx=".4" cy=".35" r=".7"><stop offset="0" stop-color="#fff8c8"/><stop offset=".5" stop-color="#f6c533"/><stop offset="1" stop-color="#b87a08"/></radialGradient>' +
    '<radialGradient id="gF" cx=".5" cy=".7" r=".8"><stop offset="0" stop-color="#c4ff7a"/><stop offset=".5" stop-color="#45c62e"/><stop offset="1" stop-color="#127218"/></radialGradient>' +
    '</defs>';
  function trevo(x, y, tam, rot) {
    var k = tam / 100, g = '<g transform="translate(' + x + ',' + y + ') rotate(' + rot + ') scale(' + k + ')">';
    g += '<path d="M0,0 Q14,34 6,68" stroke="#137a1a" stroke-width="8" fill="none" stroke-linecap="round"/>';
    for (var i = 0; i < 4; i++) {
      g += '<g transform="rotate(' + (45 + i * 90) + ')"><path d="M0,0 C-6,-8 -36,-18 -36,-34 C-36,-50 -16,-56 0,-41 C16,-56 36,-50 36,-34 C36,-18 6,-8 0,0Z" ' +
        'fill="url(#gF)" stroke="#0b5a10" stroke-width="3"/><path d="M0,-4 L0,-34" stroke="#9be86a" stroke-width="2.5" opacity=".7"/></g>';
    }
    return g + '</g>';
  }
  function moeda(cx, cy, rx, rot) {
    var ry = rx * 0.3, th = rx * 0.24, g = rot ? '<g transform="rotate(' + rot + ' ' + cx + ' ' + cy + ')">' : '<g>';
    g += '<path d="M' + r0(cx - rx) + ',' + r0(cy) + ' L' + r0(cx - rx) + ',' + r0(cy + th) + ' A' + rx + ',' + r0(ry) + ' 0 0 0 ' + r0(cx + rx) + ',' + r0(cy + th) +
      ' L' + r0(cx + rx) + ',' + r0(cy) + ' Z" fill="url(#gS)" stroke="#6b4204" stroke-width="1.5"/>';
    g += '<ellipse cx="' + r0(cx) + '" cy="' + r0(cy) + '" rx="' + rx + '" ry="' + r0(ry) + '" fill="url(#gT)" stroke="#9a6506" stroke-width="2"/>';
    g += '<ellipse cx="' + r0(cx) + '" cy="' + r0(cy) + '" rx="' + r0(rx * 0.74) + '" ry="' + r0(ry * 0.74) + '" fill="none" stroke="#c98f10" stroke-width="2"/>';
    return g + '</g>';
  }
  function pilha(cx, base, n, rx) {
    var g = '', th = rx * 0.24;
    for (var i = 0; i < n; i++) g += moeda(cx + ((i * 7) % 5) - 2, base - i * th, rx, 0);
    return g;
  }
  s += trevo(95, 85, 125, -20) + trevo(62, 292, 105, 15) + trevo(1512, 172, 112, 20);
  s += pilha(38, 850, 8, 56) + pilha(118, 878, 4, 52) + moeda(70, 862, 50, -18) + moeda(160, 872, 46, 14);
  s += pilha(1528, 722, 5, 52) + pilha(1490, 880, 9, 54) + pilha(1562, 880, 12, 52) + moeda(1438, 872, 46, 18);
  $('deco').innerHTML = s;
})();

/* ---------- logo 3D ---------- */
(function () {
  var lg = $('logo'), h = '';
  var txt = [['l1', 'MULT'], ['l2', 'PLI<span class="x">X</span>']];
  function camada(cls, dx, dy) {
    for (var i = 0; i < txt.length; i++) {
      h += '<div class="lt ' + txt[i][0] + ' ' + cls + '" style="margin-left:' + dx + 'px;margin-top:' + dy + 'px">' + txt[i][1] + '</div>';
    }
  }
  for (var p = 26; p >= 1; p--) camada(p > 20 ? 'ext esc' : 'ext', -Math.round(p * 1.3), Math.round(p * 2.6));
  camada('out', 0, 0);
  camada('mid', 0, 0);
  camada('front', 0, 0);
  lg.innerHTML = h;
})();

/* ---------- luzes nas molduras douradas ---------- */
function ponto(X, Y, W, Hh, R, s) {
  var a = W - 2 * R, b = Hh - 2 * R, q = Math.PI * R / 2, seg = [a, q, b, q, a, q, b, q], i = 0;
  while (i < 7 && s > seg[i]) { s -= seg[i]; i++; }
  var ang;
  switch (i) {
    case 0: return [X + R + s, Y];
    case 1: ang = (-90 + s / q * 90) * Math.PI / 180; return [X + W - R + R * Math.cos(ang), Y + R + R * Math.sin(ang)];
    case 2: return [X + W, Y + R + s];
    case 3: ang = (s / q * 90) * Math.PI / 180; return [X + W - R + R * Math.cos(ang), Y + Hh - R + R * Math.sin(ang)];
    case 4: return [X + W - R - s, Y + Hh];
    case 5: ang = (90 + s / q * 90) * Math.PI / 180; return [X + R + R * Math.cos(ang), Y + Hh - R + R * Math.sin(ang)];
    case 6: return [X, Y + Hh - R - s];
    default: ang = (180 + s / q * 90) * Math.PI / 180; return [X + R + R * Math.cos(ang), Y + R + R * Math.sin(ang)];
  }
}
function luzes(x, y, w, h, r, ins, gap, pular) {
  var X = x + ins, Y = y + ins, W = w - 2 * ins, Hh = h - 2 * ins, R = Math.min(r - ins, Hh / 2, W / 2);
  var L = 2 * (W - 2 * R) + 2 * (Hh - 2 * R) + 2 * Math.PI * R, n = Math.round(L / gap), out = '';
  for (var i = 0; i < n; i++) {
    var p = ponto(X, Y, W, Hh, R, i * L / n);
    if (pular && pular(p[0], p[1])) continue;
    out += '<div class="lz' + (i % 2 ? ' b' : '') + '" style="left:' + r0(p[0]) + 'px;top:' + r0(p[1]) + 'px"></div>';
  }
  return out;
}
$('luzes').innerHTML =
  luzes(590, 30, 890, 120, 60, 5, 46) +
  luzes(588, 168, 900, 535, 46, 5.5, 48) +
  luzes(36, 380, 548, 322, 36, 4.5, 44, function (px, py) { return py < 420 && px > 100 && px < 545; }) +
  luzes(112, 322, 420, 92, 46, 4, 40);

/* ---------- roletas ---------- */
var reels = [[], [], [], []];
(function () {
  for (var r = 0; r < 4; r++) {
    var box = $('k' + r);
    for (var j = r; j < 4; j++) {
      var d = document.createElement('div');
      d.className = 'reel idle';
      var s = '<div class="dash">-</div><div class="strip">';
      for (var i = 0; i < 30; i++) s += '<div>' + (i % 10) + '</div>';
      s += '</div>';
      d.innerHTML = s;
      box.appendChild(d);
      reels[j].push({ el: d, strip: d.lastChild, n: r * 4 + j });
    }
  }
})();

function setT(el, v) { el.style.webkitTransform = v; el.style.transform = v; }
function setTr(el, v) {
  if (v) { el.style.webkitTransition = '-webkit-transform ' + v; el.style.transition = 'transform ' + v; }
  else { el.style.webkitTransition = 'none'; el.style.transition = 'none'; }
}
function girar(o) {
  setTr(o.strip, null);
  setT(o.strip, '');
  var dur = (0.2 + (o.n % 3) * 0.04) + 's';
  o.strip.style.webkitAnimationDuration = dur;
  o.strip.style.animationDuration = dur;
  o.el.className = 'reel spin roll';
}
function parar(o, dig, ms) {
  setTr(o.strip, null);
  setT(o.strip, 'translateY(0px)');
  o.el.className = 'reel roll';
  void o.strip.offsetHeight;
  setTr(o.strip, ms + 'ms cubic-bezier(.1,.7,.25,1.03)');
  setT(o.strip, 'translateY(-' + ((20 + dig) * H) + 'px)');
  setTimeout(function () { o.el.className = 'reel lock'; }, ms);
}

/* ---------- som (gerado pelo aparelho, sem arquivos) ---------- */
var AC = null;
function ac() {
  if (AC === null) {
    try { var C = window.AudioContext || window.webkitAudioContext; AC = C ? new C() : false; } catch (e) { AC = false; }
  }
  if (AC && AC.state === 'suspended') { try { AC.resume(); } catch (e) {} }
  return AC || null;
}
function bip(f, dur, vol, tipo, quando) {
  var c = ac(); if (!c) return;
  try {
    var t = c.currentTime + (quando || 0);
    var o = c.createOscillator(), g = c.createGain();
    o.type = tipo || 'square';
    o.frequency.setValueAtTime(f, t);
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(c.destination);
    o.start(t); o.stop(t + dur + 0.03);
  } catch (e) {}
}
document.addEventListener('touchstart', ac);
document.addEventListener('click', ac);
var somGiro = null;
function tickLiga() { tickDesliga(); somGiro = setInterval(function () { bip(1300 + Math.random() * 500, 0.025, 0.035, 'square'); }, 75); }
function tickDesliga() { if (somGiro) clearInterval(somGiro); somGiro = null; }
function somTrava(j) { var b = 660 + j * 110; bip(b, 0.15, 0.14, 'triangle'); bip(b * 1.5, 0.25, 0.12, 'triangle', 0.07); }
function somFesta() {
  var n = [523, 659, 784, 1047, 784, 1047, 1319];
  for (var i = 0; i < n.length; i++) bip(n[i], 0.28, 0.13, 'triangle', i * 0.11);
  bip(523, 0.9, 0.08, 'triangle', 0.8); bip(659, 0.9, 0.08, 'triangle', 0.8); bip(1047, 0.9, 0.08, 'triangle', 0.8);
}

/* ---------- sorteio ---------- */
function digito() {
  var c = window.crypto || window.msCrypto;
  if (c && c.getRandomValues) {
    var a = new Uint32Array(1);
    do { c.getRandomValues(a); } while (a[0] >= 4294967290);
    return a[0] % 10;
  }
  return Math.floor(Math.random() * 10);
}
function mostrarData(d) {
  $('data').textContent = pad(d.getDate(), 2) + '/' + pad(d.getMonth() + 1, 2) + '/' + d.getFullYear();
  $('hora').textContent = pad(d.getHours(), 2) + ':' + pad(d.getMinutes(), 2) + ':' + pad(d.getSeconds(), 2);
}

var sorteando = false;
var proximo = Date.now() + PRIMEIRO;

function sortear() {
  sorteando = true;
  proximo = Date.now() + PERIODO;
  concurso++;
  try { localStorage.setItem('mpx_sorteio_conc', String(concurso)); } catch (e) {}
  $('conc').innerHTML = 'N&ordm; ' + concurso;
  mostrarData(new Date());
  var res = [digito(), digito(), digito(), digito()];
  for (var r = 0; r < 4; r++) $('r' + r).className = 'row';
  $('stage').className = 'rapido';
  $('logo').className = 'ab agita';
  for (var j = 0; j < 4; j++) for (var i = 0; i < reels[j].length; i++) girar(reels[j][i]);
  tickLiga();
  for (var k = 0; k < 4; k++) (function (j) {
    setTimeout(function () {
      for (var i = 0; i < reels[j].length; i++) parar(reels[j][i], res[j], PLANO[j][1]);
      setTimeout(function () { somTrava(j); }, PLANO[j][1]);
    }, PLANO[j][0]);
  })(k);
  setTimeout(tickDesliga, PLANO[3][0] + PLANO[3][1] - 300);
  setTimeout(function () { terminar(res); }, PLANO[3][0] + PLANO[3][1] + 400);
}

function terminar(res) {
  $('stage').className = '';
  $('logo').className = 'ab';
  festa(res);
  setTimeout(function () {
    for (var r = 0; r < 4; r++) (function (r) {
      setTimeout(function () { $('r' + r).className = 'row win'; }, r * 250);
    })(r);
    sorteando = false;
  }, FESTA_MS);
}

function festa(res) {
  var s = '';
  for (var i = 0; i < 4; i++) {
    var d = (0.25 + i * 0.15) + 's';
    s += '<span style="-webkit-animation-delay:' + d + ';animation-delay:' + d + '">' + res[i] + '</span>';
  }
  $('fnum').innerHTML = s;
  $('fcon').innerHTML = 'CONCURSO N&ordm; ' + concurso;
  var cores = ['#fff35a', '#c5e81f', '#ffffff', '#ff5a5a', '#5ad1ff', '#ffb02e', '#3f9c1f'];
  var c = '';
  for (var k = 0; k < 110; k++) {
    var w = 12 + Math.floor(Math.random() * 14), h = 8 + Math.floor(Math.random() * 10);
    var dur = (2 + Math.random() * 1.6).toFixed(2), del = (Math.random() * 0.9).toFixed(2);
    c += '<div class="cf" style="left:' + (700 + Math.floor(Math.random() * 2170)) + 'px;width:' + w + 'px;height:' + h + 'px;background:' +
      cores[k % cores.length] + ';-webkit-animation-duration:' + dur + 's;animation-duration:' + dur + 's;-webkit-animation-delay:' +
      del + 's;animation-delay:' + del + 's;border-radius:' + (k % 3 === 0 ? '50%' : '2px') + '"></div>';
  }
  $('conf').innerHTML = c;
  $('festa').className = 'ab on';
  somFesta();
  setTimeout(function () { $('festa').className = 'ab'; }, FESTA_MS);
  setTimeout(function () { $('conf').innerHTML = ''; }, FESTA_MS + 600);
}

(function () {
  var s = '';
  for (var i = 0; i < 18; i++) {
    var a1 = (i * 20 - 5) * Math.PI / 180, a2 = (i * 20 + 5) * Math.PI / 180;
    s += '<polygon fill="#fff58a" points="0,0 ' + Math.round(Math.cos(a1) * 900) + ',' + Math.round(Math.sin(a1) * 900) + ' ' +
      Math.round(Math.cos(a2) * 900) + ',' + Math.round(Math.sin(a2) * 900) + '"/>';
  }
  $('raios').innerHTML = s;
})();

function tique() {
  if (sorteando) return;
  if (Date.now() >= proximo) sortear();
}

$('conc').innerHTML = 'N&ordm; ' + (concurso + 1);
mostrarData(new Date(proximo));
setInterval(tique, 200);
