# How to use Fimanistic

Fimanistic shows a short, multi-page guide the first time a player opens the
main menu.

---

## For players

- The guide appears automatically the **first time** the main menu loads.
- Use **Next** to move forward and **Skip** to leave early. Both appear on every
  page **except the last**.
- The **last page** only shows **Get started**, which closes the guide.
- Some pages contain interactive controls:
  - a **key-binding button** — this is Minecraft's own control button. It shows the
    current key; click it, then press the key (or mouse button) you want to bind, or
    press **Esc** to unbind it.
  - an **On/Off toggle** — the vanilla toggle button; click it to switch a feature
    on or off.
- Once dismissed, the guide will not show again unless it is reset (see below).

---

## For pack authors

### Opening the editor

Install **Mod Menu**, then:

1. Main menu (or pause menu) → **Mods**.
2. Select **Fimanistic**.
3. Click the **settings / gear** button. The editor opens.

### Adding elements

The top toolbar adds elements to the current page:

| Button      | Adds                                                            |
|-------------|----------------------------------------------------------------|
| **+ Text**  | A text label.                                                  |
| **+ Image** | An image (see *Images* below).                                 |
| **+ Control** | A button that rebinds a keybinding when clicked in the guide. |
| **+ Setting** | A button that toggles a mod setting when clicked in the guide. |
| **Delete**  | Removes the currently selected element.                        |
| **Grid: ON/OFF** | Toggles the alignment grid (and snapping).                |

New elements appear in the centre of the page, already selected.

### Moving, resizing and zooming

- **Select**: left-click an element. A yellow outline and a corner handle appear.
- **Move**: drag the body of the element. With the grid on, it snaps to grid lines.
- **Resize**: drag the small **bottom-right handle**.
- **Delete**: press the **Delete** key (or the toolbar **Delete** button) while an
  element is selected.
- **Zoom the whole canvas**: **scroll** the mouse wheel. The view zooms around the
  cursor. Drag empty canvas to pan.
- **Resize one element quickly**: **hold the left mouse button and scroll** over an
  element. Images scale as a whole; text also grows/shrinks its font.

### Editing element properties

Selecting an element opens a property panel on the right.

- **Text** — type into the **Text** box. Set colour (`AARRGGBB` hex), **Scale**, and
  toggle **Center** and **Shadow**. When *Center* is on, the text is centred both
  horizontally and vertically inside its box.
  - Shortcut: **double-click** a text element on the canvas to jump straight into
    editing its text.
- **Image** — type a filename, or click **Pick next file** to cycle through the PNGs
  you have added.
- **Control** — set the **Keybind id** (which keybinding to expose, e.g. `key.jump`)
  and an optional **Label**. In the guide this element is rendered as Minecraft's
  **real key-bind button**: the player clicks it and presses a key (or mouse button)
  to rebind, or Esc to unbind. There is no separate "set the key" step in the editor —
  the player chooses the key. (See *Finding keybind IDs* below.)
- **Setting** — set the **Setting key**, an optional **Label**, and the **Default
  value** using the vanilla **On/Off** toggle. In the guide the element appears as
  that same On/Off toggle and writes the value into the mod's settings store.

### Finding keybind IDs

The **Keybind id** field is a keybinding's internal name, e.g. `key.jump`. It is the
ID, not the key — *which* key it is bound to is chosen by the player when they click
the key-bind button in the guide.

**The easiest way to find any keybind ID (vanilla or modded):** open
`.minecraft/options.txt` in a text editor. Every keybinding is listed as:

```
key_<keybind id>:<current key>
```

For example `key_key.jump:key.keyboard.space`. The part between `key_` and `:` is the
**Keybind id** to paste into Fimanistic (`key.jump`), and the value after `:` is the
key-name format used internally (you don't need it — you bind by pressing a key).
This works for **modded** keybinds too: every installed mod's keys appear here.

#### Vanilla default keybind IDs

Common ones r listed below

| ID                        | Action                       |
|---------------------------|------------------------------|
| `key.forward`             | Walk Forward                 |
| `key.back`                | Walk Backward                |
| `key.left`                | Strafe Left                  |
| `key.right`               | Strafe Right                 |
| `key.jump`                | Jump                         |
| `key.sneak`               | Sneak                        |
| `key.sprint`              | Sprint                       |
| `key.attack`              | Attack / Destroy             |
| `key.use`                 | Use Item / Place Block       |
| `key.drop`                | Drop Selected Item           |
| `key.pickItem`            | Pick Block                   |
| `key.swapOffhand`         | Swap Item With Off Hand      |
| `key.inventory`           | Open / Close Inventory       |
| `key.hotbar.1` … `key.hotbar.9` | Hotbar Slots 1–9       |
| `key.chat`                | Open Chat                    |
| `key.command`             | Open Command                 |
| `key.playerlist`          | List Players                 |
| `key.socialInteractions`  | Social Interactions Screen   |
| `key.advancements`        | Advancements                 |
| `key.screenshot`          | Take Screenshot              |
| `key.togglePerspective`   | Toggle Perspective           |
| `key.smoothCamera`        | Toggle Cinematic Camera      |
| `key.fullscreen`          | Toggle Fullscreen            |
| `key.toggleGui`           | Toggle GUI                   |
| `key.quickActions`        | Quick Actions                |
| `key.saveToolbarActivator`| Save Hotbar Activator        |
| `key.loadToolbarActivator`| Load Hotbar Activator        |

Spectator: `key.spectatorOutlines` (Highlight Players), `key.spectatorHotbar`
(Select On Hotbar), `key.toggleSpectatorShaderEffects`.

There are also many `key.debug.*` IDs (e.g. `key.debug.overlay`,
`key.debug.reloadChunk`, `key.debug.crash`, `key.debug.showHitboxes`,
`key.debug.copyLocation` …) bound under the F3 debug menu. You rarely need to rebind
these, but they follow the same `key.debug.<name>` pattern and also appear in
`options.txt`.

> If you type a Keybind id that no entry matches, nothing happens when the guide
> button is clicked (Fimanistic logs a warning). So double-check the spelling
> against `options.txt`.

### Images

Drop `.png` files into:

```
config/fimanistic/images/
```

They become available immediately via the **Pick next file** button on image
elements (and as page backgrounds in the JSON). Use **Save** in the editor to make
newly added files load.

### Pages

The bottom bar manages pages:

- **`<` / `>`** — previous / next page.
- **+ Page / - Page** — add a page after the current one / delete the current page.
- **Preview** — play the guide from the current page without marking it as "seen".
- **Save** — write everything to `config/fimanistic.json`.
- **Show on next launch (reset)** — clears the "already seen" flag so the guide
  shows again the next time the menu loads. Use this to test the real first-launch
  experience.
- **Done** — close the editor.

### Where data lives

Everything is stored in `config/fimanistic.json`. Positions and sizes are saved as
fractions of the screen, so a guide built at one resolution looks right at any
other. Delete the file to restore the built-in starter guide.

