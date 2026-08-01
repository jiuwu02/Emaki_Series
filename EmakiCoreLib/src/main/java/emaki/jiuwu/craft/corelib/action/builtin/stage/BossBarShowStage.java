package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Shows or replaces one of the target's boss bars, addressed by id.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: a boss bar is attached to one player's connection.</p>
 */
public final class BossBarShowStage extends BaseStage {

    public BossBarShowStage() {
        super("boss_bar_show", "feedback", "Shows or replaces a per-player boss bar by id.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("id", CoreStageParameterType.STRING, "Boss bar id"),
                CoreStageParameter.required("title", CoreStageParameterType.STRING, "Boss bar title"),
                CoreStageParameter.optional("progress", CoreStageParameterType.DOUBLE, "1",
                        "Progress from 0 to 1"),
                CoreStageParameter.optional("color", CoreStageParameterType.STRING, "purple", "Bar color"),
                CoreStageParameter.optional("style", CoreStageParameterType.STRING, "solid", "Bar style"),
                CoreStageParameter.optional("flags", CoreStageParameterType.STRING, "",
                        "Comma-separated bar flags"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String id = arguments.getString("id");
        if (Texts.isBlank(id)) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.boss_bar.id_required");
        }
        BarColor color = parseColor(arguments.getString("color", "purple"));
        if (color == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.boss_bar.unknown_color",
                    Map.of("color", arguments.getString("color")));
        }
        BarStyle style = parseStyle(arguments.getString("style", "solid"));
        if (style == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.boss_bar.unknown_style",
                    Map.of("style", arguments.getString("style")));
        }
        ParsedFlags flags = parseFlags(arguments.getString("flags"));
        if (flags.invalidFlag() != null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.boss_bar.unknown_flag", Map.of("flag", flags.invalidFlag()));
        }
        double progress = Math.max(0D, Math.min(1D, arguments.getDouble("progress", 1D)));
        BossBar bossBar = BossBarStore.show(target, id,
                MiniMessages.legacyText(arguments.getString("title")),
                color, style, progress, flags.flags().toArray(new BarFlag[0]));
        if (bossBar == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.boss_bar.show_failed", Map.of("id", id));
        }
        return CoreActionOutcome.success(Map.of(
                "id", Texts.normalizeId(id),
                "progress", bossBar.getProgress(),
                "color", color.name().toLowerCase(Locale.ROOT),
                "style", style.name().toLowerCase(Locale.ROOT)));
    }

    private static BarColor parseColor(String raw) {
        if (Texts.isBlank(raw)) {
            return BarColor.PURPLE;
        }
        try {
            return BarColor.valueOf(Texts.trim(raw).replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static BarStyle parseStyle(String raw) {
        if (Texts.isBlank(raw)) {
            return BarStyle.SOLID;
        }
        try {
            return BarStyle.valueOf(Texts.trim(raw).replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ParsedFlags parseFlags(String raw) {
        if (Texts.isBlank(raw)) {
            return new ParsedFlags(List.of(), null);
        }
        List<BarFlag> flags = new ArrayList<>();
        for (String part : Texts.toStringSafe(raw).split("[,|;]")) {
            if (Texts.isBlank(part)) {
                continue;
            }
            try {
                flags.add(BarFlag.valueOf(Texts.trim(part).replace('-', '_').toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return new ParsedFlags(List.of(), part);
            }
        }
        return new ParsedFlags(List.copyOf(flags), null);
    }

    private record ParsedFlags(List<BarFlag> flags, String invalidFlag) {
    }
}
