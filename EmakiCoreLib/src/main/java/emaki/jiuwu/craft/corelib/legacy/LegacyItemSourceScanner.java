package emaki.jiuwu.craft.corelib.legacy;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.FileReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.RunReport;
import emaki.jiuwu.craft.corelib.legacy.LegacyItemSourceRewriter.Status;

public final class LegacyItemSourceScanner {

    private LegacyItemSourceScanner() {
    }

    public static @NotNull RunReport scan(@NotNull Path root,
            @NotNull List<LegacyTargetSpec> specs,
            @NotNull Logger logger) {
        return new LegacyItemSourceRewriter(root, specs, logger).run(false);
    }

    public static void report(@NotNull Path root,
            @NotNull List<LegacyTargetSpec> specs,
            @NotNull Logger logger,
            @NotNull String command) {
        RunReport report = scan(root, specs, logger);
        List<FileReport> convertible = report.convertible();
        long unconvertible = report.count(Status.UNCONVERTIBLE);
        if (convertible.isEmpty() && unconvertible == 0L) {
            return;
        }
        if (!convertible.isEmpty()) {
            logger.warning("检测到 " + convertible.size() + " 份配置仍在用旧 item_sources 识别格式，共 "
                    + report.occurrences() + " 处");
            for (FileReport file : convertible) {
                logger.warning("  " + file.fileName() + " (" + file.occurrences() + " 处)");
            }
            logger.warning("执行 /" + command + " convert-legacy 预览，/" + command
                    + " convert-legacy confirm 应用");
        }
        for (FileReport file : report.files()) {
            if (file.status() == Status.UNCONVERTIBLE && Texts.isNotBlank(file.detail())) {
                logger.warning("  " + file.fileName() + " 无法自动转换: " + file.detail());
            }
        }
    }
}
