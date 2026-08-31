package emaki.jiuwu.craft.corelib.bootstrap;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class ConfigKeyMigration {

    public record Rename(String legacyPath, String currentPath) {

        public Rename {
            legacyPath = Texts.toStringSafe(legacyPath);
            currentPath = Texts.toStringSafe(currentPath);
        }
    }

    private ConfigKeyMigration() {
    }

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
