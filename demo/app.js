/* BitKeyboard demo — lapisan tampilan. Semua logika bahasa (kamus, trie,
   suggester, decoder glide) berasal dari kode Kotlin aplikasi Android yang
   dikompilasi ke JavaScript (demo/dist/bitcore.js). */
(() => {
  'use strict';

  const bridge = () => (window.bitcore && window.bitcore.demo) || null;
  const $ = (id) => document.getElementById(id);

  const el = {
    editor: $('editor'),
    keyboard: $('keyboard'),
    suggestions: $('suggestions'),
    trail: $('trailLine'),
    toast: $('toast'),
    statWords: $('statWords'),
    statLoad: $('statLoad'),
    statGlide: $('statGlide'),
    statSuggest: $('statSuggest'),
    presetResult: $('presetResult'),
    glideToggle: $('glideToggle'),
    mic: $('mic'),
    gear: $('gear'),
    appStatus: $('appStatus'),
  };

  const state = {
    ready: false,
    layer: 'letters',
    shift: false,
    caps: false,
    ctrl: false,
    alt: false,
    fn: false,
    emojiTab: 0,
    recent: [],
    glideEnabled: true,
    layout: null,
    keys: [],
    geom: { colW: 41, rowH: 45, letterTop: 0, top: 0, width: 400 },
    composingRange: null,
    gliding: false,
    glidePoints: [],
    down: null,
    timers: { longPress: 0, repeat: 0 },
    lastShiftTap: 0,
    metrics: { loadMs: 0, glideMs: 0, suggestMs: 0 },
  };

  // ---------------------------------------------------------------- editor

  function text() { return el.editor.value; }

  function setText(value, caret) {
    el.editor.value = value;
    const pos = caret === undefined ? value.length : caret;
    el.editor.setSelectionRange(pos, pos);
  }

  function insert(str) {
    const start = el.editor.selectionStart;
    const end = el.editor.selectionEnd;
    const value = text();
    const next = value.slice(0, start) + str + value.slice(end);
    setText(next, start + str.length);
    afterEdit();
  }

  function backspace() {
    const start = el.editor.selectionStart;
    const end = el.editor.selectionEnd;
    if (start !== end) {
      setText(text().slice(0, start) + text().slice(end), start);
    } else if (start > 0) {
      const value = text();
      const cut = start >= 2 && isSurrogatePair(value[start - 1], value[start]) ? 2 : 1;
      setText(value.slice(0, start - cut) + value.slice(start), start - cut);
    } else {
      return;
    }
    afterEdit();
  }

  function insertWord(word, trailingSpace = true) {
    if (!word) return;
    const caret = el.editor.selectionStart;
    const before = text().slice(0, caret);
    const needsSpace = before.length > 0 && !/\s$/.test(before);
    insert((needsSpace ? ' ' : '') + word + (trailingSpace ? ' ' : ''));
  }

  function isSurrogatePair(hi, lo) {
    if (!hi || !lo) return false;
    const a = hi.charCodeAt(0);
    const b = lo.charCodeAt(0);
    return a >= 0xd800 && a <= 0xdbff && b >= 0xdc00 && b <= 0xdfff;
  }

  function moveCursor(delta) {
    const pos = Math.max(0, Math.min(text().length, el.editor.selectionStart + delta));
    el.editor.setSelectionRange(pos, pos);
    refreshSuggestions();
  }

  function wordBeforeCaret() {
    const caret = el.editor.selectionStart;
    const value = text();
    let i = caret;
    while (i > 0 && /[\p{L}\p{M}'’-]/u.test(value[i - 1])) i -= 1;
    return { word: value.slice(i, caret), start: i, end: caret };
  }

  function previousWord(start) {
    let i = start;
    const value = text();
    while (i > 0 && /\s/.test(value[i - 1])) i -= 1;
    let end = i;
    while (i > 0 && !/\s/.test(value[i - 1])) i -= 1;
    return value.slice(i, end);
  }

  function replaceRange(start, end, str) {
    const value = text();
    setText(value.slice(0, start) + str + value.slice(end), start + str.length);
    afterEdit();
  }

  function afterEdit() {
    syncShiftFromContext();
    refreshSuggestions();
  }

  function syncShiftFromContext() {
    const value = text();
    const caret = el.editor.selectionStart;
    const before = value.slice(0, caret).replace(/\s+$/, '');
    if (!before) { setShift(true); return; }
    if (/[.!?…]["'”’)]?$/.test(before)) { setShift(true); return; }
    // Shift manual tetap aktif sampai dipakai atau diketik setelah spasi.
    if (state.shift && /\s$/.test(value.slice(0, caret))) setShift(false);
  }

  function setShift(on) {
    if (state.shift === on) return;
    state.shift = on;
    if (!on) state.caps = false;
    renderKeys();
  }

  // ----------------------------------------------------------- suggestions

  function refreshSuggestions() {
    const api = bridge();
    if (!api || !state.ready) return;
    const caret = el.editor.selectionStart;
    if (caret !== el.editor.selectionEnd) { renderSuggestions([]); return; }
    const { word, start } = wordBeforeCaret();
    const prev = previousWord(start);
    const t0 = performance.now();
    let raw = '';
    try {
      raw = api.suggest(word, prev, 4);
    } catch (e) { raw = ''; }
    const ms = performance.now() - t0;
    if (ms > 0) state.metrics.suggestMs = state.metrics.suggestMs === 0 ? ms : state.metrics.suggestMs * 0.8 + ms * 0.2;
    el.statSuggest.textContent = state.metrics.suggestMs.toFixed(2) + ' ms';
    const items = raw.split(';').filter(Boolean).map((entry) => {
      const parts = entry.split(':');
      return { word: parts[0], kind: parts[1] || '' };
    });
    state.composingRange = word ? { start, end: caret } : null;
    renderSuggestions(items, !word);
  }

  function renderSuggestions(items, predictive) {
    el.suggestions.textContent = '';
    if (!items.length) {
      const empty = document.createElement('span');
      empty.className = 'suggestion';
      empty.innerHTML = '<span class="empty">Ketik atau geser jari di keyboard…</span>';
      el.suggestions.appendChild(empty);
      return;
    }
    items.forEach((item, index) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'suggestion';
      b.textContent = item.word;
      if (predictive && index === 0) {
        const tag = document.createElement('span');
        tag.className = 'tag';
        tag.textContent = 'berikut';
        b.appendChild(tag);
      } else if (item.kind === 'CORRECTION') {
        const tag = document.createElement('span');
        tag.className = 'tag';
        tag.textContent = 'koreksi';
        b.appendChild(tag);
      }
      b.addEventListener('click', () => commitSuggestion(item.word));
      b.addEventListener('contextmenu', (ev) => {
        ev.preventDefault();
        const api = bridge();
        if (api) api.learnWord(item.word);
        showToast(`"${item.word}" disimpan ke kamus pribadi`);
      });
      el.suggestions.appendChild(b);
    });
  }

  function commitSuggestion(word) {
    const api = bridge();
    const range = state.composingRange;
    if (range) {
      replaceRange(range.start, range.end, word);
    } else {
      const caret = el.editor.selectionStart;
      insert((caret > 0 && !/\s/.test(text()[caret - 1]) ? ' ' : '') + word);
    }
    if (api) api.learnWord(word);
    state.composingRange = null;
    el.editor.focus();
  }

  function learnCurrentWord() {
    const api = bridge();
    const { word } = wordBeforeCaret();
    if (!api || !word) return;
    api.learnWord(word);
    showToast(`"${word}" disimpan ke kamus pribadi`);
  }

  // --------------------------------------------------------------- keyboard

  function layoutFor(layer) {
    const api = bridge();
    if (!api) return null;
    try {
      return JSON.parse(api.layoutJson(layer, state.emojiTab, state.recent.join(',')));
    } catch (e) {
      return null;
    }
  }

  function buildKeyboard() {
    const layout = layoutFor(state.layer);
    if (!layout) return;
    state.layout = layout;
    el.keyboard.querySelectorAll('.key').forEach((n) => n.remove());
    state.keys = [];
    layout.rows.forEach((row, rowIndex) => {
      const spec = { rowIndex, row, nodes: [] };
      row.keys.forEach((key) => {
        const node = document.createElement('button');
        node.type = 'button';
        node.className = 'key ' + styleClass(key);
        node.dataset.key = String(state.keys.length);
        node.appendChild(labelNode(key));
        appendCorners(node, key);
        el.keyboard.appendChild(node);
        state.keys.push({ key, node, rowIndex });
        spec.nodes.push(node);
      });
    });
    renderKeys();
    layoutGeometry();
    el.keyboard.style.minHeight = (state.geom.total + 2) + 'px';
  }

  function styleClass(key) {
    if (key.style === 'action') return 'action';
    if (key.style === 'space') return 'space';
    if (key.style === 'modifier') return 'mod';
    if (key.style === 'function') return 'fn';
    if (key.kind === 'text' && key.value && /\p{Extended_Pictographic}/u.test(key.value)) return 'emoji';
    return '';
  }

  function displayLabel(key) {
    let label = key.label || key.value || '';
    if (state.caps || (state.shift && /^[a-z]$/.test(key.value))) label = label.toUpperCase();
    return label;
  }

  function labelNode(key) {
    const span = document.createElement('span');
    span.className = 'lbl';
    const label = displayLabel(key);
    if (label.length > 2 && !/\p{Extended_Pictographic}/u.test(label)) span.classList.add('small');
    span.textContent = label;
    if (key.kind === 'text' && /\p{Extended_Pictographic}/u.test(label)) span.style.fontSize = '22px';
    return span;
  }

  function appendCorners(node, key) {
    const positions = ['nw', 'ne', 'sw', 'se'];
    key.corners.forEach((corner, index) => {
      if (!corner) return;
      const span = document.createElement('span');
      span.className = 'corner ' + positions[index];
      span.textContent = corner;
      node.appendChild(span);
    });
  }

  function renderKeys() {
    const active = { shift: state.caps || state.shift, ctrl: state.ctrl, alt: state.alt, fn: state.fn };
    state.keys.forEach(({ key, node }) => {
      node.classList.toggle('active', false);
      if (key.action === 'SHIFT' && active.shift) node.classList.add('active');
      if (key.action === 'CTRL' && active.ctrl) node.classList.add('active');
      if (key.action === 'ALT' && active.alt) node.classList.add('active');
      if (key.action === 'FN' && active.fn) node.classList.add('active');
      const old = node.querySelector('.lbl');
      if (old) {
        const fresh = labelNode(key);
        old.replaceWith(fresh);
      }
    });
  }

  function layoutGeometry() {
    const width = el.keyboard.clientWidth;
    if (!state.layout) return;
    const columns = state.layout.columns || 10;
    const colW = width / columns;
    const rowH = Math.max(34, Math.min(52, colW * 1.12));
    let top = 6;
    let letterTop = top;
    const nodesByRow = new Map();
    state.keys.forEach((entry) => {
      if (!nodesByRow.has(entry.rowIndex)) nodesByRow.set(entry.rowIndex, []);
      nodesByRow.get(entry.rowIndex).push(entry);
    });

    const rows = state.layout.rows;
    const isLetters = state.layer === 'letters';
    rows.forEach((row, rowIndex) => {
      const h = rowH * (row.height || 1);
      if (rowIndex === 0 && isLetters) letterTop = top;
      const total = row.keys.reduce((sum, k) => sum + k.width, 0);
      let x = (row.offset || 0) * colW;
      if (total < columns - 0.01) x += (columns - total) / 2 * colW;
      (nodesByRow.get(rowIndex) || []).forEach((entry) => {
        const w = entry.key.width * colW;
        const pad = 2.5;
        Object.assign(entry.node.style, {
          left: (x + pad) + 'px',
          top: (top + pad) + 'px',
          width: Math.max(8, w - pad * 2) + 'px',
          height: Math.max(8, h - pad * 2) + 'px',
          fontSize: keyFontSize(w, h) + 'px',
        });
        entry.node.dataset.h = String(h);
        entry.rect = { left: x + pad, top: top + pad, right: x + w - pad, bottom: top + h - pad };
        x += w;
      });
      top += h;
    });
    state.geom = { colW, rowH, letterTop, top, width, total: top };
  }

  function keyFontSize(w, h) {
    const base = Math.min(w * 0.52, h * 0.42);
    return Math.max(9, Math.min(19, base));
  }

  // ----------------------------------------------------------- pointer input

  function gridPoint(clientX, clientY) {
    const rect = el.keyboard.getBoundingClientRect();
    const x = (clientX - rect.left) / state.geom.colW;
    const y = (clientY - rect.top - state.geom.letterTop) / state.geom.rowH;
    return { x, y };
  }

  function keyAt(clientX, clientY) {
    const rect = el.keyboard.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    for (const entry of state.keys) {
      const r = entry.rect;
      if (r && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return entry;
    }
    return null;
  }

  function onDown(event) {
    const entry = keyAt(event.clientX, event.clientY);
    if (!entry) return;
    clearTimeout(state.timers.longPress);
    state.down = {
      entry,
      startX: event.clientX,
      startY: event.clientY,
      glided: false,
      corner: null,
      cancelled: false,
      spaceSteps: 0,
    };
    entry.node.classList.add('pressed');
    if (event.pointerId !== undefined && typeof el.keyboard.setPointerCapture === 'function') {
      try { el.keyboard.setPointerCapture(event.pointerId); } catch (e) { /* diabaikan */ }
    }
    event.preventDefault();

    const key = entry.key;
    if (key.longpress && key.longpress.length) {
      state.timers.longPress = setTimeout(() => {
        if (state.down && !state.down.glided) showLongPress(entry);
      }, 420);
    }
    if (key.action === 'BACKSPACE' || key.repeat) startRepeat(entry);
  }

  function onMove(event) {
    const down = state.down;
    if (!down) return;
    if (down.popupOpen) return;
    const dx = event.clientX - down.startX;
    const dy = event.clientY - down.startY;
    const distance = Math.hypot(dx, dy);
    const entry = down.entry;

    if (state.gliding) {
      const p = gridPoint(event.clientX, event.clientY);
      const last = state.glidePoints[state.glidePoints.length - 1];
      if (!last || Math.hypot(p.x - last.x, p.y - last.y) > 0.035) {
        state.glidePoints.push(p);
        drawTrail(state.glidePoints);
      }
      event.preventDefault();
      return;
    }

    // Geser pada spasi memindahkan kursor, seperti di aplikasi Android.
    if (entry.key.action === 'SPACE') {
      if (Math.abs(dx) > state.geom.colW * 0.55) {
        const steps = Math.round(dx / (state.geom.colW * 0.52));
        const delta = steps - down.spaceSteps;
        if (delta !== 0) {
          const clamped = Math.max(-4, Math.min(4, delta));
          for (let i = 0; i < Math.abs(clamped); i += 1) moveCursor(clamped > 0 ? 1 : -1);
          down.spaceSteps = steps;
        }
      }
      return;
    }

    const outside = !insideRect(entry, event.clientX, event.clientY);
    // Sudut hanya dipilih selama jari masih berada di atas tombol asal.
    const dir = distance > state.geom.colW * 0.28 && !outside
      ? cornerDirection(entry, event.clientX, event.clientY)
      : null;
    if (down.corner !== dir) {
      down.corner = dir;
      highlightCorners(entry, dir);
    }
    if (distance > state.geom.colW * 0.28) clearTimeout(state.timers.longPress);

    const canGlide = state.glideEnabled && state.layer === 'letters' &&
      entry.key.kind === 'char' && /^[a-z]$/.test(entry.key.value) &&
      !state.ctrl && !state.alt;
    if (canGlide && outside && distance > state.geom.colW * 0.55) {
      startGlide(entry, event);
    }
    if (state.gliding) event.preventDefault();
  }

  function onUp(event) {
    const down = state.down;
    clearTimeout(state.timers.longPress);
    stopRepeat();
    if (!down) return;
    const entry = down.entry;
    entry.node.classList.remove('pressed');
    clearCornerHighlight(entry);

    if (state.gliding) {
      state.glidePoints.push(gridPoint(event.clientX, event.clientY));
      finishGlide();
      state.down = null;
      return;
    }

    if (down.popupOpen) {
      state.down = null;
      return;
    }

    if (down.spaceSteps !== 0) {
      // Selesai menggeser kursor di spasi: jangan ketik spasi.
      state.down = null;
      return;
    }

    const distance = Math.hypot(event.clientX - down.startX, event.clientY - down.startY);
    let cornerValue = null;
    if (distance > state.geom.colW * 0.28 && insideRect(entry, event.clientX, event.clientY)) {
      const dir = cornerDirection(entry, event.clientX, event.clientY);
      const index = ['nw', 'ne', 'sw', 'se'].indexOf(dir);
      if (index >= 0) cornerValue = entry.key.corners[index] || null;
    }
    if (cornerValue) {
      typeChar(cornerValue);
    } else {
      tapKey(entry.key);
    }
    state.down = null;
  }

  function insideRect(entry, clientX, clientY) {
    const rect = el.keyboard.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    const r = entry.rect;
    return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
  }

  function cornerDirection(entry, clientX, clientY) {
    const rect = el.keyboard.getBoundingClientRect();
    const cx = (entry.rect.left + entry.rect.right) / 2 + rect.left;
    const cy = (entry.rect.top + entry.rect.bottom) / 2 + rect.top;
    const dx = clientX - cx;
    const dy = clientY - cy;
    if (Math.hypot(dx, dy) < state.geom.colW * 0.22) return null;
    let dir;
    if (dx < 0 && dy < 0) dir = 'nw';
    else if (dx >= 0 && dy < 0) dir = 'ne';
    else if (dx < 0) dir = 'sw';
    else dir = 'se';
    const index = ['nw', 'ne', 'sw', 'se'].indexOf(dir);
    return entry.key.corners[index] ? dir : null;
  }

  function highlightCorners(entry, dir) {
    entry.node.querySelectorAll('.corner').forEach((node) => {
      const pos = node.classList.contains(dir || '') ? 'hot' : '';
      node.classList.toggle('hot', pos === 'hot');
    });
  }

  function clearCornerHighlight(entry) {
    entry.node.querySelectorAll('.corner').forEach((n) => n.classList.remove('hot'));
  }

  function startRepeat(entry) {
    stopRepeat();
    state.timers.repeat = setInterval(() => {
      if (state.down && !state.gliding) backspace();
    }, 65);
  }

  function stopRepeat() {
    clearInterval(state.timers.repeat);
    state.timers.repeat = 0;
  }

  // ------------------------------------------------------------ glide input

  function startGlide(entry, event) {
    const down = state.down;
    if (!down) return;
    state.gliding = true;
    clearTimeout(state.timers.longPress);
    state.glidePoints = [];
    const p0 = gridPoint(down.startX, down.startY);
    const p1 = gridPoint(event.clientX, event.clientY);
    state.glidePoints.push(p0, p1);
    entry.node.classList.remove('pressed');
    drawTrail(state.glidePoints);
    el.appStatus.textContent = 'membaca gerakan…';
  }

  function finishGlide() {
    const api = bridge();
    state.gliding = false;
    const points = state.glidePoints.slice();
    clearTrail();
    el.appStatus.textContent = 'siap mengetik';
    if (!api) return;
    if (points.length < 3) return;
    let arc = 0;
    for (let i = 1; i < points.length; i += 1) {
      arc += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y);
    }
    if (arc < 1.2) return;
    const csv = points.map((p) => p.x.toFixed(3) + ',' + p.y.toFixed(3)).join(';');
    const t0 = performance.now();
    let raw = '';
    try { raw = api.decodeGlide(csv, 5); } catch (e) { raw = ''; }
    const ms = performance.now() - t0;
    state.metrics.glideMs = state.metrics.glideMs === 0 ? ms : state.metrics.glideMs * 0.7 + ms * 0.3;
    el.statGlide.textContent = state.metrics.glideMs.toFixed(0) + ' ms';
    const results = raw.split(';').filter(Boolean).map((entry) => {
      const at = entry.lastIndexOf(':');
      return { word: entry.slice(0, at), score: Number(entry.slice(at + 1)) };
    });
    if (!results.length) {
      showToast('Glide tidak dikenali, coba lagi');
      return;
    }
    insertWord(results[0].word);
    const alternatives = results.slice(1, 3).map((r) => r.word).join(', ');
    showToast(alternatives ? `Glide: ${results[0].word} · alternatif: ${alternatives}` : `Glide: ${results[0].word}`);
  }

  function drawTrail(points) {
    const geom = state.geom;
    const svgRect = el.keyboard.getBoundingClientRect();
    const pts = points.map((p) => {
      const x = p.x * geom.colW;
      const y = geom.letterTop + p.y * geom.rowH;
      return x.toFixed(1) + ',' + Math.max(0, y).toFixed(1);
    }).join(' ');
    el.trail.setAttribute('points', pts);
    if (el.trail.parentElement) {
      el.trail.parentElement.setAttribute('viewBox', `0 0 ${svgRect.width} ${svgRect.height}`);
    }
  }

  function clearTrail() {
    el.trail.setAttribute('points', '');
    state.glidePoints = [];
  }

  // ------------------------------------------------------------ dispatch key

  function tapKey(key) {
    if (!key) return;
    state.composingRange = null;
    switch (key.kind) {
      case 'char':
        typeChar(key.value);
        return;
      case 'text':
        if (key.value) {
          insert(key.value);
          rememberEmoji(key.value);
        }
        return;
      case 'event':
        handleEvent(key.event);
        return;
      case 'action':
        handleAction(key.action, key.payload);
        return;
      default:
        return;
    }
  }

  function typeChar(value) {
    if (!value) return;
    const ch = (state.caps || state.shift) && /^[a-z]$/.test(value) ? value.toUpperCase() : value;
    if (state.ctrl) {
      showToast(`Ctrl+${ch.toUpperCase()} (shortcut editor)`);
      state.ctrl = false;
      renderKeys();
      return;
    }
    insert(ch);
    if (state.shift && !state.caps) setShift(false);
  }

  function handleEvent(name) {
    switch (name) {
      case 'TAB': insert('\t'); break;
      case 'ESCAPE': showToast('Esc dikirim (terminal)'); break;
      case 'DPAD_LEFT': moveCursor(-1); break;
      case 'DPAD_RIGHT': moveCursor(1); break;
      case 'DPAD_UP': moveCursor(-9999); break;
      case 'DPAD_DOWN': moveCursor(9999); break;
      case 'MOVE_HOME': el.editor.setSelectionRange(0, 0); refreshSuggestions(); break;
      default: showToast('Tombol ' + name);
    }
  }

  function handleAction(action, payload) {
    switch (action) {
      case 'SHIFT': toggleShift(); break;
      case 'CAPS_LOCK': state.caps = !state.caps; state.shift = state.caps; renderKeys(); break;
      case 'CTRL': state.ctrl = !state.ctrl; renderKeys(); break;
      case 'ALT': state.alt = !state.alt; renderKeys(); break;
      case 'FN': state.fn = !state.fn; renderKeys(); break;
      case 'BACKSPACE': backspace(); break;
      case 'SPACE': commitSpace(); break;
      case 'ENTER': commitSpace(true); insert('\n'); break;
      case 'SWITCH_LAYER':
        if (payload === 'letters') {
          state.layer = 'letters';
          state.shift = false;
          state.caps = false;
        } else {
          state.layer = payload || 'symbols';
        }
        buildKeyboard();
        break;
      case 'EMOJI_TAB':
        state.emojiTab = Number(payload) || 0;
        buildKeyboard();
        break;
      case 'OPEN_SETTINGS':
        showToast('Di aplikasi Android tombol ini membuka pengaturan BitKeyboard');
        break;
      case 'LEARN_WORD': learnCurrentWord(); break;
      default: showToast(action);
    }
  }

  function toggleShift() {
    const now = Date.now();
    if (now - state.lastShiftTap < 320) {
      state.caps = !state.caps;
      state.shift = state.caps;
    } else {
      state.shift = !state.shift;
      if (!state.shift) state.caps = false;
    }
    state.lastShiftTap = now;
    renderKeys();
  }

  function commitSpace(newline) {
    const api = bridge();
    const { word, start, end } = wordBeforeCaret();
    if (word && api) {
      if (api.isKnown(word)) {
        api.learnWord(word);
      } else {
        const fixed = api.autocorrect(word);
        if (fixed && fixed !== word) {
          replaceRange(start, end, fixed);
          showToast(`"${word}" → "${fixed}"`);
        }
      }
    }
    if (!newline) insert(' ');
    else if (state.composingRange) state.composingRange = null;
    refreshSuggestions();
  }

  function rememberEmoji(value) {
    if (!/\p{Extended_Pictographic}/u.test(value)) return;
    state.recent = [value].concat(state.recent.filter((e) => e !== value)).slice(0, 16);
  }

  // --------------------------------------------------------------- longpress

  function showLongPress(entry) {
    hideLongPress();
    const items = entry.key.longpress.filter(Boolean);
    if (!items.length) return;
    const r = entry.rect;
    const pop = document.createElement('div');
    pop.className = 'longpop';
    pop.id = 'longpop';
    items.slice(0, 8).forEach((item) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = item;
      b.addEventListener('pointerdown', (ev) => {
        ev.stopPropagation();
        typeChar(item);
        hideLongPress();
        state.down = null;
      });
      pop.appendChild(b);
    });
    el.keyboard.appendChild(pop);
    const width = pop.offsetWidth || 40 * items.length;
    const keyCenter = r.left + (r.right - r.left) / 2;
    pop.style.left = Math.max(4, Math.min(keyCenter - width / 2, el.keyboard.clientWidth - width - 4)) + 'px';
    pop.style.top = Math.max(2, r.top - 52) + 'px';
    if (state.down) state.down.popupOpen = true;
    clearTimeout(state.timers.longPress);
  }

  function hideLongPress() {
    const pop = $('longpop');
    if (pop) pop.remove();
  }

  // ------------------------------------------------------------------ preset

  const QWERTY = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];
  const ROW_OFFSET = [0, 0.5, 1.5];

  function letterCenter(ch) {
    for (let r = 0; r < QWERTY.length; r += 1) {
      const i = QWERTY[r].indexOf(ch);
      if (i >= 0) return { x: ROW_OFFSET[r] + i + 0.5, y: r + 0.5 };
    }
    return null;
  }

  function synthPath(word) {
    const letters = [...word].filter((c) => /[a-z]/.test(c));
    if (letters.length < 2) return null;
    const points = [];
    let prev = null;
    for (const ch of letters) {
      const p = letterCenter(ch);
      if (!p) continue;
      if (prev) {
        const steps = 4;
        for (let s = 1; s < steps; s += 1) {
          const t = s / steps;
          const jitter = (Math.sin(s * 2.4 + letters.length) * 0.06);
          points.push({
            x: prev.x + (p.x - prev.x) * t + jitter,
            y: prev.y + (p.y - prev.y) * t + Math.cos(s * 1.7) * 0.05,
          });
        }
      }
      const wobble = Math.sin(points.length) * 0.05;
      points.push({ x: p.x + wobble, y: p.y + wobble * 0.6 });
      prev = p;
    }
    return points.length >= 3 ? points : null;
  }

  function animateTrail(points, done) {
    const shown = [];
    let i = 0;
    const step = () => {
      if (i >= points.length) { window.setTimeout(() => { clearTrail(); done(); }, 260); return; }
      shown.push(points[i]);
      drawTrail(shown);
      i += 1;
      window.setTimeout(step, 18);
    };
    step();
  }

  async function runPreset(word) {
    const api = bridge();
    if (!api) return;
    const buttons = document.querySelectorAll('.preset');
    buttons.forEach((b) => { b.disabled = true; });
    const words = word.split(/\s+/);
    const lines = [];
    for (let index = 0; index < words.length; index += 1) {
      const part = words[index].toLowerCase();
      const path = synthPath(part);
      if (!path) continue;
      await new Promise((resolve) => animateTrail(path, resolve));
      const csv = path.map((p) => p.x.toFixed(3) + ',' + p.y.toFixed(3)).join(';');
      const t0 = performance.now();
      const raw = api.decodeGlide(csv, 3);
      const ms = performance.now() - t0;
      state.metrics.glideMs = state.metrics.glideMs === 0 ? ms : state.metrics.glideMs * 0.7 + ms * 0.3;
      el.statGlide.textContent = state.metrics.glideMs.toFixed(0) + ' ms';
      const results = raw.split(';').filter(Boolean).map((entry) => {
        const at = entry.lastIndexOf(':');
        return { word: entry.slice(0, at), score: Number(entry.slice(at + 1)) };
      });
      if (!results.length) continue;
      insertWord(results[0].word);
      const alts = results.slice(1).map((r) => r.word).join(', ');
      const same = results[0].word === part;
      lines.push((same ? `terbaca <b>${results[0].word}</b>` : `ditebak <b>${results[0].word}</b> (digeser: ${part})`) +
        (alts ? `<span class="alt">alternatif: ${alts}</span>` : ''));
      await new Promise((r) => setTimeout(r, 220));
    }
    if (lines.length) {
      el.presetResult.innerHTML = lines.join('<br />');
    }
    buttons.forEach((b) => { b.disabled = false; });
  }

  // ------------------------------------------------------------------ misc

  let toastTimer = 0;
  function showToast(message) {
    el.toast.textContent = message;
    el.toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.toast.classList.remove('show'), 2600);
  }

  // ------------------------------------------------------------------ voice

  function setupVoice() {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) {
      el.mic.addEventListener('click', () => {
        showToast('Browser ini tidak punya Web Speech API. Di aplikasi Android, tombol ini memanggil RecognizerIntent id-ID.');
      });
      return;
    }
    let recognition = null;
    el.mic.addEventListener('click', () => {
      if (recognition) {
        recognition.stop();
        recognition = null;
        el.mic.classList.remove('listening');
        return;
      }
      recognition = new Recognition();
      recognition.lang = 'id-ID';
      recognition.interimResults = false;
      recognition.continuous = false;
      el.mic.classList.add('listening');
      el.appStatus.textContent = 'mendengarkan…';
      recognition.onresult = (event) => {
        const said = event.results[0][0].transcript;
        insert((text() && !/\s$/.test(text()) && !/^\s/.test(said) ? ' ' : '') + said);
        showToast('Dikenali: ' + said);
      };
      recognition.onerror = (event) => {
        showToast('Suara gagal: ' + event.error);
        el.mic.classList.remove('listening');
        recognition = null;
      };
      recognition.onend = () => {
        el.mic.classList.remove('listening');
        el.appStatus.textContent = 'siap mengetik';
        recognition = null;
      };
      try { recognition.start(); } catch (e) { showToast('Mikrofon tidak tersedia'); }
    });
  }

  function setupStats() {
    const api = bridge();
    if (!api) return;
    el.statWords.textContent = api.dictionarySize().toLocaleString('id-ID');
    el.statGlide.textContent = '—';
  }

  async function boot() {
    el.editor.value = '';
    el.editor.focus();
    document.addEventListener('pointerdown', hideLongPress, true);
    el.keyboard.addEventListener('pointerdown', (ev) => {
      hideLongPress();
      onDown(ev);
    });
    el.keyboard.addEventListener('pointermove', onMove);
    el.keyboard.addEventListener('pointerup', onUp);
    el.keyboard.addEventListener('pointercancel', () => {
      if (state.down) state.down.node && state.down.node.classList.remove('pressed');
      state.down = null;
      state.gliding = false;
      clearTrail();
      stopRepeat();
      renderKeys();
    });
    el.editor.addEventListener('input', afterEdit);
    el.editor.addEventListener('click', refreshSuggestions);
    el.editor.addEventListener('keyup', (ev) => {
      if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(ev.key)) refreshSuggestions();
    });
    el.gear.addEventListener('click', () => {
      showToast('Demo: di Android ada pengaturan tema, tinggi tombol, dan bahasa suara.');
    });
    el.glideToggle.addEventListener('change', () => {
      state.glideEnabled = el.glideToggle.checked;
      showToast(state.glideEnabled ? 'Glide typing aktif' : 'Glide typing nonaktif');
    });
    document.querySelectorAll('.preset').forEach((button) => {
      button.addEventListener('click', () => runPreset(button.dataset.word || 'aku'));
    });
    window.addEventListener('resize', () => {
      layoutGeometry();
    });
    setupVoice();
    buildKeyboard();
    syncShiftFromContext();
    await loadDictionary();
  }

  async function loadDictionary() {
    const api = bridge();
    if (!api) {
      el.statWords.textContent = 'gagal';
      showToast('dist/bitcore.js belum dibangun. Jalankan tools/build_demo.sh');
      return;
    }
    el.appStatus.textContent = 'memuat kamus…';
    try {
      const response = await fetch('dist/dictionary.txt', { cache: 'force-cache' });
      if (!response.ok) throw new Error(String(response.status));
      const body = await response.text();
      const t0 = performance.now();
      const count = api.loadDictionary(body);
      const ms = performance.now() - t0;
      state.ready = true;
      state.metrics.loadMs = ms;
      el.statWords.textContent = count.toLocaleString('id-ID');
      el.statLoad.textContent = ms.toFixed(0) + ' ms';
      el.statSuggest.textContent = '—';
      el.appStatus.textContent = 'siap mengetik';
      el.editor.focus();
      refreshSuggestions();
    } catch (error) {
      el.statWords.textContent = 'gagal';
      showToast('Kamus tidak bisa dimuat: ' + error.message);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
