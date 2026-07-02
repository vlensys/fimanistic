# Fimanistic

> [!NOTE]
> this is FULLY vibecoded, so yeah this is just something i need for my modpack


A lightweight, client-only Fabric mod that shows a **first-launch welcome/tutorial**
for modpacks — and lets pack authors build that tutorial in-game with a small
WYSIWYG editor. No server side, no per-tick overhead after the first menu, one
JSON config file.

## What it does

- **Shows once on first launch.** The guide opens the first time the main menu
  appears. After it is dismissed it never shows again unless reset.
- **Multi-page guides** with custom backgrounds (solid colour or an image),
  text, images, and interactive elements that:
  - **change controls** (rebind a keybinding when clicked), and
  - **change mod settings** (write a value into the mod's own settings store).
- **In-game editor** opened from **Mod Menu → Fimanistic → settings (gear)**:
  - add text / image / control / setting elements,
  - move them (drag), resize them (drag the bottom-right handle),
  - toggle an **alignment grid** that also snaps placement,
  - **zoom** by holding the left mouse button and scrolling; drag empty canvas to pan,
  - manage pages, **Preview** the guide, **Save**, and **reset** so it shows again
    on next launch.

## Authoring images

Drop `.png` files into `config/fimanistic/images/`. In the editor, add an Image
element and use **Pick next file** to choose one (or type its filename). Images
are loaded lazily as dynamic textures.

## Data

Everything lives in `config/fimanistic.json`. Coordinates are stored as fractions
of the screen, so a guide authored at one resolution renders correctly at any
other. Delete the file to restore the default starter guide.

## Build

```
./gradlew build
```

The mod jar is written to `build/libs/`. Mod Menu is an optional (suggested)
dependency — the guide still works without it, but the in-game editor is reached
through Mod Menu's settings button.

## License

Available under the GPL license.
