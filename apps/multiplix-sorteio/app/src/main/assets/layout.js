/* Ajusta o quadro para preencher a tela inteira (sem faixas nas laterais). */
(function () {
  function g(id) { return document.getElementById(id); }
  function pos(el, l, w) {
    if (!el) return;
    if (l !== null) el.style.left = Math.round(l) + 'px';
    if (w !== null) el.style.width = Math.round(w) + 'px';
  }
  var chave = '';
  function aplicar() {
    var w = window.innerWidth || document.documentElement.clientWidth;
    var h = window.innerHeight || document.documentElement.clientHeight;
    if (!w || !h) return;
    var k = w + 'x' + h;
    if (k === chave) return;
    chave = k;
    var s = Math.min(w / 1568, h / 883);
    var W = w / s;
    var E = Math.max(0, W - 1568);
    var st = g('stage');
    st.style.width = Math.round(1568 + E) + 'px';
    st.style.left = Math.floor((w - (1568 + E) * s) / 2) + 'px';
    st.style.top = Math.floor((h - 883 * s) / 2) + 'px';
    st.style.webkitTransform = st.style.transform = 'scale(' + s + ')';

    var sL = 0.15 * E, D = 0.7 * E;
    pos(g('esq'), sL, null);
    pos(g('tit'), 590 + sL, 890 + D);
    pos(g('bd'), 588 + sL, 900 + D);
    for (var i = 0; i < 4; i++) pos(g('r' + i), 634 + sL, 822 + D);
    pos(g('p1'), 150 + 0.15 * E, null);
    pos(g('p2'), 600 + 0.5 * E, null);
    pos(g('p3'), 1034 + 0.85 * E, null);

    g('luzes').innerHTML =
      luzes(590 + sL, 30, 890 + D, 120, 60, 5, 46) +
      luzes(588 + sL, 168, 900 + D, 535, 46, 5.5, 48);
    g('luzL').innerHTML =
      luzes(36, 380, 548, 322, 36, 4.5, 44, function (px, py) { return py < 420 && px > 100 && px < 545; }) +
      luzes(112, 322, 420, 92, 46, 4, 40);

    pos(g('fbox'), 1000 + E / 2, null);
    pos(g('raios'), 884 + E / 2, null);
    g('festa').style.background = 'radial-gradient(circle at ' + Math.round(1000 + W / 2) + 'px 940px,rgba(20,90,30,.72),rgba(5,30,10,.93) 1000px)';
  }
  window.addEventListener('resize', function () { setTimeout(aplicar, 30); });
  window.addEventListener('load', function () { setTimeout(aplicar, 30); });
  setInterval(function () { chave = chave; aplicar(); }, 700);
  aplicar();
  // a rotina antiga recoloca o quadro quando o tamanho muda; reaplica logo depois
  setInterval(function () {
    var st = g('stage');
    if (st.style.width === '' || parseInt(st.style.width, 10) < 1568) chave = '';
  }, 300);
})();
