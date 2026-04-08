/* What it does:
- querySelectorAll('*') — visits every visible DOM element
- getComputedStyle — reads the fully resolved color values (inheritance included)
- Collects: text color (color), background-color, border colors (per visible side only)
- Transparent (rgba(0,0,0,0)) values are skipped
- Border sides with border-width == 0 are skipped; duplicate colors on the same element counted once
- Different alpha values are treated as different colors
- Result is an array sorted by total usage count descending

Each entry: { color: '#rrggbb', count: number, categories: string[], where?: {...} }

Parameters (injected via __PLACEHOLDER__ replacement before eval):
  __ONLY__    null | string[]  — restrict to these categories ("text","background","border")
  __COLORS__  null | string[]  — filter to these specific colors (hex, e.g. "#ff0000")
  __WHERE__   boolean          — include element selectors (#id / .class / tag) per color

Usage: paste into browser DevTools console — result is logged and returned.
*/
(function collectColorUsage() {
    const only         = __ONLY__;
    const filterColors = __COLORS__;
    const where        = __WHERE__;

    function rgbToHex(rgb) {
        const m = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
        if (!m) return rgb;
        const r = parseInt(m[1]), g = parseInt(m[2]), b = parseInt(m[3]);
        const hex = '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
        if (m[4] !== undefined) {
            const a = parseFloat(m[4]);
            if (a < 1) return hex + Math.round(a * 255).toString(16).padStart(2, '0');
        }
        return hex;
    }

    function hexToRgb(hex) {
        hex = hex.replace(/^#/, '');
        if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
        if (hex.length === 6) {
            const r = parseInt(hex.slice(0, 2), 16);
            const g = parseInt(hex.slice(2, 4), 16);
            const b = parseInt(hex.slice(4, 6), 16);
            return `rgb(${r}, ${g}, ${b})`;
        }
        if (hex.length === 8) {
            const r = parseInt(hex.slice(0, 2), 16);
            const g = parseInt(hex.slice(2, 4), 16);
            const b = parseInt(hex.slice(4, 6), 16);
            const a = parseFloat((parseInt(hex.slice(6, 8), 16) / 255).toFixed(2));
            return `rgba(${r}, ${g}, ${b}, ${a})`;
        }
        return null;
    }

    const filterRgbSet = filterColors ? new Set(filterColors.map(hexToRgb).filter(Boolean)) : null;
    const cats = only || ['text', 'background', 'border', 'fill', 'stroke'];

    const counts = {};
    const usedBy = {};

    function elSelector(el) {
        if (el.id) return '#' + el.id;
        const cls = Array.from(el.classList).map(c => '.' + c).join('');
        return cls || el.tagName.toLowerCase();
    }

    function add(color, category, el) {
        if (!color || color === 'transparent' || color === 'rgba(0, 0, 0, 0)') return;
        if (!cats.includes(category)) return;
        if (filterRgbSet && !filterRgbSet.has(color)) return;
        if (!counts[color]) counts[color] = { text: 0, background: 0, border: 0, fill: 0, stroke: 0 };
        counts[color][category]++;
        if (where) {
            if (!usedBy[color])           usedBy[color] = {};
            if (!usedBy[color][category]) usedBy[color][category] = new Set();
            usedBy[color][category].add(elSelector(el));
        }
    }

    document.querySelectorAll('*').forEach(el => {
        if (!el.offsetParent && el.tagName !== 'BODY' && !el.closest('svg')) {
            const r = el.getBoundingClientRect();
            if (!r.width && !r.height) return;
        }

        const style = window.getComputedStyle(el);

        add(style.color, 'text', el);
        add(style.backgroundColor, 'background', el);

        const visibleBorderColors = new Set();
        for (const [cp, wp] of [
            ['borderTopColor',    'borderTopWidth'],
            ['borderRightColor',  'borderRightWidth'],
            ['borderBottomColor', 'borderBottomWidth'],
            ['borderLeftColor',   'borderLeftWidth'],
        ]) {
            if (parseFloat(style[wp]) > 0) visibleBorderColors.add(style[cp]);
        }
        visibleBorderColors.forEach(c => add(c, 'border', el));

        // SVG fill and stroke
        if (el.closest('svg') || el.tagName === 'svg') {
            const fill = style.fill;
            if (fill && fill !== 'none') add(fill, 'fill', el);
            const stroke = style.stroke;
            if (stroke && stroke !== 'none') add(stroke, 'stroke', el);
        }
    });

    const result = Object.entries(counts)
        .sort(([, a], [, b]) => (b.text + b.background + b.border + b.fill + b.stroke) - (a.text + a.background + a.border + a.fill + a.stroke))
        .map(([rgb, c]) => {
            const total = c.text + c.background + c.border + c.fill + c.stroke;
            const categories = cats.filter(cat => c[cat] > 0);
            const entry = { color: rgbToHex(rgb), count: total, categories };
            if (where && usedBy[rgb]) {
                const w = {};
                for (const cat of cats) {
                    if (usedBy[rgb][cat]?.size) w[cat] = Array.from(usedBy[rgb][cat]);
                }
                if (Object.keys(w).length) entry.where = w;
            }
            return entry;
        });

    console.log(JSON.stringify(result, null, 2));
    return result;
})();
