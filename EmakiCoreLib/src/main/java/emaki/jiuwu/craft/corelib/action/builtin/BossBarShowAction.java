package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class BossBarShowAction extends BaseAction {

    public BossBarShowAction() {
        super(
                "bossbarshow",
                "feedback",
                "Show or replace a per-player boss bar by id.",
                ActionParameter.required("id", ActionParameterType.STRING, "Boss bar id"),
                ActionParameter.required("title", ActionParameterType.STRING, "Boss bar title"),
                ActionParameter.optional("progress", ActionParameterType.DOUBLE, "1", "Progress from 0 to 1"),
                ActionParameter.optional("color", ActionParameterType.STRING, "purple", "Bar color"),
                ActionParameter.optional("style", ActionParameterType.STRING, "solid", "Bar style"),
                ActionParameter.optional("flags", ActionParameterType.STRING, "", "Comma-separated bar flags")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        String id = stringArg(arguments, "id");
        if (Texts.isBlank(id)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "bossbarshow requires id.");
        }
        BarColor color = parseColor(arguments.get("color"));
        if (color == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown bossbar color: " + arguments.get("color"));
        }
        BarStyle style = parseStyle(arguments.get("style"));
        if (style == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown bossbar style: " + arguments.get("style"));
        }
        ParsedFlags parsedFlags = parseFlags(arguments.get("flags"));
        if (parsedFlags.invalidFlag() != null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown bossbar flag: " + parsedFlags.invalidFlag());
        }
        double progress = Math.max(0D, Math.min(1D, ActionParsers.parseDouble(arguments.get("progress"), 1D)));
        BossBar bossBar = BuiltinBossBarRegistry.show(
                context.player(),
                id,
                MiniMessages.legacyText(stringArg(arguments, "title")),
                color,
                style,
                progress,
                parsedFlags.flags().toArray(new BarFlag[0])
        );
        if (bossBar == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unable to show bossbar for id: " + id);
        }
        return ActionResult.ok(Map.of(
                "id", Texts.normalizeId(id),
                "progress", bossBar.getProgress(),
                "color", color.name().toLowerCase(Locale.ROOT),
                "style", style.name().toLowerCase(Locale.ROOT)
        ));
    }

    private BarColor parseColor(String raw) {
        if (Texts.isBlank(raw)) {
            return BarColor.PURPLE;
        }
        try {
            return BarColor.valueOf(Texts.trim(raw).replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private BarStyle parseStyle(String raw) {
        if (Texts.isBlank(raw)) {
            return BarStyle.SOLID;
        }
        try {
            return BarStyle.valueOf(Texts.trim(raw).replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private ParsedFlags parseFlags(String raw) {
        if (Texts.isBlank(raw)) {
            return new ParsedFlags(List.of(), null);
        }
        List<BarFlag> flags = new ArrayList<>();
        for (String part : Texts.toStringSafe(raw).split("[,|;]")) {
            if (Texts.isBlank(part)) {
                continue;
            }
            String normalized = Texts.trim(part).replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                flags.add(BarFlag.valueOf(normalized));
            } catch (IllegalArgumentException _) {
                return new ParsedFlags(List.of(), part);
            }
        }
        return new ParsedFlags(List.copyOf(flags), null);
    }

    private record ParsedFlags(List<BarFlag> flags, String invalidFlag) {
    }
}
