package emaki.jiuwu.craft.corelib.bootstrap;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * 配置键改名的一次性迁移。
 *
 * <p>用在 {@link BootstrapHooks#afterVersionedMerge}：该回调发生在 updater 合并之后、
 * 文件落盘之前。updater 以 {@code keepAll} 保留服主旧键，同时把新键按 bundled 默认值注入，
 * 因此单靠"新键缺失时读旧键"的读取回退无法生效——新键在落盘后必然存在，旧键会被永久遮蔽。
 * 这里把旧键的值搬到新键并删除旧键，使服主自定义值在改名后继续生效。
 *
 * <p>仅当新键当前值仍等于 bundled 默认值时才搬值，避免覆盖服主已显式写好的新键。
 */
public final class ConfigKeyMigration {

    /**
     * 一条改名映射。
     *
     * @param legacyPath 旧键路径，支持 {@code a.b} 形式的跨层级路径
     * @param currentPath 新键路径
     */
    public record Rename(String legacyPath, String currentPath) {

        public Rename {
            legacyPath = Texts.toStringSafe(legacyPath);
            currentPath = Texts.toStringSafe(currentPath);
        }
    }

    private ConfigKeyMigration() {
    }

    /**
     * 执行改名迁移，返回实际搬动的条数。
     *
     * @param runtime  服主文件（updater 合并后、落盘前）
     * @param bundled  jar 内默认值，用于判断新键是否仍是默认值
     * @param renames  改名映射
     * @param logger   用于告知服主已迁移，可为 {@code null}
     */
    public static int applyRenames(YamlSection runtime,
            YamlSection bundled,
            List<Rename> renames,
            Logger logger) {
        if (runtime == null || renames == null || renames.isEmpty()) {
            return 0;
        }
        int migrated = 0;
        for (Rename rename : renames) {
            if (rename == null
                    || Texts.isBlank(rename.legacyPath())
                    || Texts.isBlank(rename.currentPath())) {
                continue;
            }
            if (applyRename(runtime, bundled, rename, logger)) {
                migrated++;
            }
        }
        return migrated;
    }

    private static boolean applyRename(YamlSection runtime,
            YamlSection bundled,
            Rename rename,
            Logger logger) {
        String legacyPath = rename.legacyPath();
        String currentPath = rename.currentPath();
        if (!runtime.contains(legacyPath)) {
            return false;
        }
        Object legacyValue = runtime.get(legacyPath);
        if (legacyValue == null) {
            runtime.set(legacyPath, null);
            return false;
        }
        if (!isStillDefault(runtime, bundled, currentPath)) {
            // 服主已显式设置新键，保留新键，仅清理旧键。
            runtime.set(legacyPath, null);
            warn(logger, "配置键 " + legacyPath + " 已改名为 " + currentPath
                    + "，检测到你已设置新键，旧键值 " + legacyValue + " 被忽略并移除。");
            return false;
        }
        runtime.set(currentPath, legacyValue);
        runtime.set(legacyPath, null);
        warn(logger, "配置键 " + legacyPath + " 已改名为 " + currentPath
                + "，你的原值 " + legacyValue + " 已自动迁移。");
        return true;
    }

    private static boolean isStillDefault(YamlSection runtime, YamlSection bundled, String currentPath) {
        if (bundled == null || !bundled.contains(currentPath)) {
            return true;
        }
        if (!runtime.contains(currentPath)) {
            return true;
        }
        return Objects.equals(runtime.get(currentPath), bundled.get(currentPath));
    }

    /**
     * 删除只剩空壳的旧配置节，例如键上移后残留的 {@code permission: {}}。
     */
    public static void pruneEmptySection(YamlSection runtime, String path) {
        if (runtime == null || Texts.isBlank(path) || !runtime.contains(path)) {
            return;
        }
        YamlSection section = runtime.getSection(path);
        if (section != null && section.getKeys(false).isEmpty()) {
            runtime.set(path, null);
        }
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
