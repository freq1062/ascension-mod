package freq.ascension.config;

import java.util.LinkedHashMap;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

public class ConfigGroup {
    private final String prefix;
    private final LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> comments = new LinkedHashMap<>();

    public ConfigGroup(String prefix) {
        this.prefix = prefix;
    }

    public ConfigGroup add(String key, int defaultValue) {
        values.put(key, defaultValue);
        return this;
    }

    public ConfigGroup add(String key, int defaultValue, String comment) {
        values.put(key, defaultValue);
        comments.put(key, comment);
        return this;
    }

    public void load(CommentedFileConfig config) {
        values.replaceAll((key, def) -> config.getOrElse(prefix + "." + key, def));
    }

    public void setAll(CommentedFileConfig config) {
        values.forEach((key, value) -> {
            String fullKey = prefix + "." + key;
            config.set(fullKey, value);
            if (comments.containsKey(key)) {
                config.setComment(fullKey, comments.get(key));
            }
        });
    }

    public int get(String key) {
        return values.get(key);
    }
}