package emaki.jiuwu.craft.gem.legacy;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.legacy.LegacyConvertCommand;
import emaki.jiuwu.craft.corelib.legacy.LegacyMessageSink;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemLegacyEntry {

    private GemLegacyEntry() {
    }

    public static boolean handle(@NotNull EmakiGemPlugin plugin,
            @NotNull CommandSender sender,
            String[] args,
            @NotNull String adminPermission) {
        LegacyMessageSink messages = plugin.messageService();
        if (!sender.hasPermission(adminPermission)) {
            messages.send(sender, "general.no_permission");
            return true;
        }
        boolean apply = LegacyConvertCommand.applyRequested(args, 1);
        renderMatchBlocks(plugin, sender, messages, apply);
        LegacyConvertCommand.run(messages, sender, plugin.getDataFolder().toPath(),
                GemLegacyTargets.specs(), plugin.getLogger(), apply);
        return true;
    }

    private static void renderMatchBlocks(EmakiGemPlugin plugin,
            CommandSender sender,
            LegacyMessageSink messages,
            boolean apply) {
        LegacyGemConfigRewriter.RunReport report =
                new LegacyGemConfigRewriter(plugin.dataPath("items"), plugin.getLogger()).run(apply);
        messages.sendRaw(sender, messages.message("command.convert_legacy.header", Map.of(
                "mode", messages.message(apply
                        ? "command.convert_legacy.mode.apply"
                        : "command.convert_legacy.mode.dry_run"),
                "files", report.files().size()
        )));
        for (LegacyGemConfigRewriter.FileReport file : report.files()) {
            renderFile(messages, sender, file, apply);
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.summary", Map.of(
                "converted", report.count(LegacyGemConfigRewriter.Status.CONVERTED),
                "skipped", report.count(LegacyGemConfigRewriter.Status.NO_LEGACY_BLOCK),
                "conflict", report.count(LegacyGemConfigRewriter.Status.CONFLICT),
                "unconvertible", report.count(LegacyGemConfigRewriter.Status.UNCONVERTIBLE),
                "failed", report.count(LegacyGemConfigRewriter.Status.FAILED)
        )));
    }

    private static void renderFile(LegacyMessageSink messages,
            CommandSender sender,
            LegacyGemConfigRewriter.FileReport file,
            boolean apply) {
        if (file.status() == LegacyGemConfigRewriter.Status.NO_LEGACY_BLOCK) {
            return;
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.file", Map.of(
                "file", file.fileName(),
                "status", messages.message("command.convert_legacy.status."
                        + Texts.lower(file.status().name())),
                "detail", file.detail()
        )));
        if (apply && Texts.isNotBlank(file.backupName())) {
            messages.sendRaw(sender, messages.message("command.convert_legacy.backup",
                    Map.of("backup", file.backupName())));
        }
        if (!apply) {
            for (String line : file.diff()) {
                messages.sendRaw(sender, messages.message("command.convert_legacy.diff_line",
                        Map.of("line", line)));
            }
        }
    }

}
