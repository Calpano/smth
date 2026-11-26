/*
 * uxlens DOM probe.
 *
 * Runs inside the page and returns one flat record per *visually relevant* element,
 * with the exact geometry and computed style a designer would measure by hand:
 * box in document coordinates, type metrics, resolved foreground/background colors
 * (composited through transparent ancestors), text line metrics and clipping state.
 *
 * Everything derivable from pure geometry (alignment, gaps, isolation, overlap) is
 * deliberately NOT computed here -- Java does that, where it is testable.
 */
(() => {
  const MAX_ELEMENTS = 3000;
  const MIN_SIDE = 2; // px; smaller boxes carry no visual weight

  const INTERACTIVE_TAGS = new Set(['A', 'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'SUMMARY', 'LABEL']);
  const INTERACTIVE_ROLES = new Set(['button', 'link', 'checkbox', 'radio', 'switch', 'tab',
    'menuitem', 'option', 'textbox', 'combobox', 'slider', 'searchbox']);
  const HEADING_TAGS = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);

  /* ---------- color ---------- */

  function parseColor(s) {
    if (!s) return null;
    s = s.trim();
    if (s === 'transparent') return { r: 0, g: 0, b: 0, a: 0 };
    let m = s.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)\s*(?:[,/]\s*([\d.%]+)\s*)?\)$/i);
    if (m) {
      let a = m[4] === undefined ? 1 : (m[4].endsWith('%') ? parseFloat(m[4]) / 100 : parseFloat(m[4]));
      return { r: +m[1], g: +m[2], b: +m[3], a: a };
    }
    m = s.match(/^#([0-9a-f]{3,8})$/i);
    if (m) {
      const h = m[1];
      const ex = h.length <= 4 ? h.split('').map(c => c + c).join('') : h;
      return {
        r: parseInt(ex.slice(0, 2), 16), g: parseInt(ex.slice(2, 4), 16), b: parseInt(ex.slice(4, 6), 16),
        a: ex.length >= 8 ? parseInt(ex.slice(6, 8), 16) / 255 : 1
      };
    }
    // color(), lab(), oklch() etc. -- let the browser normalize it for us
    try {
      const probe = document.createElement('span');
      probe.style.color = s;
      document.body.appendChild(probe);
      const norm = getComputedStyle(probe).color;
      document.body.removeChild(probe);
      if (norm && norm !== s) return parseColor(norm);
    } catch (e) { /* ignore */ }
    return null;
  }

  function over(fg, bg) { // alpha-composite fg over opaque-ish bg
    const a = fg.a;
    return {
      r: fg.r * a + bg.r * (1 - a),
      g: fg.g * a + bg.g * (1 - a),
      b: fg.b * a + bg.b * (1 - a),
      a: 1
    };
  }

  function css(c) { return c ? `rgba(${Math.round(c.r)},${Math.round(c.g)},${Math.round(c.b)},${+c.a.toFixed(3)})` : null; }

  /* Walks ancestors to find what is actually painted behind this element.
     Returns {color, uncertain} -- uncertain when a background-image/gradient intervenes. */
  function effectiveBackground(el) {
    let acc = null;          // accumulated semi-transparent layers, front to back
    let uncertain = false;
    let node = el;
    while (node && node.nodeType === 1) {
      const st = getComputedStyle(node);
      if (st.backgroundImage && st.backgroundImage !== 'none') uncertain = true;
      const c = parseColor(st.backgroundColor);
      if (c && c.a > 0) {
        acc = acc === null ? c : over(acc, c);
        if (acc.a >= 0.999) return { color: acc, uncertain };
      }
      node = node.parentElement;
    }
    const white = { r: 255, g: 255, b: 255, a: 1 };
    return { color: acc === null ? white : over(acc, white), uncertain };
  }

  /* ---------- selectors ---------- */

  function shortSelector(el) {
    if (el.id) {
      const sel = '#' + CSS.escape(el.id);
      try { if (document.querySelectorAll(sel).length === 1) return sel; } catch (e) { /* ignore */ }
    }
    const parts = [];
    let node = el;
    while (node && node.nodeType === 1 && parts.length < 6) {
      let part = node.tagName.toLowerCase();
      if (node.id) { parts.unshift('#' + CSS.escape(node.id)); break; }
      const parent = node.parentElement;
      if (parent) {
        const sameTag = Array.from(parent.children).filter(c => c.tagName === node.tagName);
        if (sameTag.length > 1) part += ':nth-of-type(' + (sameTag.indexOf(node) + 1) + ')';
      }
      parts.unshift(part);
      node = parent;
      if (node === document.body) { parts.unshift('body'); break; }
    }
    return parts.join(' > ');
  }

  /* Which margins were written as `auto`. getComputedStyle resolves those to a used
     pixel value, so `margin: 0 auto` looks like a deliberate 231px margin unless the
     keyword is read from the Typed OM. */
  const AUTO_SIDES = [['marginAutoTop', 'margin-top'], ['marginAutoRight', 'margin-right'],
                      ['marginAutoBottom', 'margin-bottom'], ['marginAutoLeft', 'margin-left']];

  function autoMargins(el) {
    const out = {};
    for (const [key] of AUTO_SIDES) out[key] = false;
    if (!el.computedStyleMap) return out;
    try {
      const map = el.computedStyleMap();
      for (const [key, prop] of AUTO_SIDES) {
        const value = map.get(prop);
        out[key] = !!value && value.value === 'auto';
      }
    } catch (e) { /* Typed OM unavailable; px values are all we have */ }
    return out;
  }

  /* ---------- text ---------- */

  function ownText(el) {
    let s = '';
    for (const n of el.childNodes) if (n.nodeType === 3) s += n.nodeValue;
    return s.replace(/\s+/g, ' ').trim();
  }

  /* Line boxes of the element's own text, via Range rects. Gives real line count,
     real line width (for measure / line-length checks) and orphan detection. */
  function lineMetrics(el, scrollX, scrollY) {
    try {
      const r = document.createRange();
      r.selectNodeContents(el);
      const rects = Array.from(r.getClientRects()).filter(b => b.width > 0 && b.height > 0);
      if (!rects.length) return null;
      // cluster rects into lines by vertical midpoint
      const lines = [];
      for (const b of rects) {
        const mid = b.top + b.height / 2;
        let line = lines.find(l => Math.abs(l.mid - mid) < Math.max(4, b.height * 0.4));
        if (!line) { line = { mid, top: b.top, bottom: b.bottom, left: b.left, right: b.right }; lines.push(line); }
        else {
          line.left = Math.min(line.left, b.left); line.right = Math.max(line.right, b.right);
          line.top = Math.min(line.top, b.top); line.bottom = Math.max(line.bottom, b.bottom);
        }
      }
      lines.sort((a, b) => a.mid - b.mid);
      return {
        lineCount: lines.length,
        firstLineWidth: +(lines[0].right - lines[0].left).toFixed(2),
        lastLineWidth: +(lines[lines.length - 1].right - lines[lines.length - 1].left).toFixed(2),
        maxLineWidth: +Math.max(...lines.map(l => l.right - l.left)).toFixed(2),
        firstBaselineY: +(lines[0].bottom + scrollY).toFixed(2),
        firstLineLeft: +(lines[0].left + scrollX).toFixed(2)
      };
    } catch (e) { return null; }
  }

  /* ---------- main walk ---------- */

  const out = [];
  const idOf = new Map();
  /* Whether an element sits in a viewport-pinned layer. `position` is per element, so a
     static child of a fixed <footer> reports "static" while painting in a layer that does
     not live in document space at all. Carried down the tree: the walker is in document
     order, so a parent is always recorded before its children. */
  const fixedLayer = new WeakMap();
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
  const sx = window.scrollX, sy = window.scrollY;

  let node = document.body;
  let count = 0;
  do {
    if (count++ > MAX_ELEMENTS) break;
    const el = node;
    const tag = el.tagName;
    if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'TEMPLATE' ||
        tag === 'HEAD' || tag === 'META' || tag === 'LINK' || tag === 'TITLE') continue;

    const st = getComputedStyle(el);
    const inFixedLayer = (el.parentElement && fixedLayer.get(el.parentElement) === true)
        || st.position === 'fixed' || st.position === 'sticky';
    fixedLayer.set(el, inFixedLayer);

    if (st.display === 'none' || st.visibility === 'hidden' || st.visibility === 'collapse') continue;
    const opacity = parseFloat(st.opacity);
    if (!(opacity > 0.01)) continue;

    const b = el.getBoundingClientRect();
    if (b.width < MIN_SIDE || b.height < MIN_SIDE) continue;

    const text = ownText(el);
    const role = el.getAttribute('role');
    /* Two different questions. `actionable` is whether the element really does something:
       a link with an href, a form control, an explicit role. `interactive` adds the visual
       hint of a pointer cursor. Comparing the two is how a mismatch between what a thing
       looks like and what it does becomes measurable. */
    const actionable = (tag === 'A' && el.hasAttribute('href'))
      || (INTERACTIVE_TAGS.has(tag) && tag !== 'A')
      || (role && INTERACTIVE_ROLES.has(role))
      || el.hasAttribute('onclick')
      || (el.hasAttribute('tabindex') && el.tabIndex >= 0);
    const interactive = actionable || st.cursor === 'pointer';

    const fg = parseColor(st.color);
    const bgRes = effectiveBackground(el);
    const ownBg = parseColor(st.backgroundColor);

    const id = out.length;
    idOf.set(el, id);
    // nearest *kept* ancestor, so skipped wrappers do not break the tree
    let parentId = -1;
    for (let p = el.parentElement; p; p = p.parentElement) {
      if (idOf.has(p)) { parentId = idOf.get(p); break; }
    }

    const rec = {
      id, parentId,
      tag: tag.toLowerCase(),
      selector: shortSelector(el),
      role: role || null,
      ariaLabel: el.getAttribute('aria-label') || null,
      text: text.length > 200 ? text.slice(0, 200) + '…' : text,
      textLength: text.length,

      // geometry, document coordinates
      x: +(b.left + sx).toFixed(2), y: +(b.top + sy).toFixed(2),
      w: +b.width.toFixed(2), h: +b.height.toFixed(2),

      // box model
      marginTop: parseFloat(st.marginTop) || 0, marginRight: parseFloat(st.marginRight) || 0,
      marginBottom: parseFloat(st.marginBottom) || 0, marginLeft: parseFloat(st.marginLeft) || 0,
      padTop: parseFloat(st.paddingTop) || 0, padRight: parseFloat(st.paddingRight) || 0,
      padBottom: parseFloat(st.paddingBottom) || 0, padLeft: parseFloat(st.paddingLeft) || 0,
      borderTop: parseFloat(st.borderTopWidth) || 0, borderLeft: parseFloat(st.borderLeftWidth) || 0,
      borderBottom: parseFloat(st.borderBottomWidth) || 0, borderRight: parseFloat(st.borderRightWidth) || 0,
      borderColor: css(parseColor(st.borderTopColor)),
      borderStyle: st.borderTopStyle,
      borderRadius: parseFloat(st.borderTopLeftRadius) || 0,
      gap: st.gap && st.gap !== 'normal' ? (parseFloat(st.rowGap) || 0) : 0,
      columnGap: parseFloat(st.columnGap) || 0,

      // layout participation
      display: st.display,
      position: st.position,
      /* True when this element paints in a viewport-pinned layer, so its document
         coordinates say nothing about what it sits on top of. */
      fixedLayer: inFixedLayer,
      flexDirection: st.display.includes('flex') ? st.flexDirection : null,
      zIndex: st.zIndex === 'auto' ? 0 : (parseInt(st.zIndex, 10) || 0),
      opacity: +opacity.toFixed(3),
      overflowX: st.overflowX, overflowY: st.overflowY,
      textAlign: st.textAlign,
      cursor: st.cursor,
      transitionDuration: st.transitionDuration,

      // type
      fontFamily: (st.fontFamily || '').split(',')[0].replace(/["']/g, '').trim(),
      fontSize: +parseFloat(st.fontSize).toFixed(2),
      fontWeight: parseInt(st.fontWeight, 10) || 400,
      fontStyle: st.fontStyle,
      lineHeight: st.lineHeight === 'normal' ? -1 : +parseFloat(st.lineHeight).toFixed(2),
      letterSpacing: st.letterSpacing === 'normal' ? 0 : +parseFloat(st.letterSpacing).toFixed(2),
      textTransform: st.textTransform,
      textDecorationLine: st.textDecorationLine,

      // color
      color: css(fg),
      backgroundColor: css(ownBg),
      effectiveBackground: css(bgRes.color),
      backgroundUncertain: bgRes.uncertain,
      hasBackgroundImage: st.backgroundImage !== 'none',
      boxShadow: st.boxShadow !== 'none' ? st.boxShadow : null,

      // semantics / state
      interactive: !!interactive,
      actionable: !!actionable,
      heading: HEADING_TAGS.has(tag) ? parseInt(tag.slice(1), 10) : 0,
      focusable: el.tabIndex >= 0,
      disabled: !!el.disabled,

      // clipping / scrolling
      scrollWidth: el.scrollWidth, scrollHeight: el.scrollHeight,
      clientWidth: el.clientWidth, clientHeight: el.clientHeight,
      textClipped: (st.overflowY === 'hidden' || st.overflowY === 'clip' ||
                    st.textOverflow === 'ellipsis' || st.webkitLineClamp !== 'none')
                   && el.scrollHeight > el.clientHeight + 1,
      childCount: el.children.length,
      /* How many boxes this element actually paints. An inline element that wraps paints
         one per line, and its bounding rect is their union -- a rectangle covering text
         that belongs to its neighbours. Anything comparing geometry has to know. */
      clientRectCount: el.getClientRects().length,
      lines: null,
      img: null
    };

    Object.assign(rec, autoMargins(el));

    if (text) rec.lines = lineMetrics(el, sx, sy);

    if (tag === 'IMG') {
      rec.img = {
        naturalWidth: el.naturalWidth || 0, naturalHeight: el.naturalHeight || 0,
        alt: el.getAttribute('alt'),
        loaded: el.complete && el.naturalWidth > 0,
        objectFit: st.objectFit,
        src: (el.currentSrc || el.src || '').slice(0, 300)
      };
    } else if (tag === 'SVG' || tag === 'svg') {
      rec.img = { naturalWidth: 0, naturalHeight: 0, alt: null, loaded: true, objectFit: null, src: 'inline-svg' };
    }

    out.push(rec);
  } while ((node = walker.nextNode()));

  /* ---------- page level ---------- */

  /* Whether the page really scrolls sideways, asked by trying it.
     scrollWidth is not the answer: a fixed element wider than the viewport inflates
     documentElement.scrollWidth (and, under mobile emulation, window.innerWidth too)
     while creating no scrollable area at all. The only trustworthy test is whether the
     viewport actually moves. */
  function measureMaxScrollX() {
    const before = window.scrollX;
    window.scrollTo(1e7, window.scrollY);
    const max = window.scrollX;
    window.scrollTo(before, window.scrollY);
    return max;
  }
  const maxScrollX = measureMaxScrollX();

  const de = document.documentElement;
  return {
    url: location.href,
    title: document.title,
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    scrollWidth: Math.max(de.scrollWidth, document.body.scrollWidth),
    maxScrollX: maxScrollX,
    scrollHeight: Math.max(de.scrollHeight, document.body.scrollHeight),
    documentBackground: css(effectiveBackground(document.body).color),
    prefersColorScheme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light',
    truncated: count > MAX_ELEMENTS,
    elements: out
  };
})()
