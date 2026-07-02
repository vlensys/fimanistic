package vlensys.fimanistic.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import vlensys.fimanistic.client.screen.EditorScreen;

/**
 * Hooks the editor into Mod Menu's per-mod settings (gear) button.
 */
public class FimanisticModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new EditorScreen(FimanisticClient.CONFIG, parent);
	}
}
