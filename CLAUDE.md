# smth MCP

smth exposes these MCP tools. Full docs in `doc/smth.md`.

## browser_list_devices
Stateless. Lists device names for use with `browser_see_visual`.

## browser_launch
**Call this first.** Launches a new browser session and opens a URL. Restarts the browser if one is already running.

## browser_goto
Navigates the existing session to a new URL without relaunching the browser.

## browser_read_text
Returns page text as Markdown + list of interactive elements, each with a CSS selector usable with `browser_click`, `browser_hover`, and `browser_type`. SVG elements with `cursor: pointer`, click handlers, or ARIA roles are included.

## browser_see_fonts
Returns JSON: fonts grouped by family with size/weight/usage count. Includes SVG `<text>` elements.

## browser_see_colors
Returns JSON: all computed colors (text, background, border, SVG fill, SVG stroke) with usage counts per color. Different alpha values are distinct entries.

## browser_see_color_pairs
Returns JSON array: every text/background color pair found on the page, with WCAG 2.2 contrast ratio and AA/AAA pass/fail flags. Effective background is resolved by walking up the DOM. SVG elements with fill are included as foreground colors.

## browser_see_dom
Returns lens-filtered compact HTML of the live DOM. Lenses: `text`, `media`, `layout`, `code`, `svg` (all SVG elements/attributes), `css-classes`, `none`. Pass `search=["term"]` to get text matches with surrounding context lines instead.

## browser_click
Clicks an element by `id` or CSS `selector`. Waits for navigation to complete before returning. Selector accepts `:has-text('substring')` at the end (see below).

## browser_hover
Hovers an element by `id` or CSS `selector`. Returns a before/after diff of the text+code lens. Selector accepts `:has-text('substring')` at the end.

## browser_type
Types text into a form field by `id` or CSS `selector`. Clears first by default. Selector accepts `:has-text('substring')` at the end.

### `:has-text('...')` selector extension (click/hover/type)
Append `:has-text('substring')` to the end of any CSS selector to match the innermost element whose visible text contains the substring (case-insensitive). Example: `button:has-text('Submit')`, `:has-text('Accept all')`. Both single and double quotes work. Only one `:has-text()` per selector, always at the end.

## browser_remember_dom
Captures a named DOM snapshot for later comparison. Params: `name`, optional `lens`, `exclude`, `max_chars`.

## browser_doms
Lists all named snapshots stored in the current session.

## browser_dom_compare
Two modes: diff (`a` + `b` → `+`/`-` lines) or subtract (`a` + `subtract` → lines in `a` not in the background snapshot, i.e. foreground content).

## fetch_dom_content
All-in-one: open a URL, auto-detect background (by visiting a similar same-site page), return the unique foreground content. Stores `<prefix>_page`, `<prefix>_peer`, `<prefix>_background` snapshots for further use with `browser_dom_compare`.

## browser_see_visual
Screenshots the current page and returns the image as embedded content (base64 PNG).
Params: `zoom`, `device`, `width`, `height`, `full_page`, `element_id`.

**Always call `browser_launch` before any other browser_ tool.**
See `doc/smth.md` for full details.
