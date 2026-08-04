package emaki.jiuwu.craft.corelib.text;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.text.Texts}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具改由 {@code emaki-corelib-api} 契约 artifact 提供，
 * 业务模块不再直连实现包。此处保留全部 18 个方法签名并逐一委托，
 * 因此旧调用点行为完全不变，可在一个完整次版本周期内平滑迁移。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.text.Texts}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class Texts {

    private Texts() {
    }

    public static String toStringSafe(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.toStringSafe(value);
    }

    public static boolean isBlank(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.isBlank(value);
    }

    public static boolean isNotBlank(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.isNotBlank(value);
    }

    public static String trim(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.trim(value);
    }

    public static String lower(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.lower(value);
    }

    public static String upper(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.upper(value);
    }

    public static boolean startsWith(Object text, Object prefix) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.startsWith(text, prefix);
    }

    public static boolean endsWith(Object text, Object suffix) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.endsWith(text, suffix);
    }

    public static boolean contains(Object text, Object substring) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.contains(text, substring);
    }

    public static String stripMiniTags(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.stripMiniTags(value);
    }

    public static String normalizeWhitespace(String value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.normalizeWhitespace(value);
    }

    public static String normalizeWhitespace(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.normalizeWhitespace(value);
    }

    public static String normalizeId(String value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.normalizeId(value);
    }

    public static List<String> stripMiniTags(Collection<?> values) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.stripMiniTags(values);
    }

    public static String formatTemplate(String template, Map<String, ?> replacements) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.formatTemplate(template, replacements);
    }

    public static List<String> expandTemplateLines(String template, Map<String, ?> replacements) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.expandTemplateLines(template, replacements);
    }

    public static List<String> formatTemplateList(Collection<?> template, Map<String, ?> replacements) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.formatTemplateList(template, replacements);
    }

    public static List<String> asStringList(Object value) {
        return emaki.jiuwu.craft.corelib.api.text.Texts.asStringList(value);
    }
}
