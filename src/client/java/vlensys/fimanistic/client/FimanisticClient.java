package vlensys.fimanistic.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import vlensys.fimanistic.Fimanistic;
import vlensys.fimanistic.client.config.TutorialConfig;
import vlensys.fimanistic.client.screen.TutorialScreen;

import java.nio.file.Files;

public class FimanisticClient implements ClientModInitializer {

	/** Single shared, mutable config instance for the session. */
	public static TutorialConfig CONFIG;

	private boolean firstLaunchHandled = false;

	@Override
	public void onInitializeClient() {
		CONFIG = TutorialConfig.load();
		try {
			Files.createDirectories(ImageStore.dir());
		} catch (Exception e) {
			Fimanistic.LOGGER.warn("Could not create images dir", e);
		}

		// Show the guide once, the first time the main menu appears.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (firstLaunchHandled || CONFIG.firstLaunchDone) return;
			if (mc.screen instanceof TitleScreen) {
				firstLaunchHandled = true;
				mc.setScreen(new TutorialScreen(CONFIG, mc.screen, false, 0));
			}
		});
	}
}
