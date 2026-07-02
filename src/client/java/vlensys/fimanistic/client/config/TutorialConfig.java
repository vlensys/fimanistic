package vlensys.fimanistic.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import vlensys.fimanistic.Fimanistic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole mod is data-driven by this single JSON document, stored in
 * {@code config/fimanistic.json}. It is intentionally a flat, Gson-friendly
 * POJO graph (public fields, no logic) so it stays tiny and trivially
 * serializable. Coordinates are stored as fractions of the screen (0..1) so a
 * guide authored at one resolution renders correctly at any other.
 */
public class TutorialConfig {

	/** Element kinds. Stored as a string for forward-compatibility. */
	public static final String TYPE_TEXT = "text";
	public static final String TYPE_IMAGE = "image";
	public static final String TYPE_CONTROL = "control";
	public static final String TYPE_SETTING = "setting";

	public int schema = 1;
	/** Set true once the player has dismissed the first-launch guide. */
	public boolean firstLaunchDone = false;
	/** Editor preference: draw the alignment grid. */
	public boolean gridEnabled = true;
	/** Number of grid cells across/down used for snapping (resolution independent). */
	public int gridDivisions = 24;
	/** Arbitrary key/value store that {@code setting} elements read and write. */
	public Map<String, String> modSettings = new LinkedHashMap<>();
	/**
	 * When true, {@link #globalBackground} is drawn behind every page, overriding
	 * each page's own background. When false, every page uses its own.
	 */
	public boolean bgAllPages = false;
	/**
	 * The shared "all pages" background. Only its {@code bg*} fields are used
	 * (its element list is ignored); reusing {@link Page} lets the same editor and
	 * renderer code handle both the per-page and the shared background.
	 */
	public Page globalBackground = new Page();
	public List<Page> pages = new ArrayList<>();

	/** The two stacked layers an element can live on. */
	public static final int LAYER_BACKGROUND = 0;
	public static final int LAYER_OBJECTS = 1;

	/** Page background kinds. */
	public static final String BG_PANORAMA = "panorama";
	public static final String BG_IMAGE = "image";
	public static final String BG_COLOR = "color";

	public static class Page {
		/** Background kind: panorama (default), image, or colour. */
		public String bgType = BG_PANORAMA;
		/** ARGB background fill, used when {@link #bgType} is {@code color}. */
		public int bgColor = 0xFF101018;
		/** Background image filename, used when {@link #bgType} is {@code image}. */
		public String bgImage = "";
		public List<Element> elements = new ArrayList<>();
	}

	/**
	 * One placed item. A single flexible struct covers all element kinds to keep
	 * the model lightweight; only the fields relevant to {@link #type} are used.
	 */
	public static class Element {
		public String type = TYPE_TEXT;
		/** 0 = background layer, 1 = text/objects layer. */
		public int layer = LAYER_OBJECTS;
		// Position/size as fractions of the screen.
		public float x = 0.4f;
		public float y = 0.4f;
		public float w = 0.2f;
		public float h = 0.08f;

		// TEXT
		public String text = "Text";
		public int color = 0xFFFFFFFF;
		public float scale = 1.0f;
		public boolean shadow = true;
		public boolean center = false;

		// IMAGE
		public String image = "";

		// CONTROL (rebinds a vanilla/mod keybind when applied)
		public String keyMapping = "key.jump";   // KeyMapping.getName()
		public String keyName = "key.keyboard.space"; // InputConstants key name

		// SETTING (writes into modSettings when applied)
		public String settingKey = "example";
		public String settingValue = "true";

		public Element copy() {
			Element e = new Element();
			e.type = type; e.layer = layer; e.x = x; e.y = y; e.w = w; e.h = h;
			e.text = text; e.color = color; e.scale = scale; e.shadow = shadow; e.center = center;
			e.image = image;
			e.keyMapping = keyMapping; e.keyName = keyName;
			e.settingKey = settingKey; e.settingValue = settingValue;
			return e;
		}
	}

	/**
	 * The background to actually draw for {@code page}: the shared "all pages"
	 * background when {@link #bgAllPages} is set, otherwise the page's own.
	 */
	public Page effectiveBackground(Page page) {
		return bgAllPages ? globalBackground : page;
	}

	// ---- persistence -------------------------------------------------------

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("fimanistic.json");
	}

	public static TutorialConfig load() {
		Path p = path();
		if (Files.exists(p)) {
			try {
				String json = Files.readString(p, StandardCharsets.UTF_8);
				TutorialConfig cfg = GSON.fromJson(json, TutorialConfig.class);
				if (cfg != null) {
					cfg.normalize();
					return cfg;
				}
			} catch (Exception e) {
				Fimanistic.LOGGER.warn("Failed to read {}, using defaults", p, e);
			}
		}
		TutorialConfig cfg = defaults();
		cfg.save();
		return cfg;
	}

	public void save() {
		Path p = path();
		try {
			Files.createDirectories(p.getParent());
			Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Fimanistic.LOGGER.error("Failed to write {}", p, e);
		}
	}

	/** Guard against malformed/partial JSON producing null collections. */
	private void normalize() {
		if (modSettings == null) modSettings = new LinkedHashMap<>();
		if (pages == null) pages = new ArrayList<>();
		if (gridDivisions < 2) gridDivisions = 24;
		if (globalBackground == null) globalBackground = new Page();
		if (globalBackground.bgType == null || globalBackground.bgType.isBlank()) {
			globalBackground.bgType = BG_PANORAMA;
		}
		for (Page pg : pages) {
			if (pg.elements == null) pg.elements = new ArrayList<>();
			if (pg.bgType == null || pg.bgType.isBlank()) {
				// Migrate older configs: a set image stays an image, else colour.
				pg.bgType = (pg.bgImage != null && !pg.bgImage.isBlank()) ? BG_IMAGE : BG_COLOR;
			}
			for (Element e : pg.elements) {
				if (e.type == null) e.type = TYPE_TEXT;
				if (e.layer != LAYER_BACKGROUND) e.layer = LAYER_OBJECTS;
			}
		}
	}

	/** A friendly starter guide so first launch is never blank. */
	public static TutorialConfig defaults() {
		TutorialConfig c = new TutorialConfig();

		Page p1 = new Page();
		p1.bgColor = 0xFF12131A;
		Element title = new Element();
		title.type = TYPE_TEXT; title.text = "Welcome to the pack!";
		title.x = 0.5f; title.y = 0.30f; title.w = 0.6f; title.h = 0.1f;
		title.scale = 2.0f; title.center = true; title.color = 0xFFFFE08A;
		Element sub = new Element();
		sub.type = TYPE_TEXT; sub.text = "This short guide shows up once. Edit it in Mod Menu → Fimanistic.";
		sub.x = 0.5f; sub.y = 0.45f; sub.w = 0.7f; sub.h = 0.1f;
		sub.scale = 1.0f; sub.center = true; sub.color = 0xFFCFCFE0;
		p1.elements.add(title);
		p1.elements.add(sub);

		Page p2 = new Page();
		p2.bgColor = 0xFF14171F;
		Element t2 = new Element();
		t2.type = TYPE_TEXT; t2.text = "Tip: you can rebind keys and flip mod settings from the guide itself.";
		t2.x = 0.5f; t2.y = 0.4f; t2.w = 0.7f; t2.h = 0.1f;
		t2.center = true; t2.color = 0xFFFFFFFF;
		p2.elements.add(t2);

		c.pages.add(p1);
		c.pages.add(p2);
		return c;
	}
}
