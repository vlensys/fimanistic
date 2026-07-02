package vlensys.fimanistic.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import vlensys.fimanistic.client.config.TutorialConfig;
import vlensys.fimanistic.client.config.TutorialConfig.Element;

/**
 * Helpers shared by the guide's native key-binding and toggle widgets.
 */
public final class Actions {

	private Actions() {}

	/** Find a vanilla/mod keybinding by its name (e.g. {@code key.jump}), or null. */
	public static KeyMapping findKeyMapping(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options == null || name == null) return null;
		for (KeyMapping km : mc.options.keyMappings) {
			if (km.getName().equals(name)) return km;
		}
		return null;
	}

	/**
	 * Names (IDs) of every registered keybinding — vanilla and every loaded mod's —
	 * sorted for a stable browse order. These are the values a {@code control}
	 * element's keybind id can be set to.
	 */
	public static java.util.List<String> allKeyMappingNames() {
		Minecraft mc = Minecraft.getInstance();
		java.util.List<String> out = new java.util.ArrayList<>();
		if (mc.options == null) return out;
		for (KeyMapping km : mc.options.keyMappings) out.add(km.getName());
		out.sort(String::compareToIgnoreCase);
		return out;
	}

	/** Current on/off state of a setting (falls back to the element's default). */
	public static boolean settingState(Element e, TutorialConfig cfg) {
		return Boolean.parseBoolean(cfg.modSettings.getOrDefault(e.settingKey, e.settingValue));
	}

	/** Persist a setting value into the mod's own store. */
	public static void setSetting(String key, boolean value, TutorialConfig cfg) {
		cfg.modSettings.put(key, String.valueOf(value));
		cfg.save();
	}
}
