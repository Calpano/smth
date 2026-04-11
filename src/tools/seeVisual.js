import { KnownDevices } from 'puppeteer-core';
import { drainLogs } from '../browser/session.js';

export default {
  schema: {
    name: 'browser_see_visual',
    description: 'Screenshot the current page and return the image as embedded content visible to the model.',
    annotations: { readOnlyHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        width:      { type: 'number', description: 'Viewport width in pixels (default: current viewport).' },
        height:     { type: 'number', description: 'Viewport height in pixels (default: current viewport).' },
        full_page:  { type: 'boolean', description: 'Capture full scrollable page (default: true, or false when element_id is set).' },
        zoom:       { type: 'number', description: 'CSS zoom factor. 1 = no zoom, 1.5 = 150%, 2 = 200% etc. (default: 1).' },
        element_id: { type: 'string', description: 'Scroll to and center this element id before capturing. Implies full_page: false.' },
        device:     { type: 'string', description: 'Emulate a named device (sets viewport, pixel ratio, user-agent). Overrides width/height. Call browser_list_devices to see options.' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const { zoom = 1, device, width, height, element_id } = args;
    const full_page = args.full_page ?? (!element_id);

    if (device) {
      if (!KnownDevices[device]) throw new Error(`Unknown device "${device}". Call browser_list_devices to see available options.`);
      await page.emulate(KnownDevices[device]);
    } else if (width || height) {
      const vp = page.viewport();
      await page.setViewport({ width: width || vp.width, height: height || vp.height });
    }

    if (zoom !== 1) {
      await page.evaluate((z) => { document.documentElement.style.zoom = `${z * 100}%`; }, zoom);
    }

    if (element_id) {
      const found = await page.evaluate((elId) => {
        const el = document.getElementById(elId);
        if (!el) return false;
        el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' });
        return true;
      }, element_id);
      if (!found) throw new Error(`Element #${element_id} not found on page`);
      await new Promise(r => setTimeout(r, 150));
    }

    const buffer = await page.screenshot({ fullPage: full_page });

    if (zoom !== 1) {
      await page.evaluate(() => { document.documentElement.style.zoom = ''; });
    }

    const content = [{ type: 'image', data: buffer.toString('base64'), mimeType: 'image/png' }];
    if (args.getConsoleLogs) {
      const logs = drainLogs(session);
      if (logs) content.push({ type: 'text', text: logs.trimStart() });
    }
    return { content };
  },
};
