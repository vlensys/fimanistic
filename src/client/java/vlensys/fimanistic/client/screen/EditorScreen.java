package vlensys.fimanistic.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import vlensys.fimanistic.client.Actions;
import vlensys.fimanistic.client.FimanisticClient;
import vlensys.fimanistic.client.ImageStore;
import vlensys.fimanistic.client.config.TutorialConfig;
import vlensys.fimanistic.client.config.TutorialConfig.Element;
import vlensys.fimanistic.client.config.TutorialConfig.Page;

import java.util.List;
import java.util.Locale;

/**
 * In-game WYSIWYG editor for the guide. Opened from Mod Menu's settings button.
 *
 * Canvas interaction:
 *  - click an element to select; drag its body to move, drag the bottom-right
 *    handle to resize; drag empty canvas to pan.
 *  - hold left mouse button and scroll the wheel to zoom around the cursor.
 *  - toggle the alignment grid (which also snaps placement) from the toolbar.
 *
 * Elements and view are stored in fractional canvas space, so zoom/pan are purely
 * a viewing transform and never alter what gets saved.
 */
public class EditorScreen extends Screen {

	private static final int TOP_BAR = 24;
	private static final int PANEL_W = 124;
	private static final int STRIP_W = 84;        // left slide filmstrip
	private static final int SLIDE_H = 46;
	private static final int SLIDE_GAP = 6;
	private static final float HANDLE_PX = 8f;

	private final TutorialConfig cfg;
	private final Screen parent;

	private int pageIndex = 0;
	private Element selected;
	/** Layer that new elements are placed on / that the panel acts on. */
	private int activeLayer = TutorialConfig.LAYER_OBJECTS;

	private enum Drag { NONE, MOVE, RESIZE, PAN }
	private Drag drag = Drag.NONE;
	private boolean leftDown = false;
	/** Index of the slide being dragged in the filmstrip, or -1. */
	private int draggingSlide = -1;

	private float zoom = 1f;
	private float panX = 0f;
	private float panY = 0f;

	private String status = "";

	/** Live title-screen panorama, used for panorama-backed pages. */
	private final Panorama panorama = new Panorama();

	/** Captions drawn above panel fields: {x, y} -> text. */
	private final java.util.List<int[]> labelPos = new java.util.ArrayList<>();
	private final java.util.List<String> labelText = new java.util.ArrayList<>();

	/** The text-content EditBox of the current selection, focused on double-click. */
	private EditBox primaryTextBox;
	private EditBox lastFieldBox;

	public EditorScreen(TutorialConfig cfg, Screen parent) {
		super(Component.translatable("fimanistic.editor.title"));
		this.cfg = cfg;
		this.parent = parent;
		if (cfg.pages.isEmpty()) cfg.pages.add(new Page());
	}

	private Page page() {
		pageIndex = Math.max(0, Math.min(pageIndex, cfg.pages.size() - 1));
		return cfg.pages.get(pageIndex);
	}

	// ---- widget layout -----------------------------------------------------

	@Override
	protected void init() {
		// Register image textures now, on the render thread — the extract/render pass
		// records draw commands and must not allocate GPU textures.
		ImageStore.preloadAll();

		// Top toolbar: element creation + grid.
		int x = 4;
		x = toolButton("fimanistic.button.addText", x, () -> add(newText()));
		x = toolButton("fimanistic.button.addImage", x, () -> add(newImage()));
		x = toolButton("fimanistic.button.addControl", x, () -> add(newControl()));
		x = toolButton("fimanistic.button.addSetting", x, () -> add(newSetting()));
		x = toolButton("fimanistic.button.delete", x, this::deleteSelected);
		addRenderableWidget(Button.builder(
				Component.translatable("fimanistic.button.grid", onOff(cfg.gridEnabled)),
				b -> { cfg.gridEnabled = !cfg.gridEnabled; rebuildWidgets(); })
				.bounds(x, 2, 64, 20).build());
		x += 66;
		// Active layer: new elements land here; toggles the selection's layer too.
		addRenderableWidget(Button.builder(Component.literal("Layer: " + layerName(activeLayer)), b -> {
			activeLayer = (activeLayer == TutorialConfig.LAYER_OBJECTS)
					? TutorialConfig.LAYER_BACKGROUND : TutorialConfig.LAYER_OBJECTS;
			rebuildWidgets();
		}).bounds(x, 2, 78, 20).build());

		// Bottom bar: pages + actions.
		int by = height - 24;
		int bx = 4;
		bx = barButton("fimanistic.button.prevPage", bx, 24, () -> { pageIndex--; selected = null; rebuildWidgets(); });
		bx = barButton("fimanistic.button.nextPage", bx, 24, () -> { pageIndex++; selected = null; rebuildWidgets(); });
		bx = barButton("fimanistic.button.addPage", bx, 56, () -> { cfg.pages.add(pageIndex + 1, new Page()); pageIndex++; selected = null; rebuildWidgets(); });
		bx = barButton("fimanistic.button.delPage", bx, 56, this::deletePage);
		bx = barButton("fimanistic.button.preview", bx, 60, () -> minecraft.setScreen(new TutorialScreen(cfg, this, true, pageIndex)));
		bx = barButton("fimanistic.button.save", bx, 48, this::save);
		bx = barButton("fimanistic.button.reset", bx, 150, this::resetFirstLaunch);
		bx = barButton("fimanistic.button.wipe", bx, 84, this::wipeConfig);
		// Done saves too, so edits are never silently lost when leaving the editor.
		addRenderableWidget(Button.builder(Component.translatable("fimanistic.button.done"), b -> closeEditor())
				.bounds(width - 54, by, 50, 20).build());

		buildPropertyPanel();
	}

	private int toolButton(String key, int x, Runnable action) {
		int w = 58;
		addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
				.bounds(x, 2, w, 20).build());
		return x + w + 2;
	}

	private int barButton(String key, int x, int w, Runnable action) {
		if (w <= 0) w = 160;
		addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
				.bounds(x, height - 24, w, 20).build());
		return x + w + 2;
	}

	/** Right-hand panel: page background when nothing is selected, else element props. */
	private void buildPropertyPanel() {
		labelPos.clear();
		labelText.clear();
		primaryTextBox = null;
		int px = width - PANEL_W + 6;
		int w = PANEL_W - 12;
		int y = TOP_BAR + 16;

		if (selected == null) {
			buildPageBackgroundPanel(px, w, y);
			return;
		}

		switch (selected.type) {
			case TutorialConfig.TYPE_TEXT -> {
				y = field(px, y, w, "Text", selected.text, s -> selected.text = s);
				primaryTextBox = lastFieldBox; // focused on double-click
				y = field(px, y, w, "Colour AARRGGBB", hex(selected.color), s -> selected.color = parseColor(s, selected.color));
				y = field(px, y, w, "Scale", trimFloat(selected.scale), s -> selected.scale = parseFloat(s, selected.scale));
				toggle(px, y, w, "Center: " + onOff(selected.center), () -> selected.center = !selected.center);
				toggle(px, y + 22, w, "Shadow: " + onOff(selected.shadow), () -> selected.shadow = !selected.shadow);
			}
			case TutorialConfig.TYPE_IMAGE -> {
				y = field(px, y, w, "Image file", selected.image, s -> selected.image = s);
				toggle(px, y, w, "Pick next file", this::cycleImage);
			}
			case TutorialConfig.TYPE_CONTROL -> {
				y = field(px, y, w, "Label", selected.text, s -> selected.text = s);
				y = field(px, y, w, "Keybind id (e.g. key.jump)", selected.keyMapping, s -> selected.keyMapping = s);
				// Browse every registered keybind — vanilla and other mods' — instead of
				// having to know the raw id. Cycles selected.keyMapping through them.
				addRenderableWidget(Button.builder(Component.literal("Pick next keybind"),
						b -> { cycleKeybind(); rebuildWidgets(); }).bounds(px, y, w, 18).build());
				y += 24;
				// Confirm what the id currently resolves to (or warn if unknown).
				KeyMapping km = Actions.findKeyMapping(selected.keyMapping);
				if (km != null) {
					caption(px, y, "= " + Component.translatable(km.getName()).getString());
					caption(px, y + 9, "bound to " + km.getTranslatedKeyMessage().getString());
				} else {
					caption(px, y, "Unknown id — not registered");
					caption(px, y + 9, "by any loaded mod.");
				}
			}
			case TutorialConfig.TYPE_SETTING -> {
				y = field(px, y, w, "Label", selected.text, s -> selected.text = s);
				y = field(px, y, w, "Setting key", selected.settingKey, s -> selected.settingKey = s);
				// Default value uses the vanilla On/Off toggle, matching the guide.
				caption(px, y, "Default value");
				boolean def = Boolean.parseBoolean(selected.settingValue);
				addRenderableWidget(CycleButton.onOffBuilder(def)
						.create(px, y + 10, w, 18, Component.literal("Default"),
								(btn, val) -> selected.settingValue = String.valueOf(val)));
			}
			default -> { }
		}

		// Common: which layer this element lives on.
		int ly = height - 52;
		addRenderableWidget(Button.builder(Component.literal("On: " + layerName(selected.layer) + " layer"), b -> {
			selected.layer = (selected.layer == TutorialConfig.LAYER_OBJECTS)
					? TutorialConfig.LAYER_BACKGROUND : TutorialConfig.LAYER_OBJECTS;
			rebuildWidgets();
		}).bounds(px, ly, w, 18).build());
	}

	/** Background editor, shown when no element is selected. */
	private void buildPageBackgroundPanel(int px, int w, int y) {
		// Scope: does the background below belong to this page, or to every page?
		caption(px, y, "Background applies to");
		addRenderableWidget(Button.builder(
				Component.literal(cfg.bgAllPages ? "All pages" : "This page only"), b -> {
			cfg.bgAllPages = !cfg.bgAllPages;
			rebuildWidgets();
		}).bounds(px, y + 10, w, 18).build());
		y += 34;

		// Edit the shared background when scope is "all pages", else this page's own.
		Page bg = cfg.effectiveBackground(page());
		caption(px, y, cfg.bgAllPages ? "Shared background" : "Page background");
		addRenderableWidget(Button.builder(Component.literal("Type: " + bg.bgType), b -> {
			cycleBgType(bg);
			rebuildWidgets();
		}).bounds(px, y + 10, w, 18).build());
		y += 34;

		if (TutorialConfig.BG_COLOR.equals(bg.bgType)) {
			field(px, y, w, "Colour AARRGGBB", hex(bg.bgColor), s -> bg.bgColor = parseColor(s, bg.bgColor));
		} else if (TutorialConfig.BG_IMAGE.equals(bg.bgType)) {
			y = field(px, y, w, "Image file", bg.bgImage, s -> bg.bgImage = s);
			addRenderableWidget(Button.builder(Component.literal("Pick next file"), b -> { cyclePageImage(bg); rebuildWidgets(); })
					.bounds(px, y, w, 18).build());
		} else {
			caption(px, y, "Uses the title-screen");
			caption(px, y + 9, "panorama (default).");
		}
	}

	private void cycleBgType(Page pg) {
		pg.bgType = switch (pg.bgType == null ? "" : pg.bgType) {
			case TutorialConfig.BG_PANORAMA -> TutorialConfig.BG_COLOR;
			case TutorialConfig.BG_COLOR -> TutorialConfig.BG_IMAGE;
			default -> TutorialConfig.BG_PANORAMA;
		};
	}

	private void cyclePageImage(Page pg) {
		List<String> imgs = ImageStore.list();
		if (imgs.isEmpty()) { status = "Drop PNGs in config/fimanistic/images"; return; }
		int idx = imgs.indexOf(pg.bgImage);
		pg.bgImage = imgs.get((idx + 1) % imgs.size());
	}

	private static String layerName(int layer) {
		return layer == TutorialConfig.LAYER_BACKGROUND ? "BG" : "Obj";
	}

	private void caption(int x, int y, String text) {
		labelPos.add(new int[]{x, y});
		labelText.add(text);
	}

	private interface Setter { void set(String value); }

	private int field(int x, int y, int w, String label, String value, Setter setter) {
		caption(x, y, label);
		EditBox box = new EditBox(font, x, y + 10, w, 16, Component.literal(label));
		box.setMaxLength(512);
		box.setValue(value == null ? "" : value);
		box.setResponder(setter::set);
		addRenderableWidget(box);
		lastFieldBox = box;
		return y + 32;
	}

	private void toggle(int x, int y, int w, String label, Runnable action) {
		addRenderableWidget(Button.builder(Component.literal(label), b -> { action.run(); rebuildWidgets(); })
				.bounds(x, y, w, 20).build());
	}

	// ---- element creation --------------------------------------------------

	private void add(Element e) {
		e.layer = activeLayer;
		page().elements.add(e);
		selected = e;
		status = "";
		rebuildWidgets();
	}

	private Element newText() {
		Element e = new Element();
		e.type = TutorialConfig.TYPE_TEXT; e.text = "New text";
		e.x = 0.5f; e.y = 0.5f; e.w = 0.3f; e.h = 0.06f; e.center = true;
		return e;
	}

	private Element newImage() {
		Element e = new Element();
		e.type = TutorialConfig.TYPE_IMAGE;
		e.x = 0.5f; e.y = 0.5f; e.w = 0.25f; e.h = 0.25f;
		List<String> imgs = ImageStore.list();
		e.image = imgs.isEmpty() ? "" : imgs.get(0);
		return e;
	}

	private Element newControl() {
		Element e = new Element();
		e.type = TutorialConfig.TYPE_CONTROL; e.text = "Set Jump = Space";
		e.x = 0.5f; e.y = 0.5f; e.w = 0.22f; e.h = 0.07f;
		e.keyMapping = "key.jump"; e.keyName = "key.keyboard.space";
		return e;
	}

	private Element newSetting() {
		Element e = new Element();
		e.type = TutorialConfig.TYPE_SETTING; e.text = "Enable feature";
		e.x = 0.5f; e.y = 0.5f; e.w = 0.22f; e.h = 0.07f;
		e.settingKey = "feature"; e.settingValue = "true";
		return e;
	}

	private void deleteSelected() {
		if (selected != null) {
			page().elements.remove(selected);
			selected = null;
			rebuildWidgets();
		}
	}

	private void deletePage() {
		if (cfg.pages.size() > 1) {
			cfg.pages.remove(pageIndex);
			pageIndex = Math.max(0, pageIndex - 1);
		} else {
			cfg.pages.get(0).elements.clear();
		}
		selected = null;
		rebuildWidgets();
	}

	private void cycleImage() {
		List<String> imgs = ImageStore.list();
		if (imgs.isEmpty()) { status = "Drop PNGs in config/fimanistic/images"; return; }
		int idx = imgs.indexOf(selected.image);
		selected.image = imgs.get((idx + 1) % imgs.size());
	}

	/** Step to the next registered keybinding (vanilla or any mod's). */
	private void cycleKeybind() {
		List<String> names = Actions.allKeyMappingNames();
		if (names.isEmpty()) { status = "No keybinds registered"; return; }
		int idx = names.indexOf(selected.keyMapping);
		selected.keyMapping = names.get((idx + 1) % names.size());
	}

	private void save() {
		cfg.save();
		// Re-read any edited PNGs, but do it here (input handler, render thread),
		// not during the extract pass.
		ImageStore.invalidate();
		ImageStore.preloadAll();
		status = "Saved.";
	}

	private void resetFirstLaunch() {
		cfg.firstLaunchDone = false;
		cfg.save();
		status = "Guide will show on next launch.";
	}

	/** Save everything to disk, then return to Mod Menu. */
	private void closeEditor() {
		ImageStore.invalidate();
		cfg.save();
		minecraft.setScreen(parent);
	}

	/**
	 * Destructive: throw away the entire config file and rebuild the starter
	 * guide. Guarded behind a confirmation with clear disclaimers, since it wipes
	 * every slide, keybind/setting binding and mod setting the player has made.
	 */
	private void wipeConfig() {
		minecraft.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						TutorialConfig fresh = TutorialConfig.defaults();
						fresh.save();
						ImageStore.invalidate();
						// Swap the session-wide instance so the guide and Mod Menu
						// both see the wiped config immediately.
						FimanisticClient.CONFIG = fresh;
						minecraft.setScreen(new EditorScreen(fresh, parent));
					} else {
						minecraft.setScreen(this);
					}
				},
				Component.translatable("fimanistic.wipe.title"),
				Component.translatable("fimanistic.wipe.message"),
				Component.translatable("fimanistic.wipe.confirm"),
				Component.translatable("fimanistic.wipe.cancel")));
	}

	// ---- coordinate helpers ------------------------------------------------

	private float canvasMouseX(double mx) { return (float) ((mx - panX) / zoom); }
	private float canvasMouseY(double my) { return (float) ((my - panY) / zoom); }

	/** Canvas-space pixel rect for an element (pre zoom/pan). */
	private int[] canvasRect(Element e) {
		return CanvasRenderer.rect(e, width, height);
	}

	private boolean inCanvas(double mx, double my) {
		return my > TOP_BAR && my < height - 26 && mx > STRIP_W && mx < width - PANEL_W;
	}

	private float snap(float frac) {
		if (!cfg.gridEnabled) return frac;
		int n = Math.max(2, cfg.gridDivisions);
		return Math.round(frac * n) / (float) n;
	}

	// ---- rendering ---------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		gfx.fill(0, 0, width, height, 0xFF202024); // editor backdrop behind the canvas

		Matrix3x2fStack pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(panX, panY);
		pose.scale(zoom);

		Page pg = page();
		CanvasRenderer.drawBackground(gfx, cfg.effectiveBackground(pg), 0, 0, width, height, panorama, mouseX, mouseY);

		if (cfg.gridEnabled) drawGrid(gfx);

		// Background layer first, then objects layer.
		for (Element e : drawOrder()) {
			int[] r = canvasRect(e);
			CanvasRenderer.drawElement(gfx, font, e, r[0], r[1], r[2], r[3], cfg);
		}

		if (selected != null && pg.elements.contains(selected)) {
			int[] r = canvasRect(selected);
			outline(gfx, r[0], r[1], r[2], r[3], 0xFFFFD24A);
			int hs = Math.max(2, Math.round(HANDLE_PX / zoom));
			gfx.fill(r[0] + r[2] - hs, r[1] + r[3] - hs, r[0] + r[2], r[1] + r[3], 0xFFFFD24A);
		}

		pose.popMatrix();
	}

	private void drawGrid(GuiGraphicsExtractor gfx) {
		int n = Math.max(2, cfg.gridDivisions);
		int color = 0x22FFFFFF;
		for (int i = 0; i <= n; i++) {
			int gx = Math.round(i * width / (float) n);
			gfx.fill(gx, 0, gx + 1, height, color);
			int gy = Math.round(i * height / (float) n);
			gfx.fill(0, gy, width, gy + 1, color);
		}
	}

	private void outline(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int c) {
		gfx.fill(x, y, x + w, y + 1, c);
		gfx.fill(x, y + h - 1, x + w, y + h, c);
		gfx.fill(x, y, x + 1, y + h, c);
		gfx.fill(x + w - 1, y, x + w, y + h, c);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// Chrome backgrounds (above the canvas, below the widgets).
		gfx.fill(0, 0, width, TOP_BAR, 0xCC15151A);
		gfx.fill(0, height - 26, width, height, 0xCC15151A);
		gfx.fill(width - PANEL_W, TOP_BAR, width, height - 26, 0xE015151A);

		drawSlideStrip(gfx);

		// Field captions sit just above their EditBoxes.
		for (int i = 0; i < labelPos.size(); i++) {
			int[] p = labelPos.get(i);
			gfx.text(font, Component.literal(labelText.get(i)), p[0], p[1], 0xFF9090A0);
		}

		super.extractRenderState(gfx, mouseX, mouseY, partialTick);

		// Overlays.
		int leftX = STRIP_W + 4;
		gfx.text(font, Component.literal(String.format(Locale.ROOT, "Zoom %.0f%%", zoom * 100)), leftX, TOP_BAR + 2, 0xFFB0B0C0);
		if (!status.isEmpty()) {
			gfx.text(font, Component.literal(status), leftX, height - 36, 0xFF8BE08B);
		}
	}

	/** The Google-Slides-style filmstrip down the left edge. */
	private void drawSlideStrip(GuiGraphicsExtractor gfx) {
		gfx.fill(0, TOP_BAR, STRIP_W, height - 26, 0xE015151A);
		gfx.text(font, Component.literal("Slides"), 6, TOP_BAR + 3, 0xFF9090A0);
		for (int i = 0; i < cfg.pages.size(); i++) {
			int[] s = slideRect(i);
			if (s[1] > height - 26) break; // overflow guard
			Page pg = cfg.pages.get(i);
			// Reflect the effective background (the shared one when "all pages" is on).
			Page bg = cfg.effectiveBackground(pg);
			Identifier thumb = TutorialConfig.BG_IMAGE.equals(bg.bgType) ? ImageStore.texture(bg.bgImage) : null;
			if (thumb != null) {
				gfx.blit(thumb, s[0], s[1], s[0] + s[2], s[1] + s[3], 0f, 1f, 0f, 1f);
			} else {
				gfx.fill(s[0], s[1], s[0] + s[2], s[1] + s[3], swatch(bg));
			}
			int border = (i == pageIndex) ? 0xFFFFD24A : 0xFF404048;
			outline(gfx, s[0], s[1], s[2], s[3], border);
			gfx.text(font, Component.literal(String.valueOf(i + 1)), s[0] + 3, s[1] + 3, 0xFFFFFFFF);
			gfx.text(font, Component.literal(bg.bgType), s[0] + 3, s[1] + s[3] - 11, 0xFFB0B0C0);
		}
	}

	private int swatch(Page pg) {
		if (TutorialConfig.BG_PANORAMA.equals(pg.bgType)) return 0xFF35506F;
		if (TutorialConfig.BG_IMAGE.equals(pg.bgType)) return 0xFF555555;
		return pg.bgColor;
	}

	/** Pixel rect {x, y, w, h} of slide thumbnail {@code i} in the strip. */
	private int[] slideRect(int i) {
		int sx = 6;
		int sw = STRIP_W - 12;
		int sy = TOP_BAR + 14 + i * (SLIDE_H + SLIDE_GAP);
		return new int[]{sx, sy, sw, SLIDE_H};
	}

	/** Slide index under a strip point, or -1. */
	private int slideAt(double mx, double my) {
		if (mx < 0 || mx > STRIP_W) return -1;
		for (int i = 0; i < cfg.pages.size(); i++) {
			int[] s = slideRect(i);
			if (my >= s[1] && my <= s[1] + s[3]) return i;
		}
		return -1;
	}

	// ---- input -------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		if (ev.button() == 0) leftDown = true;

		// The slide filmstrip owns its column: click to switch, drag to reorder.
		if (ev.button() == 0 && ev.x() < STRIP_W && ev.y() > TOP_BAR && ev.y() < height - 26) {
			int i = slideAt(ev.x(), ev.y());
			if (i >= 0) {
				pageIndex = i;
				selected = null;
				draggingSlide = i;
				rebuildWidgets();
			}
			return true;
		}

		if (super.mouseClicked(ev, doubleClick)) return true; // a widget consumed it
		if (ev.button() != 0 || !inCanvas(ev.x(), ev.y())) return false;

		float cmx = canvasMouseX(ev.x());
		float cmy = canvasMouseY(ev.y());

		// Resize handle of the current selection?
		if (selected != null && page().elements.contains(selected)) {
			int[] r = canvasRect(selected);
			float hs = HANDLE_PX / zoom;
			if (cmx >= r[0] + r[2] - hs && cmx <= r[0] + r[2] && cmy >= r[1] + r[3] - hs && cmy <= r[1] + r[3]) {
				drag = Drag.RESIZE;
				return true;
			}
		}

		// Topmost element under the cursor?
		Element hit = elementAt(cmx, cmy);

		if (hit != null) {
			boolean changed = hit != selected;
			selected = hit;
			drag = Drag.MOVE;
			if (changed) rebuildWidgets();

			// Double-click a text element to jump straight into editing it.
			if (doubleClick && TutorialConfig.TYPE_TEXT.equals(hit.type) && primaryTextBox != null) {
				setFocused(primaryTextBox);
				primaryTextBox.setFocused(true);
				primaryTextBox.moveCursorToEnd(false);
				drag = Drag.NONE; // don't drag while editing
			}
			return true;
		}

		// Empty canvas: deselect and pan.
		if (selected != null) { selected = null; rebuildWidgets(); }
		drag = Drag.PAN;
		return true;
	}

	/** Elements ordered for drawing: background layer first, then objects, stable. */
	private List<Element> drawOrder() {
		List<Element> out = new java.util.ArrayList<>();
		for (Element e : page().elements) if (e.layer == TutorialConfig.LAYER_BACKGROUND) out.add(e);
		for (Element e : page().elements) if (e.layer != TutorialConfig.LAYER_BACKGROUND) out.add(e);
		return out;
	}

	/** Topmost element whose canvas rect contains the given canvas-space point. */
	private Element elementAt(float cmx, float cmy) {
		List<Element> els = drawOrder();
		for (int i = els.size() - 1; i >= 0; i--) {
			int[] r = canvasRect(els.get(i));
			if (cmx >= r[0] && cmx <= r[0] + r[2] && cmy >= r[1] && cmy <= r[1] + r[3]) {
				return els.get(i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent ev, double deltaX, double deltaY) {
		// Reordering a slide in the filmstrip (live, as the cursor crosses slots).
		if (draggingSlide >= 0) {
			int target = slideAt(ev.x(), ev.y());
			if (target < 0) {
				target = ev.y() < slideRect(0)[1] ? 0 : cfg.pages.size() - 1;
			}
			if (target != draggingSlide) {
				Page moved = cfg.pages.remove(draggingSlide);
				cfg.pages.add(target, moved);
				draggingSlide = target;
				pageIndex = target;
				rebuildWidgets();
			}
			return true;
		}

		if (drag == Drag.PAN) {
			panX += (float) deltaX;
			panY += (float) deltaY;
			return true;
		}
		if (selected == null) return super.mouseDragged(ev, deltaX, deltaY);

		if (drag == Drag.MOVE) {
			selected.x = snap(clamp01(canvasMouseX(ev.x()) / width));
			selected.y = snap(clamp01(canvasMouseY(ev.y()) / height));
			return true;
		}
		if (drag == Drag.RESIZE) {
			int[] r = canvasRect(selected);
			float newRightX = canvasMouseX(ev.x());
			float newBottomY = canvasMouseY(ev.y());
			float wFrac = clamp01((newRightX - r[0]) / width);
			float hFrac = clamp01((newBottomY - r[1]) / height);
			selected.w = Math.max(0.02f, snap(wFrac));
			selected.h = Math.max(0.02f, snap(hFrac));
			// keep the top-left corner fixed while resizing
			selected.x = clamp01((r[0] / (float) width) + selected.w / 2f);
			selected.y = clamp01((r[1] / (float) height) + selected.h / 2f);
			return true;
		}
		return super.mouseDragged(ev, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent ev) {
		if (ev.button() == 0) { leftDown = false; drag = Drag.NONE; draggingSlide = -1; }
		return super.mouseReleased(ev);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (vertical == 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		float factor = vertical > 0 ? 1.1f : (1f / 1.1f);

		// Hold LMB and scroll to resize the element under the cursor (text/image/etc).
		if (leftDown) {
			Element e = elementAt(canvasMouseX(mouseX), canvasMouseY(mouseY));
			if (e == null) e = selected;
			if (e != null) {
				e.w = clamp(e.w * factor, 0.02f, 1f);
				e.h = clamp(e.h * factor, 0.02f, 1f);
				if (TutorialConfig.TYPE_TEXT.equals(e.type)) {
					e.scale = clamp(e.scale * factor, 0.1f, 12f);
				}
				selected = e;
				return true;
			}
		}

		// Plain scroll zooms the whole canvas around the cursor.
		float old = zoom;
		zoom = clamp(old * factor, 0.25f, 4f);
		float cx = (float) ((mouseX - panX) / old);
		float cy = (float) ((mouseY - panY) / old);
		panX = (float) (mouseX - cx * zoom);
		panY = (float) (mouseY - cy * zoom);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent ev) {
		// Delete key removes the selected object (unless a text field is focused).
		if (ev.key() == InputConstants.KEY_DELETE && selected != null && !(getFocused() instanceof EditBox)) {
			deleteSelected();
			return true;
		}
		return super.keyPressed(ev);
	}

	@Override
	public void onClose() {
		closeEditor();
	}

	// ---- small utils -------------------------------------------------------

	private static String onOff(boolean b) {
		return b ? "ON" : "OFF";
	}

	private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
	private static float clamp01(float v) { return clamp(v, 0f, 1f); }

	private static String hex(int c) { return String.format("%08X", c); }

	private static int parseColor(String s, int fallback) {
		try {
			String t = s.trim().replace("#", "");
			long v = Long.parseLong(t, 16);
			if (t.length() <= 6) v |= 0xFF000000L;
			return (int) v;
		} catch (Exception e) {
			return fallback;
		}
	}

	private static float parseFloat(String s, float fallback) {
		try { return Float.parseFloat(s.trim()); } catch (Exception e) { return fallback; }
	}

	private static String trimFloat(float f) {
		return (f == Math.rint(f)) ? String.valueOf((int) f) : String.valueOf(f);
	}
}
