// Tool registry. The order here determines the order they appear in
// MCP list_tools responses.

import listDevices    from './listDevices.js';
import launch         from './launch.js';
import goto_          from './goto.js';
import readText       from './readText.js';
import seeFonts       from './seeFonts.js';
import seeColors      from './seeColors.js';
import seeColorPairs  from './seeColorPairs.js';
import seeDom         from './seeDom.js';
import click          from './click.js';
import hover          from './hover.js';
import type_          from './type.js';
import rememberDom    from './rememberDom.js';
import listDoms       from './listDoms.js';
import domCompare     from './domCompare.js';
import fetchDomContent from './fetchDomContent.js';
import seeVisual      from './seeVisual.js';

export const tools = [
  listDevices,
  launch,
  goto_,
  readText,
  seeFonts,
  seeColors,
  seeColorPairs,
  seeDom,
  click,
  hover,
  type_,
  rememberDom,
  listDoms,
  domCompare,
  fetchDomContent,
  seeVisual,
];

export const toolsByName = new Map(tools.map(t => [t.schema.name, t]));
