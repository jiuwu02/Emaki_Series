package emaki.jiuwu.craft.corelib.config;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.config.ConfigNodes}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 10 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.config.ConfigNodes}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class ConfigNodes {

    private ConfigNodes() {
    }

    public static Object get(Object mapping, String key) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.get(mapping, key);
    }

    public static boolean contains(Object mapping, String key) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.contains(mapping, key);
    }

    public static Map<String, Object> entries(Object mapping) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.entries(mapping);
    }

    public static Object toPlainData(Object value) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.toPlainData(value);
    }

    public static String string(Object mapping, String key, String defaultValue) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.string(mapping, key, defaultValue);
    }

    public static boolean bool(Object mapping, String key, boolean defaultValue) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.bool(mapping, key, defaultValue);
    }

    public static <E extends Enum<E>> E enumOrDefault(Object value, E defaultValue) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.enumOrDefault(value, defaultValue);
    }

    public static <E extends Enum<E>> E enumOrThrow(Class<E> type, Object value) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.enumOrThrow(type, value);
    }

    public static YamlSection section(Object mapping, String key) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.section(mapping, key);
    }

    public static List<Object> asObjectList(Object value) {
        return emaki.jiuwu.craft.corelib.api.config.ConfigNodes.asObjectList(value);
    }
}
