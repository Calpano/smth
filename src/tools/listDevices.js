import { KnownDevices } from 'puppeteer-core';

export default {
  needsSession: false,
  schema: {
    name: 'browser_list_devices',
    description: 'List all available device names that can be passed to the browser_see_visual "device" parameter.',
    annotations: { readOnlyHint: true },
    inputSchema: { type: 'object', properties: {} },
  },
  async handler() {
    return { content: [{ type: 'text', text: Object.keys(KnownDevices).sort().join('\n') }] };
  },
};
