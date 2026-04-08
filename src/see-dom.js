(function seeDom(lens, custom, justCount, maxChars, exclude) {
  // ── Built-in classification ──────────────────────────────────────────────
  const BUILTIN = {
    patterns: {
      'data-*': ['code'],
      'on*':    ['code'],
      'aria-*': ['code', 'text'],
    },
    elements: {
      html: ['layout'], head: ['code'], meta: ['code'], link: ['code'],
      title: ['code'], script: ['code'], style: ['code'],
      body: ['layout'], div: ['layout'], span: ['text', 'layout'],
      p: ['text', 'layout'], br: ['layout'],
      h1: ['text'], h2: ['text'], h3: ['text'], h4: ['text'], h5: ['text'], h6: ['text'],
      strong: ['text'], em: ['text'],
      a: ['text', 'code'], label: ['text'],
      form: ['layout', 'code'],
      button: ['text', 'code'],
      input: ['text', 'code'], select: ['text', 'code'],
      option: ['text', 'code'], optgroup: ['text'],
      table: ['layout'], thead: ['layout'], tbody: ['layout'],
      tr: ['layout'], th: ['text', 'layout'], td: ['text', 'layout'],
      svg: ['media', 'layout', 'svg'], path: ['media', 'svg'],
      g: ['svg'], circle: ['svg'], rect: ['svg'], ellipse: ['svg'],
      line: ['svg'], polyline: ['svg'], polygon: ['svg'],
      text: ['text', 'svg'], tspan: ['text', 'svg'], use: ['svg'],
      defs: ['svg'], symbol: ['svg'], clippath: ['svg'], mask: ['svg'],
      pattern: ['svg'], marker: ['svg'], image: ['media', 'svg'],
      lineargradient: ['svg'], radialgradient: ['svg'], stop: ['svg'],
      foreignobject: ['svg'],
      canvas: ['media', 'code'], img: ['media', 'layout'],
      header: ['layout'], footer: ['layout'], nav: ['layout'],
      main: ['layout'], section: ['layout'], article: ['layout'], aside: ['layout'],
    },
    attributes: {
      global: {
        id:       ['text', 'media', 'layout', 'code'],
        class:    ['text', 'media', 'layout', 'code'],
        style:    ['layout'],
        title:    ['text'],
        hidden:   ['code'],
        tabindex: ['code'],
        role:     ['code', 'text'],
      },
      by_element: {
        html:   { lang: ['code'] },
        meta:   { charset: ['code'], name: ['code'], content: ['code'], property: ['code'] },
        link:   { rel: ['code'], href: ['code'], type: ['code'] },
        script: { src: ['code'], type: ['code'] },
        svg:    { viewBox: ['layout', 'svg'], xmlns: ['code'], width: ['layout', 'svg'], height: ['layout', 'svg'] },
        path:   { d: ['media', 'svg'], fill: ['media', 'svg'], stroke: ['media', 'svg'], 'stroke-width': ['layout', 'svg'], transform: ['svg'], 'clip-path': ['svg'] },
        g:      { fill: ['svg'], stroke: ['svg'], transform: ['svg'], 'clip-path': ['svg'], opacity: ['svg'] },
        circle: { cx: ['svg'], cy: ['svg'], r: ['svg'], fill: ['svg'], stroke: ['svg'], 'stroke-width': ['svg'] },
        rect:   { x: ['svg'], y: ['svg'], width: ['svg'], height: ['svg'], rx: ['svg'], ry: ['svg'], fill: ['svg'], stroke: ['svg'], 'stroke-width': ['svg'] },
        ellipse:{ cx: ['svg'], cy: ['svg'], rx: ['svg'], ry: ['svg'], fill: ['svg'], stroke: ['svg'] },
        line:   { x1: ['svg'], y1: ['svg'], x2: ['svg'], y2: ['svg'], stroke: ['svg'], 'stroke-width': ['svg'] },
        polyline: { points: ['svg'], fill: ['svg'], stroke: ['svg'], 'stroke-width': ['svg'] },
        polygon:  { points: ['svg'], fill: ['svg'], stroke: ['svg'] },
        text:   { x: ['svg'], y: ['svg'], 'text-anchor': ['svg'], 'font-size': ['svg'], fill: ['svg'] },
        tspan:  { x: ['svg'], y: ['svg'], dx: ['svg'], dy: ['svg'] },
        use:    { href: ['svg'], 'xlink:href': ['svg'], x: ['svg'], y: ['svg'], width: ['svg'], height: ['svg'] },
        symbol: { viewBox: ['svg'] },
        clippath: {},
        mask:   { x: ['svg'], y: ['svg'], width: ['svg'], height: ['svg'] },
        marker: { viewBox: ['svg'], refX: ['svg'], refY: ['svg'], markerWidth: ['svg'], markerHeight: ['svg'] },
        lineargradient: { x1: ['svg'], y1: ['svg'], x2: ['svg'], y2: ['svg'], gradientUnits: ['svg'] },
        radialgradient: { cx: ['svg'], cy: ['svg'], r: ['svg'], fx: ['svg'], fy: ['svg'], gradientUnits: ['svg'] },
        stop:   { offset: ['svg'], 'stop-color': ['svg'], 'stop-opacity': ['svg'] },
        foreignobject: { x: ['svg'], y: ['svg'], width: ['svg'], height: ['svg'] },
        image:  { href: ['svg', 'media'], 'xlink:href': ['svg', 'media'], x: ['svg'], y: ['svg'], width: ['svg', 'layout'], height: ['svg', 'layout'], preserveAspectRatio: ['svg'] },
        form:   { action: ['code'], method: ['code'], enctype: ['code'] },
        button: { type: ['code'], onclick: ['code'] },
        input:  {
          type: ['code'], name: ['code'], value: ['text', 'code'],
          placeholder: ['text'], checked: ['text', 'code'],
          min: ['code'], max: ['code'], step: ['code'], onchange: ['code'],
        },
        select:   { onchange: ['code'] },
        option:   { value: ['code'] },
        optgroup: { label: ['text'] },
        label:    { for: ['code'] },
        td:       { colspan: ['layout'], rowspan: ['layout'] },
        th:       { colspan: ['layout'], rowspan: ['layout'], scope: ['code'] },
        canvas:   { width: ['layout'], height: ['layout'] },
        img:      { src: ['media'], alt: ['text'], width: ['layout'], height: ['layout'] },
        a:        { href: ['code'], rel: ['code'], target: ['code'] },
      },
    },
  };

  // ── Merge custom overrides (union of categories) ─────────────────────────
  const cls = {
    patterns: { ...BUILTIN.patterns },
    elements: { ...BUILTIN.elements },
    attributes: {
      global: { ...BUILTIN.attributes.global },
      by_element: Object.fromEntries(
        Object.entries(BUILTIN.attributes.by_element).map(([t, a]) => [t, { ...a }])
      ),
    },
  };
  for (const [tag, cats] of Object.entries((custom || {}).elements || {})) {
    cls.elements[tag] = [...new Set([...(cls.elements[tag] || []), ...cats])];
  }
  for (const [attr, cats] of Object.entries(((custom || {}).attributes || {}).global || {})) {
    cls.attributes.global[attr] = [...new Set([...(cls.attributes.global[attr] || []), ...cats])];
  }
  for (const [tag, attrs] of Object.entries(((custom || {}).attributes || {}).by_element || {})) {
    if (!cls.attributes.by_element[tag]) cls.attributes.by_element[tag] = {};
    for (const [attr, cats] of Object.entries(attrs)) {
      cls.attributes.by_element[tag][attr] = [...new Set([...(cls.attributes.by_element[tag][attr] || []), ...cats])];
    }
  }

  // ── Lens matching ────────────────────────────────────────────────────────
  const active = lens && lens.length ? new Set(lens) : null;
  function matches(cats) { return !active || cats.some(c => active.has(c)); }
  function elementActive(tag) { return matches(cls.elements[tag] || []); }
  function attrActive(tag, name) {
    for (const [pat, cats] of Object.entries(cls.patterns)) {
      if (new RegExp('^' + pat.replace(/\*/g, '.*') + '$').test(name) && matches(cats)) return true;
    }
    if (matches((cls.attributes.global)[name] || [])) return true;
    if (matches((cls.attributes.by_element[tag] || {})[name] || [])) return true;
    return false;
  }

  // ── Exclude selectors ────────────────────────────────────────────────────
  const excludeSelectors = (exclude || '').split(',').map(s => s.trim()).filter(Boolean);
  function isExcluded(el) {
    return excludeSelectors.some(sel => { try { return el.matches(sel); } catch(e) { return false; } });
  }

  // ── none mode (no DOM output, useful when only console logs are needed) ──
  if (lens && lens.includes('none')) return '';

  // ── css-classes mode ─────────────────────────────────────────────────────
  if (lens && lens.includes('css-classes')) {
    const counts = {};
    document.querySelectorAll('*').forEach(el => {
      if (isExcluded(el)) return;
      el.classList.forEach(c => { counts[c] = (counts[c] || 0) + 1; });
    });
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .map(([cls, count]) => ({ class: cls, count }));
  }

  // ── Serialization ────────────────────────────────────────────────────────
  const VOID = new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);
  let counts;

  function serialize(node, depth, maxDepth) {
    if (node.nodeType === Node.TEXT_NODE) {
      return node.textContent.replace(/\s+/g, ' ').trim();
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return '';
    if (isExcluded(node)) return '';

    const tag = node.tagName.toLowerCase();
    const isInert = tag === 'script' || tag === 'style';
    const atMax = maxDepth !== null && depth >= maxDepth;

    // At maxDepth: still render direct text nodes so labels/buttons aren't empty
    const childNodes = isInert ? [] : Array.from(node.childNodes);
    const toRender = atMax ? childNodes.filter(c => c.nodeType === Node.TEXT_NODE) : childNodes;
    const children = toRender.map(c => serialize(c, depth + 1, maxDepth)).filter(Boolean).join('');

    if (!elementActive(tag)) return children;

    counts.elements[tag] = (counts.elements[tag] || 0) + 1;
    const FORM_CONTROLS = new Set(['input', 'select', 'textarea']);
    const activeAttrs = Array.from(node.attributes).filter(a => attrActive(tag, a.name));
    activeAttrs.forEach(a => { counts.attributes[a.name] = (counts.attributes[a.name] || 0) + 1; });

    // For form controls, inject live .value if it's active and not already present as an HTML attribute
    const liveValue = (FORM_CONTROLS.has(tag) && node.value && attrActive(tag, 'value') &&
      !activeAttrs.some(a => a.name === 'value')) ? node.value : null;

    const attrs = [
      ...activeAttrs.map(a => {
        // Use live JS .value property for form controls instead of the HTML attribute default
        if (a.name === 'value' && FORM_CONTROLS.has(tag)) {
          const v = node.value;
          return v ? `value="${v}"` : null;
        }
        return a.value === '' ? a.name : `${a.name}="${a.value}"`;
      }).filter(Boolean),
      ...(liveValue ? [`value="${liveValue}"`] : []),
    ].join(' ');
    const open = attrs ? `<${tag} ${attrs}>` : `<${tag}>`;
    if (VOID.has(tag)) return open;
    return `${open}${children}</${tag}>`;
  }

  function run(maxDepth) {
    counts = { elements: {}, attributes: {} };
    const html = serialize(document.documentElement, 0, maxDepth);
    return { html, counts: { elements: { ...counts.elements }, attributes: { ...counts.attributes } } };
  }

  // ── Depth budget (max_chars) ─────────────────────────────────────────────
  let best, usedDepth;

  if (maxChars !== null) {
    best = run(1);
    usedDepth = 1;
    for (let d = 2; ; d++) {
      const candidate = run(d);
      if (candidate.html === best.html) break;       // tree fully expanded
      if (candidate.html.length > maxChars) break;   // exceeded budget
      best = candidate;
      usedDepth = d;
    }
  } else {
    best = run(null);
    usedDepth = null;
  }

  // ── Output ───────────────────────────────────────────────────────────────
  if (justCount) {
    const out = { chars: best.html.length, elements: best.counts.elements, attributes: best.counts.attributes };
    if (usedDepth !== null) out.depth = usedDepth;
    return out;
  }
  return best.html;
})(__LENS__, __CUSTOM__, __JUST_COUNT__, __MAX_CHARS__, __EXCLUDE__);
