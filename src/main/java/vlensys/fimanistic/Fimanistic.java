package vlensys.fimanistic;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fimanistic is a client-only mod, so this common initializer is intentionally
 * tiny. All of the real work happens in {@code vlensys.fimanistic.client}.
 */
public class Fimanistic implements ModInitializer {
	public static final String MOD_ID = "fimanistic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Fimanistic loaded.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
