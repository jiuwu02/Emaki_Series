package emaki.jiuwu.craft.gem.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record SocketOpenerConfig(String id,
        boolean enabled,
        ItemSource itemSource,
        Integer customModelData,
        String displayName,
        List<String> lore,
        Set<String> opensGemTypes,
        boolean consumeOnSuccess,
        List<String> successActions,
        List<String> failureActions) {

    public SocketOpenerConfig {
        id = Texts.lower(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
        lore = lore == null ? List.of() : List.copyOf(lore);
        opensGemTypes = opensGemTypes == null ? Set.of() : Set.copyOf(opensGemTypes);
        successActions = successActions == null ? List.of() : List.copyOf(successActions);
        failureActions = failureActions == null ? List.of() : List.copyOf(failureActions);
    }

    public boolean supportsType(String gemType) {
        if (opensGemTypes.isEmpty()) {
            return true;
        }
        return opensGemTypes.contains(Texts.lower(gemType)) || opensGemTypes.contains("any");
    }

    public static SocketOpenerConfig fromConfig(String id, YamlSection section) {
        if (section == null) {
            return null;
        }
        String normalizedId = Texts.lower(id);
        if (Texts.isBlank(normalizedId)) {
            return null;
        }
        Set<String> gemTypes = new LinkedHashSet<>();
        for (String value : section.getStringList("opens_gem_types")) {
            if (Texts.isNotBlank(value)) {
                gemTypes.add(Texts.lower(value));
            }
        }
        return new SocketOpenerConfig(
                normalizedId,
                section.getBoolean("enabled", true),
                ItemSourceUtil.parse(section.get("item_source")),
                Numbers.tryParseInt(section.get("custom_model_data"), null),
                section.getString("display_name", normalizedId),
                section.getStringList("lore"),
                gemTypes,
                section.getBoolean("consume_on_success", true),
                section.getStringList("success_actions"),
                section.getStringList("failure_actions")
        );
    }
}
