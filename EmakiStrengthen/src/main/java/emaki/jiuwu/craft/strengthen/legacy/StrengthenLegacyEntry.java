package emaki.jiuwu.craft.strengthen.legacy;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.legacy.LegacyConvertCommand;
import emaki.jiuwu.craft.corelib.legacy.LegacyMessageSink;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenLegacyEntry {

    private StrengthenLegacyEntry() {
    }

    public static boolean handle(@NotNull EmakiStrengthenPlugin plugin,
            @NotNull CommandSender sender,
            String[] args,
            @NotNull String adminPermission) {
        LegacyMessageSink messages = plugin.messageService();
        if (!sender.hasPermission(adminPermission)) {
            messages.send(sender, "general.no_permission");
            return true;
        }
        boolean apply = LegacyConvertCommand.applyRequested(args, 1);
        LegacyStrengthenConfigRewriter.RunReport report =
                new LegacyStrengthenConfigRewriter(plugin.dataPath("recipes"), plugin.getLogger()).run(apply);
        messages.sendRaw(sender, messages.message("command.convert_legacy.header", Map.of(
                "mode", messages.message(apply
                        ? "command.convert_legacy.mode.apply"
                        : "command.convert_legacy.mode.dry_run"),
                "files", report.files().size()
        )));
        for (LegacyStrengthenConfigRewriter.FileReport file : report.files()) {
            renderFile(messages, sender, file, apply);
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.summary", Map.of(
                "converted", report.count(LegacyStrengthenConfigRewriter.Status.CONVERTED),
                "skipped", report.count(LegacyStrengthenConfigRewriter.Status.NO_LEGACY_BLOCK),
                "conflict", report.count(LegacyStrengthenConfigRewriter.Status.CONFLICT),
                "unconvertible", report.count(LegacyStrengthenConfigRewriter.Status.UNCONVERTIBLE),
                "failed", report.count(LegacyStrengthenConfigRewriter.Status.FAILED)
        )));
        if (!apply) {
            messages.send(sender, report.hasConvertible()
                    ? "command.convert_legacy.dry_run_hint"
                    : "command.convert_legacy.nothing_to_do");
        }
        return true;
    }

    private static void renderFile(LegacyMessageSink messages,
            CommandSender sender,
            LegacyStrengthenConfigRewriter.FileReport file,
            boolean apply) {
        if (file.status() == LegacyStrengthenConfigRewriter.Status.NO_LEGACY_BLOCK) {
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
