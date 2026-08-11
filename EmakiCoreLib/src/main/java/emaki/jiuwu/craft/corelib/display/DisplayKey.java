package emaki.jiuwu.craft.corelib.display;

import java.util.Objects;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * 展示实体的三段式身份。
 *
 * <p>{@code namespace} 区分调用方（如 {@code emakicooking} / {@code emakiattribute}），
 * {@code group} 是可批量清理的分组（如工位坐标、目标实体 UUID），
 * {@code id} 是分组内的条目标识（如 {@code info} / {@code ingredient_0}）。
 *
 * @param namespace 调用方命名空间
 * @param group     分组标识
 * @param id        分组内条目标识
 */
public record DisplayKey(String namespace, String group, String id) {

    public DisplayKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(id, "id");
    }

    /** {@return 分组级键，用于 {@code removeGroup}} */
    public String groupKey() {
        return namespace + ":" + group;
    }

    /** {@return 条目级键，唯一标识一个展示实体} */
    public String runtimeKey() {
        return groupKey() + ":" + id;
    }

    /** {@return 命名空间前缀，用于 {@code removeNamespace} 的前缀匹配} */
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
