package emaki.jiuwu.craft.corelib.text;

import java.util.Map;

public interface LogMessages {

    String message(String key);

    String message(String key, Map<String, ?> replacements);

    void info(String key);

    void info(String key, Map<String, ?> replacements);

    void warning(String key);

    void warning(String key, Map<String, ?> replacements);

    void severe(String key);

    void severe(String key, Map<String, ?> replacements);
}
