package emaki.jiuwu.craft.item.service;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemPdcWriter {

    private static final String ATTRIBUTE_SOURCE_ID = "emakiitem";

    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGateway attributeGateway;
    private final SkillPdcGateway skillPdcGateway;

    public EmakiItemPdcWriter(EmakiItemIdentifier identifier,
            PdcAttributeGateway attributeGateway,
            SkillPdcGateway skillPdcGateway) {
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.skillPdcGateway = skillPdcGateway;
    }

    public void write(ItemStack itemStack, EmakiItemDefinition definition) {
        if (itemStack == null || definition == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            identifier.writeIdentity(itemMeta, definition.id());
            itemStack.setItemMeta(itemMeta);
        }
        if (!definition.attributes().isEmpty()
                && Bukkit.getPluginManager().isPluginEnabled("EmakiAttribute")) {
            attributeGateway.write(itemStack, ATTRIBUTE_SOURCE_ID, definition.attributes(), attributeMeta(definition));
        }
        if (!definition.skills().isEmpty()
                && Bukkit.getPluginManager().isPluginEnabled("EmakiSkills")) {
            skillPdcGateway.write(itemStack, definition.skills());
        }
    }

    public void shutdown() {
        attributeGateway.shutdown();
    }

    private Map<String, String> attributeMeta(EmakiItemDefinition definition) {
        Map<String, String> meta = new LinkedHashMap<>(definition.attributeMeta());
        if (definition.conditions().configured()) {
            meta.putIfAbsent("condition_expressions", conditionExpression(definition));
            meta.putIfAbsent("condition_type", definition.conditions().type());
            meta.putIfAbsent("required_count", Integer.toString(definition.conditions().requiredCount()));
            meta.putIfAbsent("invalid_as_failure", Boolean.toString(definition.conditions().invalidAsFailure()));
        }
        meta.entrySet().removeIf(entry -> Texts.isBlank(entry.getKey()) || entry.getValue() == null);
        return meta.isEmpty() ? Map.of() : Map.copyOf(meta);
    }

    private String conditionExpression(EmakiItemDefinition definition) {
        List<String> entries = definition.conditions().entries().stream()
                .filter(Texts::isNotBlank)
                .map(entry -> "(" + entry + ")")
                .toList();
        if (entries.isEmpty()) {
            return "";
        }
        return switch (Texts.normalizeId(definition.conditions().type())) {
            case "any_of" -> String.join(" || ", entries);
            case "at_least" -> thresholdExpression(entries, Math.max(1, definition.conditions().requiredCount()), false);
            case "exactly" -> thresholdExpression(entries, Math.max(0, definition.conditions().requiredCount()), true);
            default -> String.join(" && ", entries);
        };
    }

    private String thresholdExpression(List<String> entries, int requiredCount, boolean exact) {
        if (entries.isEmpty()) {
            return "";
        }
        if (!exact && requiredCount <= 1) {
            return String.join(" || ", entries);
        }
        if (requiredCount > entries.size()) {
            return "false";
        }
        if (requiredCount == entries.size()) {
            return String.join(" && ", exactMatches(entries, allIndexes(entries.size())));
        }
        List<String> combinations = new java.util.ArrayList<>();
        collectCombinations(entries, requiredCount, exact, 0, new java.util.ArrayList<>(), combinations);
        return combinations.isEmpty() ? "false" : String.join(" || ", combinations);
    }

    private void collectCombinations(List<String> entries,
            int requiredCount,
            boolean exact,
            int start,
            List<Integer> selected,
            List<String> output) {
        if (selected.size() == requiredCount) {
            output.add(String.join(" && ", exact ? exactMatches(entries, selected) : selected.stream().map(entries::get).toList()));
            return;
        }
        for (int index = start; index < entries.size(); index++) {
            selected.add(index);
            collectCombinations(entries, requiredCount, exact, index + 1, selected, output);
            selected.remove(selected.size() - 1);
        }
    }

    private List<Integer> allIndexes(int size) {
        List<Integer> indexes = new java.util.ArrayList<>();
        for (int index = 0; index < size; index++) {
            indexes.add(index);
        }
        return indexes;
    }

    private List<String> exactMatches(List<String> entries, List<Integer> selected) {
        List<String> expressions = new java.util.ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            expressions.add(selected.contains(index) ? entry : "!" + entry);
        }
        return expressions;
    }
}
