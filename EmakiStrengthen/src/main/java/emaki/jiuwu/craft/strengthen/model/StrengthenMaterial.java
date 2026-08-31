package emaki.jiuwu.craft.strengthen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public final class StrengthenMaterial {

    private static final Logger LOGGER = Logger.getLogger(StrengthenMaterial.class.getName());

    public enum Role {
        BASE,
        SUPPORT,
        PROTECTION,
        BREAKTHROUGH,
        CLEANSE;

        public static Role fromText(String text) {
            if (Texts.isBlank(text)) {
                return null;
            }
            try {
                return Role.valueOf(Texts.trim(text).toUpperCase(Locale.ROOT));
            } catch (Exception _) {
                return null;
            }
        }
    }

    private final String recipeId;
    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ItemSourceRef source;
    private final Role role;
    private final int consumeAmount;
    private final int minTargetStar;
    private final int maxTargetStar;
    private final double successBonus;
    private final double successCap;
    private final int protectionMinTargetStar;
    private final int requiredFromTargetStar;
    private final int crackRemove;
    private final Matcher matcher;

    public StrengthenMaterial(String recipeId,
            String id,
            String displayName,
            List<String> description,
            ItemSourceRef source,
            Role role,
            int consumeAmount,
            int minTargetStar,
            int maxTargetStar,
            double successBonus,
            double successCap,
            int protectionMinTargetStar,
            int requiredFromTargetStar,
            int crackRemove) {
        this(recipeId, id, displayName, description, source, role, consumeAmount, minTargetStar, maxTargetStar,
                successBonus, successCap, protectionMinTargetStar, requiredFromTargetStar, crackRemove, null);
    }

    public StrengthenMaterial(String recipeId,
            String id,
            String displayName,
            List<String> description,
            ItemSourceRef source,
            Role role,
            int consumeAmount,
            int minTargetStar,
            int maxTargetStar,
            double successBonus,
            double successCap,
            int protectionMinTargetStar,
            int requiredFromTargetStar,
            int crackRemove,
            @Nullable Matcher matcher) {
        this.recipeId = Texts.trim(recipeId);
        this.id = Texts.trim(id);
        this.displayName = displayName;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.source = source;
        this.role = role;
        this.consumeAmount = Math.max(1, consumeAmount);
        this.minTargetStar = Math.max(1, minTargetStar);
        this.maxTargetStar = Math.max(this.minTargetStar, maxTargetStar);
        this.successBonus = successBonus;
        this.successCap = successCap;
        this.protectionMinTargetStar = protectionMinTargetStar;
        this.requiredFromTargetStar = requiredFromTargetStar;
        this.crackRemove = crackRemove;
        this.matcher = matcher;
    }

    public static StrengthenMaterial fromMap(String recipeId, Role role, Map<?, ?> raw, int index) {
        if (raw == null || role == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                values.put(Texts.lower(String.valueOf(entry.getKey())), entry.getValue());
            }
        }
        String id = Texts.toStringSafe(values.get("id"));
        if (Texts.isBlank(id)) {
            id = Texts.lower(recipeId) + "_" + Texts.lower(role.name()) + "_" + Math.max(1, index + 1);
        }
        Object rawMatcher = values.get("matcher");
        Matcher matcher = rawMatcher == null ? null : Matcher.fromConfig(rawMatcher);
        ItemSourceRef source = ItemSourceUtil.parse(values.get("item"));
        if (source == null && matcher == null) {
            return null;
        }
        return new StrengthenMaterial(
                recipeId,
                id,
                Texts.toStringSafe(values.getOrDefault("display_name", id)),
                parseStringList(values.get("description")),
                source,
                role,
                Numbers.tryParseInt(values.get("consume_amount"), 1),
                Numbers.tryParseInt(values.get("min_target_star"), 1),
                Numbers.tryParseInt(values.get("max_target_star"), Integer.MAX_VALUE),
                Numbers.tryParseDouble(values.get("success_bonus"), 0D),
                Numbers.tryParseDouble(values.get("success_cap"), 0D),
                Numbers.tryParseInt(values.get("protection_min_target_star"), 0),
                Numbers.tryParseInt(values.get("required_from_target_star"), 0),
                Numbers.tryParseInt(values.get("crack_remove"), 0),
                matcher
        );
    }

    private static List<String> parseStringList(Object raw) {
        if (raw instanceof Iterable<?> iterable) {
            ArrayList<String> values = new ArrayList<>();
            for (Object entry : iterable) {
                values.add(Texts.toStringSafe(entry));
            }
            return List.copyOf(values);
        }
        if (raw == null) {
            return List.of();
        }
        return List.of(Texts.toStringSafe(raw));
    }

    public boolean availableForTargetStar(int targetStar) {
        return targetStar >= minTargetStar && targetStar <= maxTargetStar;
    }

    public boolean matches(ItemSourceRef itemSource, int targetStar) {
        return availableForTargetStar(targetStar) && ItemSourceUtil.matches(source, itemSource);
    }

    public boolean matches(@Nullable MatchContext context, int targetStar) {
        if (!availableForTargetStar(targetStar)) {
            return false;
        }
        if (matcher == null) {
            return ItemSourceUtil.matches(source, context == null ? null : context.itemSource());
        }
        if (context == null) {
            return false;
        }
        try {
            return matcher.test(context);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warning("材料 Matcher 判定抛出异常，视为不匹配: " + String.valueOf(exception.getMessage()));
            return false;
        }
    }

    public String recipeId() {
        return recipeId;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> description() {
        return description;
    }

    public ItemSourceRef source() {
        return source;
    }

    public Role role() {
        return role;
    }

    public int consumeAmount() {
        return consumeAmount;
    }

    public int minTargetStar() {
        return minTargetStar;
    }

    public int maxTargetStar() {
        return maxTargetStar;
    }

    public double successBonus() {
        return successBonus;
    }

    public double successCap() {
        return successCap;
    }

    public int protectionMinTargetStar() {
        return protectionMinTargetStar;
    }

    public int requiredFromTargetStar() {
        return requiredFromTargetStar;
    }

    public int crackRemove() {
        return crackRemove;
    }

    public @Nullable Matcher matcher() {
        return matcher;
    }
}
