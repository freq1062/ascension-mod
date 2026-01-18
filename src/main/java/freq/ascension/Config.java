package freq.ascension;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Config {
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("ascension.properties");

    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    private final List<ConfigOption> options;

    private Config(List<ConfigOption> options) {
        this.options = options;
        // populate defaults
        for (ConfigOption opt : options)
            values.put(opt.key(), opt.defaultValue());
    }

    // A small container for a key, default value, and comment.
    private record ConfigOption(String key, Object defaultValue, String comment) {
    }

    /* ========== Define your options concisely here ========== */
    private static List<ConfigOption> buildOptions() {
        return List.of(
                new ConfigOption("spells_damage_teammates", false,
                        "Whether spells deal damage to teammates."),
                new ConfigOption("earth", true,
                        "Earth abilities unlocking enabled."),
                new ConfigOption("sky", true,
                        "Sky abilities unlocking enabled."),
                new ConfigOption("ocean", true,
                        "Ocean abilities unlocking enabled."),
                new ConfigOption("flora", true,
                        "Flora abilities unlocking enabled."),
                new ConfigOption("magic", true,
                        "Magic abilities unlocking enabled."),
                new ConfigOption("nether", true,
                        "Nether abilities unlocking enabled."),
                new ConfigOption("end", true,
                        "End abilities unlocking enabled."),

                new ConfigOption("earth_god", true,
                        "Earth god ascending enabled."),
                new ConfigOption("sky_god", true,
                        "Sky god ascending enabled."),
                new ConfigOption("ocean_god", true,
                        "Ocean god ascending enabled."),
                new ConfigOption("flora_god", true,
                        "Flora god ascending enabled."),
                new ConfigOption("magic_god", true,
                        "Magic god ascending enabled."),
                new ConfigOption("nether_god", true,
                        "Nether god ascending enabled."),
                new ConfigOption("end_god", true,
                        "End god ascending enabled."),
                new ConfigOption("god_death_cooldown", 86400,
                        "How long a player must wait after dying as a god before they can become god again, in s"),
                new ConfigOption("influence_ban_duration", 86400,
                        "How long a player is banned when their influence drops below -5."));
    }

    /* ========== Loading / saving ========== */
    public static Config load() throws IOException {
        List<ConfigOption> opts = buildOptions();
        Config cfg = new Config(opts);

        // Read existing properties if present
        Properties p = new Properties();
        Files.createDirectories(CONFIG_DIR);
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                p.load(in);
            }
        }

        // For each option, parse the string into the proper type (fallback to default
        // on parse errors)
        for (ConfigOption opt : opts) {
            String key = opt.key();
            Object def = opt.defaultValue();
            String prop = p.getProperty(key);
            Object value = def;
            try {
                if (def instanceof Integer) {
                    value = prop != null ? Integer.parseInt(prop.trim()) : def;
                } else if (def instanceof Long) {
                    value = prop != null ? Long.parseLong(prop.trim()) : def;
                } else if (def instanceof Double) {
                    value = prop != null ? Double.parseDouble(prop.trim()) : def;
                } else if (def instanceof Boolean) {
                    value = prop != null ? Boolean.parseBoolean(prop.trim()) : def;
                } else { // treat as string
                    value = prop != null ? prop : def;
                }
            } catch (NumberFormatException ignore) {
                /* keep default */ }

            cfg.values.put(key, value);
            p.setProperty(key, value.toString()); // ensure property exists for saving
        }

        // Save back with pretty formatting (comments, blank lines, order)
        cfg.savePretty();

        return cfg;
    }

    /**
     * Writes a nicer, human-friendly properties file with comments and blank lines.
     * This is done instead of Properties.store() so we can control
     * ordering/comments.
     */
    private void savePretty() throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(CONFIG_FILE)) {
            w.write("# Ascension mod settings");
            w.newLine();
            w.write("# Edit the values below. Blank lines and comments are preserved here.");
            w.newLine();
            w.newLine();

            // Example of grouping: we infer groups by comments embedded in
            // ConfigOption.comment (you design)
            for (ConfigOption opt : options) {
                String comment = opt.comment();
                if (comment != null && !comment.isBlank()) {
                    // allow multi-line comments separated by '\n'
                    String[] lines = comment.split("\n");
                    for (String c : lines) {
                        w.write("# " + c.trim());
                        w.newLine();
                    }
                }
                // key = value
                Object val = values.get(opt.key());
                w.write(opt.key() + " = " + String.valueOf(val));
                w.newLine();
                w.newLine(); // blank line between entries for readability
            }
            w.flush();
        }
    }

    /* ========== Typed getters ========== */
    public int getInt(String key) {
        return asType(key, Integer.class);
    }

    public long getLong(String key) {
        return asType(key, Long.class);
    }

    public double getDouble(String key) {
        return asType(key, Double.class);
    }

    public boolean getBoolean(String key) {
        return asType(key, Boolean.class);
    }

    public String getString(String key) {
        return asType(key, String.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T asType(String key, Class<T> cls) {
        if (!values.containsKey(key))
            throw new IllegalArgumentException("Unknown config key: " + key);
        Object v = values.get(key);
        if (cls.isInstance(v))
            return (T) v;
        // attempt conversions for common mismatches (e.g., integer stored as Long)
        if (cls == Integer.class && v instanceof Number)
            return (T) Integer.valueOf(((Number) v).intValue());
        if (cls == Long.class && v instanceof Number)
            return (T) Long.valueOf(((Number) v).longValue());
        if (cls == Double.class && v instanceof Number)
            return (T) Double.valueOf(((Number) v).doubleValue());
        if (cls == String.class)
            return (T) v.toString();
        if (cls == Boolean.class && v instanceof String)
            return (T) Boolean.valueOf((String) v);
        throw new ClassCastException("Config key '" + key + "' is not of type " + cls.getSimpleName());
    }

    /* ========== Optional: set and persist at runtime ========== */
    public void set(String key, Object value) throws IOException {
        if (!values.containsKey(key))
            throw new IllegalArgumentException("Unknown config key: " + key);
        values.put(key, value);
        savePretty();
    }
}
