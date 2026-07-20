package emaki.jiuwu.craft.item.loader.migration.configureditem;

import java.io.File;
import java.util.Arrays;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemMigration;
import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemMigration.FileIssue;
import emaki.jiuwu.craft.corelib.item.migration.configureditem.ConfiguredItemMigration.MigrationReport;


public final class ConfiguredItemMigrationBridge {

    private ConfiguredItemMigrationBridge() {
    }

    public static void migrate(JavaPlugin plugin, File[] files) {
        MigrationReport report = ConfiguredItemMigration.migrateLegacyItemFiles(
                plugin.getDataFolder().toPath(),
                files == null ? java.util.List.of() : Arrays.asList(files)
        );
        for (FileIssue issue : report.skipped()) {
            plugin.getLogger().warning("Skipped legacy EmakiItem definition migration for "
                    + issue.file().getPath() + ": " + issue.message()
                    + "; the legacy reader remains active.");
        }
        for (FileIssue issue : report.failures()) {
            plugin.getLogger().warning("Legacy EmakiItem definition migration failed for "
                    + issue.file().getPath() + ": " + issue.message()
                    + "; the original file will be loaded through legacy compatibility.");
        }
        if (report.changedFiles() > 0) {
            plugin.getLogger().info("Migrated " + report.changedFiles() + " EmakiItem definition file(s). Backups: "
                    + report.backupRoot());
        }
    }
}
