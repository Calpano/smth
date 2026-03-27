/*
Returns an array of font entries, one per family, sorted by usage count descending.
Each entry: { family, count, sizes: number[], weights: number[] }
*/
(function collectFontUsage() {
    const counts = {};

    document.querySelectorAll('*').forEach(el => {
        if (!el.offsetParent && el.tagName !== 'BODY') return;
        const style = window.getComputedStyle(el);
        const family = style.fontFamily.split(',')[0].trim().replace(/['"]/g, '');
        const size   = parseFloat(style.fontSize);
        const weight = parseInt(style.fontWeight, 10);

        if (!counts[family]) counts[family] = { count: 0, sizes: new Set(), weights: new Set() };
        counts[family].count++;
        counts[family].sizes.add(size);
        counts[family].weights.add(weight);
    });

    const result = Object.entries(counts)
        .map(([family, data]) => ({
            family,
            count: data.count,
            sizes:   [...data.sizes].sort((a, b) => a - b),
            weights: [...data.weights].sort((a, b) => a - b),
        }))
        .sort((a, b) => b.count - a.count);

    console.log(JSON.stringify(result, null, 2));
    return result;
})();
