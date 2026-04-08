/* What it does:
- Visits every visible element that has direct text content
- Determines its text color via getComputedStyle
- Determines its effective background by walking up the DOM until a non-transparent
  background-color is found; falls back to rgb(255, 255, 255) (browser default)
- Groups by (text, background) pair and counts occurrences
- Computes WCAG 2.2 contrast ratio for each pair using the relative luminance formula
- Emits aa (>=4.5) and aaa (>=7.0) pass/fail boolean flags
- Result sorted by count descending, colors as hex strings

Usage: paste into browser DevTools console — result is logged and returned.
*/
(function collectColorPairs() {
    function parseRgb(color) {
        const m = color.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)/);
        if (!m) return null;
        return { r: +m[1], g: +m[2], b: +m[3], a: m[4] !== undefined ? +m[4] : 1 };
    }

    function rgbToHex(color) {
        const c = parseRgb(color);
        if (!c) return color;
        return '#' + [c.r, c.g, c.b].map(v => v.toString(16).padStart(2, '0')).join('');
    }

    function linearize(c) {
        c /= 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    function luminance({ r, g, b }) {
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b);
    }

    function contrastRatio(c1, c2) {
        const hi = Math.max(luminance(c1), luminance(c2));
        const lo = Math.min(luminance(c1), luminance(c2));
        return Math.round(((hi + 0.05) / (lo + 0.05)) * 100) / 100;
    }

    function effectiveBg(el) {
        let node = el;
        while (node && node.tagName !== 'HTML') {
            const c = parseRgb(window.getComputedStyle(node).backgroundColor);
            if (c && c.a > 0) return window.getComputedStyle(node).backgroundColor;
            node = node.parentElement;
        }
        return 'rgb(255, 255, 255)';
    }

    const map = {};

    document.querySelectorAll('*').forEach(el => {
        if (!el.offsetParent && el.tagName !== 'BODY' && !el.closest('svg')) {
            const r = el.getBoundingClientRect();
            if (!r.width && !r.height) return;
        }
        const isSvg = el.closest('svg') || el.tagName === 'svg';
        const hasText = Array.from(el.childNodes).some(
            n => n.nodeType === Node.TEXT_NODE && n.textContent.trim()
        );
        // For SVG elements, treat fill as a foreground color even without text nodes
        const svgFill = isSvg ? window.getComputedStyle(el).fill : null;
        const hasSvgFill = svgFill && svgFill !== 'none' && !/rgba?\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\s*\)/.test(svgFill);
        if (!hasText && !hasSvgFill) return;

        const style = window.getComputedStyle(el);
        const text = hasText ? style.color : svgFill;
        const bg   = effectiveBg(el);
        const key  = `${text}|||${bg}`;

        if (!map[key]) {
            const tc = parseRgb(text);
            const bc = parseRgb(bg);
            const ratio = (tc && bc) ? contrastRatio(tc, bc) : null;
            map[key] = {
                text:       rgbToHex(text),
                background: rgbToHex(bg),
                contrast:   ratio,
                aa:         ratio !== null ? ratio >= 4.5 : false,
                aaa:        ratio !== null ? ratio >= 7.0 : false,
                count:      0,
            };
        }
        map[key].count++;
    });

    const result = Object.values(map).sort((a, b) => b.count - a.count);
    console.log(JSON.stringify(result, null, 2));
    return result;
})();
