package emaki.jiuwu.craft.strengthen.model;

import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class StrengthenMaterial {

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
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ItemSource source;
    private final Role role;
    private final double successBonus;
    private final double successCap;
    private final int protectionMinTargetStar;
    private final int requiredFromTargetStar;
    private final int crackRemove;

    public StrengthenMaterial(String id,
            String displayName,
            List<String> description,
            ItemSource source,
            Role role,
            double successBonus,
            double successCap,
            int protectionMinTargetStar,
            int requiredFromTargetStar,
            int crackRemove) {
        this.id = Texts.trim(id);
        this.displayName = displayName;
        this.description = description == null ? List.of() : List.copyOf(description);
        this.source = source;
        this.role = role;
        this.successBonus = successBonus;
        this.successCap = successCap;
        this.protectionMinTargetStar = protectionMinTargetStar;
        this.requiredFromTargetStar = requiredFromTargetStar;
        this.crackRemove = crackRemove;
    }

    public static StrengthenMaterial fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        ItemSource source = ItemSourceUtil.parse(section.get("item"));
        Role role = Role.fromText(section.getString("role"));
        if (Texts.isBlank(id) || source == null || role == null) {
            return null;
        }
        return new StrengthenMaterial(
                id,
                section.getString("display_name", id),
                List.copyOf(section.getStringList("description")),
                source,
                role,
                Numbers.tryParseDouble(section.get("success_bonus"), 0D),
                Numbers.tryParseDouble(section.get("success_cap"), 0D),
                Numbers.tryParseInt(section.get("protection_min_target_star"), 0),
                Numbers.tryParseInt(section.get("required_from_target_star"), 0),
                Numbers.tryParseInt(section.get("crack_remove"), 0)
        );
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

    public ItemSource source() {
        return source;
    }

    public Role role() {
        return role;
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
}
