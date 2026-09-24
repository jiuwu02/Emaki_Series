package emaki.jiuwu.craft.corelib.action.select;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetIdentity;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class TargetPlaceholders {

    private static final Pattern TARGET_PLACEHOLDER = Pattern.compile("%target_([A-Za-z0-9_.:-]+)%");
    private static final String PERMISSION_PREFIX = "permission_";
    private static final String TAG_PREFIX = "tag_";
    private static final String STATE_PREFIX = "state_";
    private static final String EFFECT_PREFIX = "effect_";
    private static final String LEVEL_SUFFIX = "_level";
    private static final String ID_SUFFIX = "_id";

    private TargetPlaceholders() {
    }

    public static String expand(String template, TargetFacts facts) {
        if (Texts.isBlank(template)) {
            return Texts.toStringSafe(template);
        }
        TargetFacts safeFacts = facts == null ? TargetFacts.absent() : facts;
        Matcher matcher = TARGET_PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String value = resolve(token, safeFacts);
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String resolve(String token, TargetFacts facts) {
        String key = Texts.lower(token);
        return switch (key) {
            case "type" -> facts.entityType();
            case "name" -> facts.plainName();
            case "world" -> facts.worldName();
            case "health" -> plain(facts.health());
            case "max_health" -> plain(facts.maxHealth());
            case "health_percent" -> plain(facts.healthPercent());
            case "distance" -> facts.distance() < 0D ? null : plain(facts.distance());
            case "level" -> facts.player() ? String.valueOf(facts.experienceLevel()) : null;
            case "food" -> facts.player() ? String.valueOf(facts.foodLevel()) : null;
            case "gamemode" -> facts.player() ? facts.gameMode() : null;
            default -> resolveExtended(key, facts);
        };
    }

    private static String resolveExtended(String key, TargetFacts facts) {
        if (key.startsWith(PERMISSION_PREFIX)) {
            Boolean allowed = facts.permissions().apply(key.substring(PERMISSION_PREFIX.length()));
            return allowed == null ? null : allowed.toString();
        }
        if (key.startsWith(TAG_PREFIX)) {
            return Boolean.toString(facts.scoreboardTags().contains(key.substring(TAG_PREFIX.length())));
        }
        if (key.startsWith(STATE_PREFIX)) {
            Boolean state = facts.state(key.substring(STATE_PREFIX.length()));
            return state == null ? null : state.toString();
        }
        if (key.startsWith(EFFECT_PREFIX)) {
            Integer amplifier = facts.potionAmplifier(
                    TargetEffectKeys.normalize(key.substring(EFFECT_PREFIX.length())));
            return amplifier == null ? "" : String.valueOf(amplifier);
        }
        for (Map.Entry<String, CoreTargetIdentity> entry : facts.identities().entrySet()) {
            String system = Texts.lower(entry.getKey());
            CoreTargetIdentity identity = entry.getValue();
            if (identity == null) {
                continue;
            }
            if (key.equals(system + ID_SUFFIX)) {
                return identity.id();
            }
            if (key.equals(system + LEVEL_SUFFIX)) {
                return plain(identity.level());
            }
        }
        return null;
    }

    private static String plain(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        double rounded = Math.round(value * 1000D) / 1000D;
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1.0E15D) {
            return String.valueOf((long) rounded);
        }
        return Numbers.toPlainString(rounded);
    }
}