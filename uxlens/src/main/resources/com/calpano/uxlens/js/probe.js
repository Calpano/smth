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

  /* Resolving a colour is the browser's job, not a regular expression's.
     getComputedStyle hands back whatever notation the stylesheet used, and modern design
     systems are written in oklch() -- shadcn and Tailwind v4 among them. A parser that
     understands only rgb() and hex returns null for every colour on such a page, and then
     every contrast check silently does not run. So the fast paths stay for the common
     notations, and anything else is painted onto a one-pixel canvas and read back, which
     works for oklch, lab, color(), color-mix() and named colours alike. */
  const colorCanvas = document.createElement('canvas');
  colorCanvas.width = 1;
  colorCanvas.height = 1;
  const colorCtx = colorCanvas.getContext('2d', { willReadFrequently: true });
  const colorCache = new Map();

  function parseColor(s) {
    if (!s) return null;
    s = s.trim();
    if (!s || s === 'none') return null;
    if (s === 'transparent') return { r: 0, g: 0, b: 0, a: 0 };
    if (colorCache.has(s)) return colorCache.get(s);
    let value = parseSimpleColor(s);
    if (value === null) value = paintAndRead(s);
    colorCache.set(s, value);
    return value;
  }

  /* The notations a browser hands back most of the time, parsed directly because the
     canvas round trip is comparatively slow and this runs thousands of times. */
  function parseSimpleColor(s) {
    let m = s.match(/^rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)\s*(?:[,/]\s*([\d.%]+)\s*)?\)$/i);
    if (m) {
      const a = m[4] === undefined ? 1
          : (m[4].endsWith('%') ? parseFloat(m[4]) / 100 : parseFloat(m[4]));
      return { r: +m[1], g: +m[2], b: +m[3], a: a };
    }
    m = s.match(/^#([0-9a-f]{3,8})$/i);
    if (m) {
      const h = m[1];
      const ex = h.length <= 4 ? h.split('').map(c => c + c).join('') : h;
      if (ex.length !== 6 && ex.length !== 8) return null;
      return {
        r: parseInt(ex.slice(0, 2), 16),
        g: parseInt(ex.slice(2, 4), 16),
        b: parseInt(ex.slice(4, 6), 16),
        a: ex.length >= 8 ? parseInt(ex.slice(6, 8), 16) / 255 : 1
      };
    }
    return null;
  }

  /* Resolves a colour by painting it over two known opaque backdrops and solving.
     Reading a half-transparent pixel back directly does not work: the canvas stores
     premultiplied values, and un-premultiplying an 8-bit sample quantises badly at low
     alpha -- rgba(200,100,50,0.1) comes back as 196,98,49. Painted over black the sample is
     colour*alpha and over white it is colour*alpha + 255*(1-alpha), which gives alpha from
     the difference and the colour from the first. Both samples are fully opaque, so neither
     is quantised, and the composite -- which is the quantity every contrast check actually
     uses -- is exact. */
  function paintAndRead(value) {
    try {
      // An unparseable value leaves fillStyle untouched, so trying it against two different
      // starting values tells us whether the browser understood it.
      colorCtx.fillStyle = '#000000';
      colorCtx.fillStyle = value;
      const fromBlack = colorCtx.fillStyle;
      colorCtx.fillStyle = '#ffffff';
      colorCtx.fillStyle = value;
      if (colorCtx.fillStyle !== fromBlack) return null;

      const onBlack = paintOver(value, '#000000');
      const onWhite = paintOver(value, '#ffffff');

      let lost = 0;
      for (let i = 0; i < 3; i++) lost += onWhite[i] - onBlack[i];
      const alpha = Math.max(0, Math.min(1, 1 - (lost / 3) / 255));
      if (alpha < 0.002) return { r: 0, g: 0, b: 0, a: 0 };
      return {
        r: Math.min(255, onBlack[0] / alpha),
        g: Math.min(255, onBlack[1] / alpha),
        b: Math.min(255, onBlack[2] / alpha),
        a: alpha
      };
    } catch (e) {
      return null;
    }
  }

  function paintOver(value, backdrop) {
    colorCtx.clearRect(0, 0, 1, 1);
    colorCtx.fillStyle = backdrop;
    colorCtx.fillRect(0, 0, 1, 1);
    colorCtx.fillStyle = value;
    colorCtx.fillRect(0, 0, 1, 1);
    const d = colorCtx.getImageData(0, 0, 1, 1).data;
    return [d[0], d[1], d[2]];
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

  /* The name a screen reader would announce, by the accessible-name precedence that
     matters in practice: aria-label, then aria-labelledby, then the element's own text,
     then alt, then title. Not the full HTML-AAM algorithm, which needs the whole tree --
     this covers the cases the rules below actually ask about. */
  function accessibleName(el) {
    return nameWithSource(el).name;
  }

  /* The name and where it came from. A name is not just present or absent: one taken from a
     placeholder disappears as soon as the field is typed into, which is a different defect
     from having no name at all. */
  /**
 * Where a link goes, as a class rather than a URL.
 *
 * Two callers need this and neither needs the address. The latency audit needs to know
 * whether clicking will navigate away, and the convention miner needs to know whether the
 * thing in the top left corner points at the site's own root. Storing the class instead of
 * the href also means a redacted capture carries no URLs out of somebody else's site.
 *
 * 'in-page' is a fragment or a bare '#', which opens menus and tabs without leaving.
 * 'root' is the site's own front page, which is where a logo should point.
 * 'same-site', 'external', 'download', 'scheme' (mailto:, tel:) and 'none' are the rest.
 */
function linkTargetOf(el) {
  if (el.tagName !== 'A' && el.tagName !== 'AREA') return null;
  const raw = el.getAttribute('href');
  if (raw === null || raw.trim() === '') return 'none';
  if (el.hasAttribute('download')) return 'download';
  const href = raw.trim();
  if (href.startsWith('#')) return 'in-page';

  // Classify by the href's own shape rather than by resolving it. Resolution needs the
  // page to have a real origin, and a page under test often has none: setContent leaves
  // the document at about:blank and a fixture opened from disk reports an origin of
  // "null", so every link on every synthetic page came back as unclassifiable. A relative
  // or root-relative href is same-site by construction and needs no resolving at all.
  if (/^(mailto|tel|sms|javascript|data|blob|ftp):/i.test(href)) return 'scheme';

  const absolute = /^https?:\/\//i.test(href) || href.startsWith('//');
  if (absolute) {
    let url;
    try {
      url = new URL(href, location.href);
    } catch (e) {
      return 'none';
    }
    if (url.origin !== location.origin) return 'external';
    return (url.pathname === '/' || url.pathname === '') ? 'root' : 'same-site';
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(href)) return 'scheme';
  return (href === '/' || href === './' || href === '.') ? 'root' : 'same-site';
}

function nameWithSource(el) {
    const label = el.getAttribute('aria-label');
    if (label && label.trim()) return { name: label.trim(), source: 'aria-label' };

    const labelledBy = el.getAttribute('aria-labelledby');
    if (labelledBy) {
      const parts = labelledBy.split(/\s+/)
          .map(id => document.getElementById(id))
          .filter(Boolean)
          .map(n => n.textContent.replace(/\s+/g, ' ').trim())
          .filter(Boolean);
      if (parts.length) return { name: parts.join(' '), source: 'aria-labelledby' };
    }

    /* A form control takes its name from its label: an explicit <label for>, or one
       wrapped around it. Leaving this out made every properly labelled text field look
       nameless, which is the opposite of the truth. */
    if (/^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName)) {
      const labels = el.labels ? Array.from(el.labels) : [];
      const fromLabels = labels
          .map(n => n.textContent.replace(/\s+/g, ' ').trim())
          .filter(Boolean)
          .join(' ');
      if (fromLabels) return { name: fromLabels, source: 'label' };
      const placeholder = el.getAttribute('placeholder');
      if (placeholder && placeholder.trim()) {
        return { name: placeholder.trim(), source: 'placeholder' };
      }
    }

    const own = el.textContent.replace(/\s+/g, ' ').trim();
    if (own) return { name: own, source: 'text' };

    const img = el.tagName === 'IMG' ? el : el.querySelector('img[alt]');
    if (img) {
      const alt = img.getAttribute('alt');
      if (alt && alt.trim()) return { name: alt.trim(), source: 'alt' };
    }

    const title = el.getAttribute('title');
    if (title && title.trim()) return { name: title.trim(), source: 'title' };
    return { name: '', source: 'none' };
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

  /* True for the screen-reader-only idiom: a box clipped down to nothing. */
  function isClippedToNothing(st) {
    const path = st.clipPath;
    if (path && path !== 'none' && /^inset\(\s*(50%|100%)/.test(path)) return true;
    const clip = st.clip;
    if (clip && clip !== 'auto') {
      const nums = clip.match(/-?[\d.]+/g);
      if (nums && nums.length === 4) {
        const [top, right, bottom, left] = nums.map(Number);
        if (Math.abs(right - left) <= 1 || Math.abs(bottom - top) <= 1) return true;
      }
    }
    return false;
  }

  /* True when an ancestor that clips its overflow has scrolled or collapsed this element
     entirely out of sight. Partially visible content still counts as visible. */
  function isHiddenByAncestorOverflow(el, rect) {
    for (let n = el.parentElement; n && n !== document.documentElement; n = n.parentElement) {
      const s = getComputedStyle(n);
      const clipsX = s.overflowX !== 'visible';
      const clipsY = s.overflowY !== 'visible';
      if (!clipsX && !clipsY) continue;
      const r = n.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) return true;
      if (clipsY && (rect.bottom <= r.top + 0.5 || rect.top >= r.bottom - 0.5)) return true;
      if (clipsX && (rect.right <= r.left + 0.5 || rect.left >= r.right - 0.5)) return true;
    }
    return false;
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

    /* Hidden by being put somewhere nobody can look. Two idioms, both common and both
       invisible to a display/visibility/opacity check:
         - off-canvas: position:absolute with a large negative offset, the usual way a
           collapsed menu is kept in the accessibility tree while off the screen;
         - the screen-reader-only clip, which crushes the box to nothing while leaving it
           nominally laid out.
       Measuring either as painted content poisons everything downstream -- a collapsed
       sidebar alone produced over a thousand phantom overlaps on one real page. */
    if (b.right + sx <= 0 || b.bottom + sy <= 0) continue;
    if (isClippedToNothing(st)) continue;

    if (isHiddenByAncestorOverflow(el, b)) continue;

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
      title: el.getAttribute('title') || null,
      /* What a screen reader would announce. Empty when the element has no name at all,
         which for a control is the defect itself. */
      accessibleName: accessibleName(el),
      ariaHidden: el.getAttribute('aria-hidden') === 'true',
      type: el.getAttribute('type'),
      /* Where the accessible name came from, which separates a real label from a
         placeholder standing in for one. */
      nameSource: nameWithSource(el).source,
      placeholder: el.getAttribute('placeholder') || null,
      required: el.required === true || el.hasAttribute('required')
          || el.getAttribute('aria-required') === 'true',
      ariaInvalid: el.getAttribute('aria-invalid') === 'true',
      /* Whether anything describes this control beyond naming it -- an error message, a
         hint. Resolved here because it needs the whole document. */
      describedBy: (function () {
        const ids = el.getAttribute('aria-describedby');
        if (!ids) return null;
        const text = ids.split(/\s+/)
            .map(id => document.getElementById(id))
            .filter(Boolean)
            .map(n => n.textContent.replace(/\s+/g, ' ').trim())
            .filter(Boolean)
            .join(' ');
        return text || null;
      })(),
      /* Visible text of the element and everything inside it, which is what a sighted user
         reads off a control -- distinct from `text`, which is the element's own nodes. */
      visibleText: (function () {
        const s = el.textContent.replace(/\s+/g, ' ').trim();
        return s.length > 120 ? s.slice(0, 120) : s;
      })(),
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
      linkTarget: linkTargetOf(el),

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
