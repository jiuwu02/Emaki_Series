package emaki.jiuwu.craft.level.placeholder;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderResolver;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.level.service.PlayerLevelService;

public final class LevelCorePlaceholderResolver implements PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("%emakilevel_([^%\\s]+)%", Pattern.CASE_INSENSITIVE);
    private static final List<String> FIELDS = List.of(
            "progress_percent",
            "required_exp",
            "total_exp",
            "totalexp",
            "requiredexp",
            "progress",
            "level",
            "exp",
            "total",
            "required"
    );

    private final EmakiLevelPlugin plugin;

    public LevelCorePlaceholderResolver(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String resolve(ActionContext context, String text) {
        if (context == null || context.player() == null || Texts.isBlank(text)
                || !text.toLowerCase(Locale.ROOT).contains("%emakilevel_")) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = resolve(context.player(), matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolve(Player player, String payload) {
        ParsedPlaceholder parsed = parse(payload);
        if (player == null || parsed == null) {
            return "0";
        }
        LevelTypeConfig type = plugin.typeRegistry().type(parsed.typeId()).orElse(null);
        if (type == null) {
            return "0";
        }
        PlayerLevelData data = plugin.dataStore().getOrLoad(player.getUniqueId(), plugin.typeRegistry().asMap());
        PlayerLevelEntry entry = data.entry(type.id());
        if (entry == null) {
            return "0";
        }
        double required = plugin.requirementService().requiredExp(type, entry, Math.min(type.maxLevel(), entry.level() + 1));
        double progress = required <= 0D ? 1D : Math.min(1D, entry.exp() / required);
        return switch (parsed.field()) {
            case "level" -> String.valueOf(entry.level());
            case "exp" -> PlayerLevelService.format(entry.exp());
            case "total", "totalexp", "total_exp" -> PlayerLevelService.format(entry.totalExp());
            case "required", "requiredexp", "required_exp" -> PlayerLevelService.format(required);
            case "progress" -> PlayerLevelService.format(progress);
            case "progresspercent", "progress_percent" -> PlayerLevelService.format(progress * 100D);
            default -> "0";
        };
    }

    private ParsedPlaceholder parse(String payload) {
        if (Texts.isBlank(payload)) {
            return null;
        }
        String lower = payload.toLowerCase(Locale.ROOT);
        for (String field : FIELDS) {
            String prefix = field + "_";
            if (lower.startsWith(prefix)) {
                String typeId = Texts.normalizeId(lower.substring(prefix.length()));
                return Texts.isBlank(typeId) ? null : new ParsedPlaceholder(field, typeId);
            }
        }
        return null;
    }

    private record ParsedPlaceholder(String field, String typeId) {
    }
}
