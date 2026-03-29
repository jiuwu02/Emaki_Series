package emaki.jiuwu.craft.forge.model;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class Blueprint {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ItemSource source;
    private final List<String> tags;
    private final int forgeCapacity;

    public Blueprint(String id,
                     String displayName,
                     List<String> description,
                     ItemSource source,
                     List<String> tags,
                     int forgeCapacity) {
        this.id = id;
        this.displayName = displayName;
        this.description = List.copyOf(description);
        this.source = source;
        this.tags = List.copyOf(tags);
        this.forgeCapacity = forgeCapacity;
    }

    public static Blueprint fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String id = section.getString("id");
        if (Texts.isBlank(id)) {
            return null;
        }
        ItemSource source = ItemSourceUtil.parse(section);
        if (source == null) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (String tag : Texts.asStringList(section.get("tags"))) {
            if (Texts.isNotBlank(tag)) {
                tags.add(Texts.lower(tag));
            }
        }
        Integer configuredCapacity = Numbers.tryParseInt(section.get("forge_capacity"), null);
        int forgeCapacity = configuredCapacity != null
            ? configuredCapacity
            : Numbers.tryParseInt(section.get("max_capacity"), 0);
        return new Blueprint(
            id,
            section.getString("display_name", id),
            Texts.asStringList(section.get("description")),
            source,
            tags,
            forgeCapacity
        );
    }

    public boolean hasTag(String tag) {
        return Texts.isNotBlank(tag) && tags.contains(Texts.lower(tag));
    }

    public boolean matchesSelector(Map<String, Object> selector) {
        if (selector == null) {
            return false;
        }
        String kind = Texts.lower(selector.get("kind"));
        String value = Texts.toStringSafe(selector.get("value"));
        if (Texts.isBlank(kind) || Texts.isBlank(value)) {
            return false;
        }
        return switch (kind) {
            case "id" -> Texts.lower(id).equals(Texts.lower(value));
            case "tag" -> hasTag(value);
            default -> false;
        };
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

    public List<String> tags() {
        return tags;
    }

    public int forgeCapacity() {
        return forgeCapacity;
    }
}
