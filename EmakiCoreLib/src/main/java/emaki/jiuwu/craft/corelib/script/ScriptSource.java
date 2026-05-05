package emaki.jiuwu.craft.corelib.script;

import java.nio.file.Path;

public record ScriptSource(String logicalPath,
        Path physicalPath,
        String content,
        long lastModifiedMillis,
        String sha256) {
}
