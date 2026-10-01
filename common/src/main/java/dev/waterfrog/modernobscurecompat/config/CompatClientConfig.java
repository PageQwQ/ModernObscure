package dev.waterfrog.modernobscurecompat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Loader-agnostic client config for the compat mod, persisted as a plain JSON
 * file under the vanilla config directory. Using {@link Minecraft#gameDirectory}
 * keeps this class free of any Fabric/NeoForge config API so the exact same
 * source works on every loader.
 *
 * <p>The Cloth Config screen ({@link CompatConfigScreen}) mutates the values
 * through the static setters and calls {@link #save()}.
 */
public final class CompatClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "modernobscure-compat-client.json";

    private static CompatClientConfig instance;

    // ---- persisted values ----
    private boolean enabled = true;
    private boolean smoothMovement = true;
    private double movementSpeed = 2.0;
    private double snapDistance = 48.0;
    private boolean smoothSize = true;
    private double sizeSpeed = 1.5;
    private double startScale = 0.9;

    public static boolean isEnabled() {
        return get().enabled;
    }

    public static void setEnabled(boolean value) {
        get().enabled = value;
    }

    public static boolean isSmoothMovement() {
        return get().smoothMovement;
    }

    public static void setSmoothMovement(boolean value) {
        get().smoothMovement = value;
    }

    public static double getMovementSpeed() {
        return get().movementSpeed;
    }

    public static void setMovementSpeed(double value) {
        get().movementSpeed = value;
    }

    public static double getSnapDistance() {
        return get().snapDistance;
    }

    public static void setSnapDistance(double value) {
        get().snapDistance = value;
    }

    public static boolean isSmoothSize() {
        return get().smoothSize;
    }

    public static void setSmoothSize(boolean value) {
        get().smoothSize = value;
    }

    public static double getSizeSpeed() {
        return get().sizeSpeed;
    }

    public static void setSizeSpeed(double value) {
        get().sizeSpeed = value;
    }

    public static double getStartScale() {
        return get().startScale;
    }

    public static void setStartScale(double value) {
        get().startScale = value;
    }

    public static CompatClientConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    private static void load() {
        CompatClientConfig cfg = new CompatClientConfig();
        try {
            File file = configFile();
            if (file != null && file.isFile()) {
                try (FileReader reader = new FileReader(file)) {
                    CompatClientConfig read = GSON.fromJson(reader, CompatClientConfig.class);
                    if (read != null) {
                        cfg = read;
                    }
                }
            } else {
                write(cfg);
            }
        } catch (Exception ignored) {
            // Fall back to the in-memory defaults.
        }
        cfg.sanitize();
        instance = cfg;
    }

    /** Writes the current in-memory values back to the JSON file. */
    public static void save() {
        CompatClientConfig cfg = get();
        cfg.sanitize();
        write(cfg);
    }

    private static void write(CompatClientConfig cfg) {
        try {
            File file = configFile();
            if (file == null) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(cfg, writer);
            }
        } catch (Exception ignored) {
            // Nothing we can do about a broken config path.
        }
    }

    private void sanitize() {
        if (movementSpeed <= 0.0) {
            movementSpeed = 2.0;
        }
        if (snapDistance < 0.0) {
            snapDistance = 48.0;
        }
        if (sizeSpeed <= 0.0) {
            sizeSpeed = 1.5;
        }
        if (startScale <= 0.0 || startScale > 1.0) {
            startScale = 0.9;
        }
    }

    private static File configFile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return null;
        }
        return new File(new File(minecraft.gameDirectory, "config"), FILE_NAME);
    }
}
