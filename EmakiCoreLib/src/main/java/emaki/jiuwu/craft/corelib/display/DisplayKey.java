package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record DisplayKey(String namespace, String group, String id) {

    public DisplayKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(id, "id");
    }

    public String groupKey() {
        return namespace + ":" + group;
    }

    public String runtimeKey() {
        return groupKey() + ":" + id;
    }

    public String namespacePrefix() {
        return namespace + ":";
    }

    public static boolean isValid(DisplayKey key) {
        return key != null
                && Texts.isNotBlank(key.namespace())
                && Texts.isNotBlank(key.group())
                && Texts.isNotBlank(key.id());
    }
}
