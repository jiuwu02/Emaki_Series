package emaki.jiuwu.craft.strengthen.model;

import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class StrengthenRule {

    private final String id;
    private final String profileId;
    private final List<String> sourceTypes;
    private final String sourcePattern;
    private final String slotGroup;
    private final List<String> loreContains;
    private final List<String> statsAny;

    public StrengthenRule(String id,
            String profileId,
            List<String> sourceTypes,
            String sourcePattern,
            String slotGroup,
            List<String> loreContains,
            List<String> statsAny) {
        this.id = Texts.trim(id);
        this.profileId = Texts.trim(profileId);
        this.sourceTypes = sourceTypes == null ? List.of() : sourceTypes.stream().map(Texts::lower).toList();
        this.sourcePattern = Texts.toStringSafe(sourcePattern);
        this.slotGroup = Texts.toStringSafe(slotGroup);
        this.loreContains = loreContains == null ? List.of() : loreContains.stream().map(Texts::stripMiniTags).toList();
        this.statsAny = statsAny == null ? List.of() : statsAny.stream().map(Texts::lower).toList();
    }

    public static StrengthenRule fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        String profileId = section.getString("profile");
        if (Texts.isBlank(id) || Texts.isBlank(profileId)) {
            return null;
        }
        return new StrengthenRule(
                id,
                profileId,
                List.copyOf(section.getStringList("source_types")),
                section.getString("source_pattern", ""),
                section.getString("slot_group", ""),
                List.copyOf(section.getStringList("lore_contains")),
                List.copyOf(section.getStringList("stats_any"))
        );
    }

    public String id() {
        return id;
    }

    public String profileId() {
        return profileId;
    }

    public List<String> sourceTypes() {
        return sourceTypes;
    }

    public String sourcePattern() {
        return sourcePattern;
    }

    public String slotGroup() {
        return slotGroup;
    }

    public List<String> loreContains() {
        return loreContains;
    }

    public List<String> statsAny() {
        return statsAny;
    }
}
