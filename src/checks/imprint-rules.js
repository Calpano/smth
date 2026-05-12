// Field-detection rules for browser_check_imprint. Pure data + regex helpers
// so callers can pass overrides without touching the tool code.

const NAME_HINT = /\b(?:Verantwortlich|Anbieter|Inhaber|Geschäftsführer|Responsible|Owner)\b[^\n]*?([A-ZÄÖÜ][\wÄÖÜäöüß.-]+(?:\s+(?:[A-ZÄÖÜ][\wÄÖÜäöüß.-]+|von|van|de|della))+)/i;
const POSTAL_CITY = /\b\d{4,5}\s+[A-Za-zÄÖÜäöüß.\-]+(?:\s+[A-Za-zÄÖÜäöüß.\-]+)?/;
const STREET = /\b[A-Za-zÄÖÜäöüß.\-]+(?:straße|strasse|str\.|weg|allee|platz|gasse|ring|damm)\s+\d+[a-zA-Z]?/i;
const EMAIL = /[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/;
const PHONE_HINT = /(?:Tel\.?|Telefon|Phone)[^\n]{0,40}(\+?[\d\s().\-/]{7,})/i;

// Try every rule, return { ok, match } where match is the captured substring
// or null. The check is "did we find evidence of this field on the page?".
export const DEFAULT_RULES = {
  name: text => {
    const m = text.match(NAME_HINT);
    return m ? { ok: true, match: m[1].trim() } : { ok: false, match: null };
  },
  address: text => {
    const postal = text.match(POSTAL_CITY);
    const street = text.match(STREET);
    if (postal && street) return { ok: true, match: `${street[0]}, ${postal[0]}` };
    if (postal) return { ok: true, match: postal[0] };
    return { ok: false, match: null };
  },
  email: (text, html) => {
    // Prefer mailto: links — that's the canonical imprint encoding.
    const mailto = html.match(/mailto:([^"'>\s?]+)/i);
    if (mailto) return { ok: true, match: mailto[1] };
    const m = text.match(EMAIL);
    return m ? { ok: true, match: m[0] } : { ok: false, match: null };
  },
  phone: text => {
    const m = text.match(PHONE_HINT);
    if (m) return { ok: true, match: m[1].trim() };
    return { ok: false, match: null };
  },
};

export function runFieldRules(html, text, requiredFields, overrides = {}) {
  const rules = { ...DEFAULT_RULES, ...overrides };
  const out = {};
  for (const field of requiredFields) {
    const rule = rules[field];
    if (!rule) { out[field] = { ok: false, match: null, error: `no rule for '${field}'` }; continue; }
    out[field] = rule(text, html);
  }
  return out;
}
