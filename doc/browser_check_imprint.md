## browser_check_imprint

Verifies a site has a German-law-conformant imprint reachable from the home page and that the imprint contains the required §5 TMG / §18 MStV fields.

| Param | Description |
|-------|-------------|
| `url` | Site root (or any page that links to the imprint). Required. |
| `link_text` | Visible link texts to look for. Default `['Impressum','Imprint','Legal']`. |
| `required_fields` | Fields to verify on the imprint page. Default `['name','address','email']`. Available: `name`, `address`, `email`, `phone`. |
| `also_check` | Sibling link labels whose *existence* is asserted (not followed). Default `['Datenschutz']`. |

Returns JSON:

```json
{
  "site": "https://ddot.it/",
  "imprint": {
    "found": true,
    "link_text": "Impressum",
    "url": "https://ddot.it/impressum.html",
    "status": 200,
    "fields": {
      "name":    { "ok": true,  "match": "Max Voelkel" },
      "address": { "ok": true,  "match": "Beispielstraße 1, 76131 Karlsruhe" },
      "email":   { "ok": false, "match": null }
    }
  },
  "also_check": [
    { "label": "Datenschutz", "found": true, "url": "..." }
  ],
  "ok": false,
  "reason": "email missing on imprint page"
}
```

### Field rules (defaults)

| field   | rule |
|---------|------|
| `name`  | A capitalised word pair appearing near `Verantwortlich` / `Anbieter` / `Inhaber` / `Geschäftsführer` (and English equivalents). |
| `address` | Street pattern `…straße/str./weg/allee/platz/gasse/ring/damm <number>` **and/or** postal code + city (`\d{4,5}` + word). |
| `email` | `mailto:` link preferred; falls back to a standard email regex over visible text. |
| `phone` | `Tel`/`Telefon`/`Phone` followed by a digits-and-separators run of 7+ chars. |

Rules live in `src/checks/imprint-rules.js`.

### Edge cases

* Single-page sites: if no imprint link is found, the field rules run against the current page (`imprint.found = false`, but `imprint.fields` may still pass).
* Cookie banners covering the footer: out of scope — dismiss with `browser_click` before calling.
* Non-German sites: pass `link_text=['Imprint','Legal notice']` and `required_fields=['name','address','email']`.
* PDF-only imprints: not supported. The tool reads HTML only.
* Handelsregister / company existence checks are out of scope.

→ [browser_check_imprint-example.json](browser_check_imprint-example.json)
