package emaki.jiuwu.craft.corelib.config.precheck;

import java.io.File;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record ConfigPrecheckFixAction(Kind kind, File target, String resource) {

    public ConfigPrecheckFixAction {
        kind = kind == null ? Kind.COPY_RESOURCE : kind;
        target = Objects.requireNonNull(target, "target");
        resource = Texts.toStringSafe(resource);
    }

    public enum Kind {
        COPY_RESOURCE,
        CREATE_DIRECTORY,
        MERGE_KEYS
    }
}
