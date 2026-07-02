package vlensys.fimanistic.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.network.chat.Component;
import vlensys.fimanistic.client.Actions;
import vlensys.fimanistic.client.ImageStore;
import vlensys.fimanistic.client.config.TutorialConfig;
import vlensys.fimanistic.client.config.TutorialConfig.Element;
import vlensys.fimanistic.client.config.TutorialConfig.Page;

/**
 * The first-launch viewer. Flips through authored pages. Control and setting
 * elements are rendered as the <em>actual</em> vanilla widgets — a key-binding
 * button (click to rebind, just like the Controls screen) and an On/Off toggle —
 * so they behave exactly as players expect. Finishing marks the guide as seen.
 */
public class TutorialScreen extends Screen {

	private final TutorialConfig cfg;
	private final Screen parent;
	/** When true this is just a preview opened from the editor; never marks "done". */
	private final boolean preview;
	private int page;

	/** The keybinding currently awaiting a new key (vanilla rebind behaviour). */
	private KeyMapping bindingTarget;

	/** Live title-screen panorama, used for panorama-backed pages. */
	private final Panorama panorama = new Panorama();

	public TutorialScreen(TutorialConfig cfg, Screen parent, boolean preview, int startPage) {
		super(Component.translatable("fimanistic.config.title"));
		this.cfg = cfg;
		this.parent = parent;
		this.preview = preview;
		this.page = Math.max(0, Math.min(startPage, Math.max(0, cfg.pages.size() - 1)));
	}

	@Override
	protected void init() {
		// Register image textures on the render thread, before the extract pass.
		ImageStore.preloadAll();

		// Live native widgets for control/setting elements.
		Page pg = current();
		if (pg != null) {
			for (Element e : pg.elements) {
				if (TutorialConfig.TYPE_CONTROL.equals(e.type)) addKeybindWidget(e);
				else if (TutorialConfig.TYPE_SETTING.equals(e.type)) addToggleWidget(e);
			}
		}

		int cy = height - 28;
		int last = cfg.pages.size() - 1;
		boolean isLast = page >= last;

		// Skip appears on every page except the last.
		if (!isLast) {
			addRenderableWidget(Button.builder(Component.translatable("fimanistic.button.skip"), b -> finish())
					.bounds(10, cy, 60, 20).build());
		}

		if (page > 0) {
			addRenderableWidget(Button.builder(Component.translatable("fimanistic.button.prevPage"), b -> goTo(page - 1))
					.bounds(width / 2 - 105, cy, 50, 20).build());
		}

		if (!isLast) {
			// Non-final pages advance with Next.
			addRenderableWidget(Button.builder(Component.translatable("fimanistic.button.next"), b -> goTo(page + 1))
					.bounds(width / 2 + 55, cy, 50, 20).build());
		} else {
			// Only the final page offers Get started.
			addRenderableWidget(Button.builder(Component.translatable("fimanistic.button.finish"), b -> finish())
					.bounds(width - 110, cy, 100, 20).build());
		}
	}

	/** A vanilla key-binding button: shows the current key and rebinds on click. */
	private void addKeybindWidget(Element e) {
		int[] r = CanvasRenderer.rect(e, width, height);
		KeyMapping km = Actions.findKeyMapping(e.keyMapping);
		Button button = Button.builder(keybindLabel(e, km), b -> {
			bindingTarget = km;          // enter "press a key" mode
			rebuildWidgets();
		}).bounds(r[0], r[1], r[2], r[3]).build();
		button.active = km != null;
		addRenderableWidget(button);
	}

	private Component keybindLabel(Element e, KeyMapping km) {
		String prefix = (e.text == null || e.text.isBlank()) ? "" : e.text + ": ";
		if (km == null) return Component.literal(prefix + "?");
		if (bindingTarget == km) {
			return Component.literal(prefix).append(Component.literal("> ? <"));
		}
		return Component.literal(prefix).append(km.getTranslatedKeyMessage());
	}

	/** A vanilla On/Off toggle bound to the mod setting. */
	private void addToggleWidget(Element e) {
		int[] r = CanvasRenderer.rect(e, width, height);
		boolean initial = Actions.settingState(e, cfg);
		String label = (e.text == null || e.text.isBlank()) ? e.settingKey : e.text;
		CycleButton<Boolean> toggle = CycleButton.onOffBuilder(initial)
				.create(r[0], r[1], r[2], r[3], Component.literal(label),
						(btn, val) -> Actions.setSetting(e.settingKey, val, cfg));
		addRenderableWidget(toggle);
	}

	private void goTo(int p) {
		page = Math.max(0, Math.min(p, cfg.pages.size() - 1));
		bindingTarget = null;
		rebuildWidgets();
	}

	private void finish() {
		if (!preview) {
			cfg.firstLaunchDone = true;
			cfg.save();
		}
		minecraft.setScreen(parent);
	}

	private Page current() {
		return (page >= 0 && page < cfg.pages.size()) ? cfg.pages.get(page) : null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		Page pg = current();
		CanvasRenderer.drawBackground(gfx, cfg.effectiveBackground(pg), 0, 0, width, height, panorama, mouseX, mouseY);
		if (pg != null) {
			// Background layer first, then objects layer. Control/setting are live
			// widgets (rendered later, on top).
			for (int layer = TutorialConfig.LAYER_BACKGROUND; layer <= TutorialConfig.LAYER_OBJECTS; layer++) {
				for (Element e : pg.elements) {
					if (e.layer != layer) continue;
					if (TutorialConfig.TYPE_CONTROL.equals(e.type) || TutorialConfig.TYPE_SETTING.equals(e.type)) continue;
					int[] r = CanvasRenderer.rect(e, width, height);
					CanvasRenderer.drawElement(gfx, font, e, r[0], r[1], r[2], r[3], cfg);
				}
			}
		} else {
			gfx.centeredText(font, Component.translatable("fimanistic.label.empty"), width / 2, height / 2, 0xFFAAAAAA);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		if (!cfg.pages.isEmpty()) {
			gfx.centeredText(font, Component.translatable("fimanistic.label.page",
					(page + 1), cfg.pages.size()), width / 2, height - 24, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
		// While awaiting a key, a mouse button binds to it (vanilla behaviour).
		if (bindingTarget != null) {
			rebind(bindingTarget, InputConstants.Type.MOUSE.getOrCreate(ev.button()));
			return true;
		}
		return super.mouseClicked(ev, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent ev) {
		if (bindingTarget != null) {
			if (ev.key() == InputConstants.KEY_ESCAPE) {
				rebind(bindingTarget, InputConstants.UNKNOWN); // Esc unbinds
			} else {
				rebind(bindingTarget, InputConstants.getKey(ev));
			}
			return true;
		}
		return super.keyPressed(ev);
	}

	private void rebind(KeyMapping km, InputConstants.Key key) {
		km.setKey(key);
		KeyMapping.resetMapping();
		if (minecraft.options != null) minecraft.options.save();
		bindingTarget = null;
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		if (bindingTarget != null) { bindingTarget = null; rebuildWidgets(); return; }
		finish();
	}
}
