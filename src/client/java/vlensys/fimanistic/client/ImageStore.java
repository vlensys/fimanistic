package vlensys.fimanistic.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import vlensys.fimanistic.Fimanistic;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads PNG files the user drops into {@code config/fimanistic/images/} and
 * registers them as dynamic GUI textures. Kept deliberately simple: drop a file
 * in the folder, pick it by name in the editor. Textures are loaded lazily and
 * cached so a guide referencing the same image many times costs one upload.
 */
public final class ImageStore {

	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private ImageStore() {}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("fimanistic").resolve("images");
	}

	/** PNG filenames available to pick, sorted alphabetically. */
	public static List<String> list() {
		List<String> out = new ArrayList<>();
		Path d = dir();
		if (!Files.isDirectory(d)) return out;
		try (Stream<Path> s = Files.list(d)) {
			s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
					.map(p -> p.getFileName().toString())
					.sorted()
					.forEach(out::add);
		} catch (Exception e) {
			Fimanistic.LOGGER.warn("Could not list image dir {}", d, e);
		}
		return out;
	}

	/**
	 * Returns the texture id for a filename, loading+registering it on first use.
	 * Returns {@code null} if the file is missing or not a readable PNG, so callers
	 * can fall back gracefully.
	 */
	public static Identifier texture(String filename) {
		if (filename == null || filename.isBlank()) return null;
		Identifier cached = CACHE.get(filename);
		if (cached != null) return cached;

		Path file = dir().resolve(filename);
		if (!Files.isRegularFile(file)) return null;

		try (InputStream in = Files.newInputStream(file)) {
			NativeImage img = NativeImage.read(in);
			DynamicTexture tex = new DynamicTexture(() -> "fimanistic/" + filename, img);
			Identifier id = Fimanistic.id("dynimg/" + sanitize(filename));
			Minecraft.getInstance().getTextureManager().register(id, tex);
			CACHE.put(filename, id);
			return id;
		} catch (Exception e) {
			Fimanistic.LOGGER.warn("Failed to load image {}", file, e);
			return null;
		}
	}

	/**
	 * Eagerly load+register every PNG in the images folder so the textures exist
	 * before rendering. Call this outside the GUI extract phase (e.g. from
	 * {@code Screen.init()} or an input handler): {@link #texture} allocates a GPU
	 * texture, but the extract pass only records draw commands and must not do GPU
	 * work. After this runs, rendering just reads the cache.
	 */
	public static void preloadAll() {
		for (String name : list()) {
			texture(name);
		}
	}

	/** Forget cached textures so freshly added/edited files are re-read. */
	public static void invalidate() {
		CACHE.clear();
	}

	/** Identifier paths only allow [a-z0-9/._-]; squash everything else. */
	private static String sanitize(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
		return s.isEmpty() ? "img" : s;
	}
}
