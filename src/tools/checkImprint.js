import { gotoPage } from '../browser/navigate.js';
import { findImprintLink, findSiblingLinks } from '../checks/imprint.js';
import { runFieldRules } from '../checks/imprint-rules.js';

const DEFAULT_LINK_TEXT = ['Impressum', 'Imprint', 'Legal'];
const DEFAULT_REQUIRED_FIELDS = ['name', 'address', 'email'];
const DEFAULT_ALSO_CHECK = ['Datenschutz'];

export default {
  schema: {
    name: 'browser_check_imprint',
    description: 'Verify a site has a German-law-conformant imprint reachable from the home page and that the imprint contains the required §5 TMG / §18 MStV fields (name, address, email by default).',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        url:             { type: 'string', description: 'Site root URL (or any page that links to the imprint).' },
        link_text:       { type: 'array', items: { type: 'string' }, description: "Visible link texts to look for. Default ['Impressum','Imprint','Legal']." },
        required_fields: { type: 'array', items: { type: 'string' }, description: "Fields to verify. Default ['name','address','email']. Available: name, address, email, phone." },
        also_check:      { type: 'array', items: { type: 'string' }, description: "Sibling link labels to verify exist (not followed). Default ['Datenschutz']." },
      },
      required: ['url'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const linkTexts      = args.link_text       ?? DEFAULT_LINK_TEXT;
    const requiredFields = args.required_fields ?? DEFAULT_REQUIRED_FIELDS;
    const alsoCheck      = args.also_check      ?? DEFAULT_ALSO_CHECK;

    const siteUrl = await gotoPage(page, args.url);
    const link = await findImprintLink(page, linkTexts);
    const alsoLinks = await findSiblingLinks(page, alsoCheck);

    // If no link found, fall back to running rules against the current page —
    // single-page sites often inline their imprint.
    let imprint;
    if (!link) {
      const html = await page.content();
      const text = await page.evaluate(() => document.body.innerText);
      const fields = runFieldRules(html, text, requiredFields);
      imprint = { found: false, link_text: null, url: null, status: null, fields, note: 'No imprint link found; ran field rules against current page.' };
    } else {
      // Capture main-frame status during navigation.
      let mainStatus = null;
      const onResponse = (res) => {
        if (res.frame() === page.mainFrame() && res.url() === link.href) mainStatus = res.status();
      };
      page.on('response', onResponse);
      try {
        await gotoPage(page, link.href);
      } finally {
        page.off('response', onResponse);
      }
      const finalUrl = page.url();
      const html = await page.content();
      const text = await page.evaluate(() => document.body.innerText);
      const fields = runFieldRules(html, text, requiredFields);
      imprint = { found: true, link_text: link.text, url: finalUrl, status: mainStatus, fields };
    }

    const missing = Object.entries(imprint.fields).filter(([, v]) => !v.ok).map(([k]) => k);
    const ok = imprint.found && missing.length === 0 && (imprint.status == null || (imprint.status >= 200 && imprint.status < 400));
    const reason = !imprint.found
      ? 'imprint link not found on page'
      : missing.length
        ? `${missing.join(', ')} missing on imprint page`
        : imprint.status && imprint.status >= 400
          ? `imprint returned HTTP ${imprint.status}`
          : null;

    const response = {
      site: siteUrl,
      imprint,
      also_check: alsoLinks,
      ok,
      reason,
    };
    return { content: [{ type: 'text', text: JSON.stringify(response, null, 2) }] };
  },
};
