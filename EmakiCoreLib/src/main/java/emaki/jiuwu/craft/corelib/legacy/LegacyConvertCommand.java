package emaki.jiuwu.craft.corelib.legacy;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.FileReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.RunReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.Status;

public final class LegacyConvertCommand {

    private LegacyConvertCommand() {
    }

    public static boolean applyRequested(String[] args, int index) {
        if (args == null || args.length <= index) {
            return false;
        }
        String token = args[index];
        return "confirm".equalsIgnoreCase(token) || "--apply".equalsIgnoreCase(token);
    }

    public static void run(@NotNull LegacyMessageSink messages,
            @NotNull CommandSender sender,
            @NotNull Path root,
            @NotNull List<LegacyTargetSpec> specs,
            @NotNull Logger logger,
            boolean apply) {
        render(messages, sender, new LegacyItemSourceRewriter(root, specs, logger).run(apply), apply);
    }

    public static void render(@NotNull LegacyMessageSink messages,
            @NotNull CommandSender sender,
            @NotNull RunReport report,
            boolean apply) {
        messages.sendRaw(sender, messages.message("command.convert_legacy.header", Map.of(
                "mode", messages.message(apply
                        ? "command.convert_legacy.mode.apply"
                        : "command.convert_legacy.mode.dry_run"),
                "files", report.files().size()
        )));
        for (FileReport file : report.files()) {
            renderFile(messages, sender, file, apply);
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.summary", Map.of(
                "converted", report.count(Status.CONVERTED),
                "skipped", report.count(Status.NO_LEGACY_BLOCK),
                "conflict", report.count(Status.CONFLICT),
                "unconvertible", report.count(Status.UNCONVERTIBLE),
                "failed", report.count(Status.FAILED)
        )));
        if (report.hasConvertible()) {
            messages.sendRaw(sender, messages.message("command.convert_legacy.breakdown", Map.of(
                    "plain", report.plain(),
                    "merged", report.merged(),
                    "duplicated", report.duplicated()
            )));
        }
        if (!apply) {
            messages.send(sender, report.hasConvertible()
                    ? "command.convert_legacy.dry_run_hint"
                    : "command.convert_legacy.nothing_to_do");
        }
    }

    private static void renderFile(LegacyMessageSink messages,
            CommandSender sender,
            FileReport file,
            boolean apply) {
        if (file.status() == Status.NO_LEGACY_BLOCK) {
            return;
        }
        messages.sendRaw(sender, messages.message("command.convert_legacy.file", Map.of(
                "file", file.fileName(),
                "status", messages.message("command.convert_legacy.status."
                        + file.status().name().toLowerCase(Locale.ROOT)),
                "detail", file.detail()
        )));
        if (file.merged() > 0 || file.duplicated() > 0) {
            messages.sendRaw(sender, messages.message("command.convert_legacy.file_merged", Map.of(
                    "merged", file.merged(),
                    "duplicated", file.duplicated(),
                    "plain", file.plain()
            )));
        }
        if (apply && !file.backupName().isBlank()) {
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
