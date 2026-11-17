package emaki.jiuwu.craft.corelib.text;

import java.util.Map;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public interface LogMessages {

    String message(String key);

    String message(String key, Map<String, ?> replacements);

    void info(LogTagType tag, String key);

    void info(LogTagType tag, String key, Map<String, ?> replacements);

    void warning(LogTagType tag, String key);

    void warning(LogTagType tag, String key, Map<String, ?> replacements);

    void severe(LogTagType tag, String key);

    void severe(LogTagType tag, String key, Map<String, ?> replacements);
}
