package vlensys.fimanistic.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import vlensys.fimanistic.client.Actions;
import vlensys.fimanistic.client.ImageStore;
import vlensys.fimanistic.client.config.TutorialConfig;
import vlensys.fimanistic.client.config.TutorialConfig.Element;
import vlensys.fimanistic.client.config.TutorialConfig.Page;

import java.util.List;

/**
 * Stateless drawing of pages/elements given an already-resolved pixel rect.
 * Shared by the viewer and the editor so both render identically.
 */
public final class CanvasRenderer {

	private CanvasRenderer() {}

	/**
	 * Resolve an element's fractional center/size into pixel {x, y, w, h} within a
	 * canvas of {@code W x H} pixels. Elements are anchored by their centre.
	 */
	public static int[] rect(Element e, int canvasW, int canvasH) {
		int pw = Math.max(1, Math.round(e.w * canvasW));
		int ph = Math.max(1, Math.round(e.h * canvasH));
		int px = Math.round(e.x * canvasW - pw / 2f);
		int py = Math.round(e.y * canvasH - ph / 2f);
		return new int[]{px, py, pw, ph};
	}

	/**
	 * Draw the page background. A {@code panorama} (the live title cubemap) is used
	 * when the page's background kind is panorama; otherwise an image or solid fill.
	 * The panorama always fills the whole screen, so it ignores the given rect.
	 */
	public static void drawBackground(GuiGraphicsExtractor gfx, Page page, int x, int y, int w, int h,
									  net.minecraft.client.renderer.Panorama panorama, int mouseX, int mouseY) {
		if (page == null) {
			gfx.fill(x, y, x + w, y + h, 0xFF101018);
			return;
		}
		if (TutorialConfig.BG_PANORAMA.equals(page.bgType) && panorama != null) {
			panorama.extractRenderState(gfx, mouseX, mouseY, true);
			return;
		}
		if (TutorialConfig.BG_IMAGE.equals(page.bgType)) {
			Identifier bg = ImageStore.texture(page.bgImage);
			if (bg != null) {
				// blit(id, x0, y0, x1, y1, u0, u1, v0, v1) — corner coords, interleaved UVs.
				gfx.blit(bg, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
				return;
			}
		}
		gfx.fill(x, y, x + w, y + h, page.bgColor);
	}

	/** Draw one element inside its pixel rect. {@code cfg} may be null. */
	public static void drawElement(GuiGraphicsExtractor gfx, Font font, Element e,
								   int px, int py, int pw, int ph, TutorialConfig cfg) {
		switch (e.type == null ? "" : e.type) {
			case "image" -> {
				Identifier id = ImageStore.texture(e.image);
				if (id != null) {
					gfx.blit(id, px, py, px + pw, py + ph, 0f, 1f, 0f, 1f);
				} else {
					gfx.fill(px, py, px + pw, py + ph, 0x40FFFFFF);
					gfx.centeredText(font, Component.literal("[image]"), px + pw / 2, py + ph / 2 - 4, 0xFFAAAAAA);
				}
			}
			case "control" -> {
				net.minecraft.client.KeyMapping km = Actions.findKeyMapping(e.keyMapping);
				String key = km != null ? km.getTranslatedKeyMessage().getString() : "?";
				drawActionBox(gfx, font, e.text + "  [" + key + "]", px, py, pw, ph, 0xFF2B3A55, 0xFF6F8FD0);
			}
			case "setting" -> {
				boolean on = cfg != null && Actions.settingState(e, cfg);
				int fill = on ? 0xFF2B5540 : 0xFF55302B;
				int border = on ? 0xFF6FD08F : 0xFFD08F6F;
				drawActionBox(gfx, font, e.text + ": " + (on ? "ON" : "OFF"), px, py, pw, ph, fill, border);
			}
			default -> drawText(gfx, font, e, px, py, pw, ph);
		}
	}

	private static void drawText(GuiGraphicsExtractor gfx, Font font, Element e, int px, int py, int pw, int ph) {
		float scale = e.scale <= 0 ? 1f : e.scale;
	
		int wrap = Math.max(1, (int) (pw / scale));
		int boxH = Math.max(1, (int) (ph / scale));
		int lineStep = font.lineHeight + 1;
		List<FormattedCharSequence> lines = font.split(Component.literal(e.text), wrap);


		int blockH = Math.max(lineStep, lines.size() * lineStep);
		int startY = e.center ? Math.max(0, (boxH - blockH) / 2) : 0;

		Matrix3x2fStack pose = gfx.pose();
		pose.pushMatrix();
		pose.translate(px, py);
		pose.scale(scale);
		int ly = startY;
		for (FormattedCharSequence line : lines) {
			if (e.center) {
				int lw = font.width(line);
				gfx.text(font, line, (wrap - lw) / 2, ly, e.color, e.shadow);
			} else {
				gfx.text(font, line, 0, ly, e.color, e.shadow);
			}
			ly += lineStep;
		}
		pose.popMatrix();
	}

	private static void drawActionBox(GuiGraphicsExtractor gfx, Font font, String label,
									 int px, int py, int pw, int ph, int fill, int border) {
		gfx.fill(px, py, px + pw, py + ph, fill);
		gfx.fill(px, py, px + pw, py + 1, border);
		gfx.fill(px, py + ph - 1, px + pw, py + ph, border);
		gfx.fill(px, py, px + 1, py + ph, border);
		gfx.fill(px + pw - 1, py, px + pw, py + ph, border);
		gfx.centeredText(font, Component.literal(label), px + pw / 2, py + ph / 2 - 4, 0xFFFFFFFF);
	}
}
