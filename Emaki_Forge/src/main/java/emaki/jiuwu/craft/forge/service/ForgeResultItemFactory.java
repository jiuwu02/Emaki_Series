package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class ForgeResultItemFactory {

    private record MaterialContribution(ForgeMaterial material,
                                        int amount,
                                        int slot,
                                        String category,
                                        int sequence,
                                        ItemSource source) {
    }

    private final EmakiForgePlugin plugin;

    ForgeResultItemFactory(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    ItemStack createResultItem(Recipe recipe,
                               ForgeService.GuiItems guiItems,
                               double multiplier,
                               QualitySettings.QualityTier qualityTier) {
        Recipe.ResultConfig resultConfig = recipe.result();
        if (resultConfig == null || resultConfig.outputItem() == null) {
            return null;
        }
        ItemStack base = plugin.itemIdentifierService().createItem(resultConfig.outputItem(), 1);
        if (base == null) {
            return null;
        }
        ItemStack resultItem = base.clone();
        applyResultMetaEffects(resultItem, resultConfig);
        List<MaterialContribution> materials = collectMaterialContributions(guiItems);
        applyMaterialEffects(resultItem, materials, multiplier);
        applyQualityMetaEffects(resultItem, qualityTier);
        plugin.pdcService().apply(resultItem, recipe, toPdcRecords(materials), qualityTier, multiplier);
        return resultItem;
    }

    String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        String resolvedItemName = resolveItemName(itemStack);
        if (Texts.isNotBlank(resolvedItemName)) {
            return resolvedItemName;
        }
        if (recipe != null && recipe.result() != null && recipe.result().outputItem() != null) {
            return recipe.result().outputItem().getIdentifier();
        }
        return "物品";
    }

    String resolveSourceItemName(ForgeService.GuiItems guiItems, ItemStack resultItem, Recipe recipe) {
        String sourceName = resolveItemName(guiItems == null ? null : guiItems.targetItem());
        return Texts.isNotBlank(sourceName) ? sourceName : resolveResultItemName(recipe, resultItem);
    }

    String buildShowItemPlaceholder(ForgeService.GuiItems guiItems, Recipe recipe, ItemStack resultItem) {
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
        Component display = resolveDisplayComponent(guiItems == null ? null : guiItems.targetItem());
        if (display == null) {
            display = resolveDisplayComponent(resultItem);
        }
        if (display == null) {
            display = MiniMessages.parse(resolveResultItemName(recipe, resultItem));
        }
        try {
            return MiniMessages.serialize(display.hoverEvent(resultItem.asHoverEvent(showItem -> showItem)));
        } catch (Exception ignored) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
    }

    private void applyResultMetaEffects(ItemStack itemStack, Recipe.ResultConfig resultConfig) {
        applyMetaActions(itemStack, resultConfig.nameModifications(), resultConfig.loreActions(), Map.of());
    }

    private void applyQualityMetaEffects(ItemStack itemStack, QualitySettings.QualityTier qualityTier) {
        if (qualityTier == null) {
            return;
        }
        QualitySettings settings = plugin.appConfig().qualitySettings();
        if (!settings.itemMetaEnabled()) {
            return;
        }
        applyMetaActions(
            itemStack,
            settings.itemMetaNameModifications(qualityTier.name()),
            settings.itemMetaLoreActions(qualityTier.name()),
            Map.of()
        );
    }

    private void applyMaterialEffects(ItemStack itemStack, List<MaterialContribution> materials, double multiplier) {
        Map<String, Double> contributions = new LinkedHashMap<>();
        List<Map<String, Object>> nameMods = new ArrayList<>();
        List<Map<String, Object>> loreActions = new ArrayList<>();
        for (MaterialContribution material : materials) {
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                contributions.merge(entry.getKey(), entry.getValue() * material.amount(), Double::sum);
            }
            nameMods.addAll(material.material().nameModifications());
            loreActions.addAll(material.material().loreActions());
        }
        Map<String, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : contributions.entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() * multiplier);
        }
        applyMetaActions(itemStack, nameMods, loreActions, scaled);
    }

    private List<MaterialContribution> collectMaterialContributions(ForgeService.GuiItems guiItems) {
        List<MaterialContribution> materials = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<Integer, ItemStack> entry : guiItems.requiredMaterials().entrySet()) {
            MaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "required", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : guiItems.optionalMaterials().entrySet()) {
            MaterialContribution contribution = toMaterialContribution(entry.getKey(), entry.getValue(), "optional", sequence++);
            if (contribution != null) {
                materials.add(contribution);
            }
        }
        materials.sort(Comparator.comparingInt((MaterialContribution value) -> value.material().priority())
            .thenComparingInt(MaterialContribution::sequence));
        return materials;
    }

    private MaterialContribution toMaterialContribution(int slot, ItemStack itemStack, String category, int sequence) {
        if (itemStack == null) {
            return null;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        ForgeMaterial material = findMaterialBySource(source);
        if (material == null) {
            return null;
        }
        return new MaterialContribution(material, itemStack.getAmount(), slot, category, sequence, source);
    }

    private List<ForgePdcService.MaterialRecord> toPdcRecords(List<MaterialContribution> materials) {
        List<ForgePdcService.MaterialRecord> records = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (MaterialContribution material : materials) {
            records.add(new ForgePdcService.MaterialRecord(
                material.category(),
                material.material().id(),
                material.amount(),
                material.slot(),
                material.sequence(),
                material.source(),
                timestamp
            ));
        }
        return records;
    }

    private void applyMetaActions(ItemStack itemStack,
                                  List<Map<String, Object>> nameModifications,
                                  List<Map<String, Object>> loreActions,
                                  Map<String, Double> statContributions) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        String currentName = itemMeta.hasCustomName() ? MiniMessages.serialize(itemMeta.customName()) : "";
        for (Map<String, Object> modification : nameModifications) {
            String action = Texts.lower(modification.get("action"));
            String value = Texts.toStringSafe(modification.get("value"));
            String regexPattern = Texts.toStringSafe(modification.get("regex_pattern"));
            String replacement = Texts.toStringSafe(modification.get("replacement"));
            switch (action) {
                case "append_suffix" -> currentName = currentName + value;
                case "prepend_prefix" -> currentName = value + currentName;
                case "replace" -> currentName = value;
                case "regex_replace" -> {
                    try {
                        currentName = Pattern.compile(regexPattern).matcher(currentName).replaceAll(replacement);
                    } catch (Exception ignored) {
                    }
                }
                default -> {
                }
            }
        }
        if (Texts.isNotBlank(currentName)) {
            itemMeta.customName(MiniMessages.parse(currentName));
        }
        List<Component> lore = new ArrayList<>(itemMeta.hasLore() && itemMeta.lore() != null ? itemMeta.lore() : List.<Component>of());
        for (Map<String, Object> loreAction : loreActions) {
            applyLoreAction(lore, loreAction, statContributions);
        }
        if (!lore.isEmpty()) {
            itemMeta.lore(lore);
        }
        itemStack.setItemMeta(itemMeta);
    }

    private void applyLoreAction(List<Component> lore,
                                 Map<String, Object> action,
                                 Map<String, Double> statContributions) {
        String type = Texts.lower(action.get("action"));
        List<String> content = Texts.asStringList(action.get("content"));
        String targetPattern = Texts.toStringSafe(action.get("target_pattern"));
        String regexPattern = Texts.toStringSafe(action.get("regex_pattern"));
        String replacement = Texts.toStringSafe(action.get("replacement"));
        switch (type) {
            case "insert_below" -> insertRelative(lore, content, statContributions, targetPattern, true, false);
            case "insert_above" -> insertRelative(lore, content, statContributions, targetPattern, false, false);
            case "insert_below_regex" -> insertRelative(lore, content, statContributions, regexPattern, true, true);
            case "insert_above_regex" -> insertRelative(lore, content, statContributions, regexPattern, false, true);
            case "prepend_line", "prepend_lines", "append_first_line", "append_first_lines", "insert_first" ->
                insertAt(lore, 0, content, statContributions);
            case "append", "append_line", "append_lines" -> insertAt(lore, lore.size(), content, statContributions);
            case "replace_line" -> replaceLine(lore, targetPattern, content, statContributions);
            case "delete_line" -> deleteLine(lore, targetPattern, false);
            case "delete_line_regex" -> deleteLine(lore, regexPattern, true);
            case "regex_replace" -> replaceRegex(lore, regexPattern, replacement, statContributions);
            default -> {
            }
        }
    }

    private void insertRelative(List<Component> lore,
                                List<String> content,
                                Map<String, Double> statContributions,
                                String pattern,
                                boolean below,
                                boolean regex) {
        int index = -1;
        for (int line = 0; line < lore.size(); line++) {
            String plain = MiniMessages.plain(lore.get(line));
            boolean matches = regex ? Pattern.compile(pattern).matcher(plain).find() : plain.contains(pattern);
            if (matches) {
                index = below ? line + 1 : line;
                break;
            }
        }
        insertAt(lore, index < 0 ? lore.size() : index, content, statContributions);
    }

    private void insertAt(List<Component> lore, int index, List<String> content, Map<String, Double> statContributions) {
        int insertIndex = index;
        for (String line : content) {
            lore.add(insertIndex++, MiniMessages.parse(formatStatLine(line, statContributions)));
        }
    }

    private void replaceLine(List<Component> lore,
                             String targetPattern,
                             List<String> content,
                             Map<String, Double> statContributions) {
        for (int index = 0; index < lore.size(); index++) {
            String plain = MiniMessages.plain(lore.get(index));
            if (!plain.contains(targetPattern)) {
                continue;
            }
            String replacement = content.isEmpty() ? "" : formatStatLine(content.get(0), statContributions);
            lore.set(index, MiniMessages.parse(replacement));
            return;
        }
    }

    private void deleteLine(List<Component> lore, String pattern, boolean regex) {
        for (int index = lore.size() - 1; index >= 0; index--) {
            String plain = MiniMessages.plain(lore.get(index));
            boolean matches = regex ? Pattern.compile(pattern).matcher(plain).find() : plain.contains(pattern);
            if (matches) {
                lore.remove(index);
            }
        }
    }

    private void replaceRegex(List<Component> lore,
                              String regexPattern,
                              String replacement,
                              Map<String, Double> statContributions) {
        for (int index = 0; index < lore.size(); index++) {
            String plain = MiniMessages.plain(lore.get(index));
            try {
                String updated = Pattern.compile(regexPattern).matcher(plain).replaceAll(replacement);
                lore.set(index, MiniMessages.parse(formatStatLine(updated, statContributions)));
            } catch (Exception ignored) {
            }
        }
    }

    private String formatStatLine(String line, Map<String, Double> statContributions) {
        String formatted = Texts.toStringSafe(line);
        for (Map.Entry<String, Double> entry : statContributions.entrySet()) {
            formatted = formatted.replace(
                "{" + entry.getKey() + "}",
                Numbers.formatNumber(entry.getValue(), plugin.appConfig().defaultNumberFormat())
            );
        }
        return formatted;
    }

    private ForgeMaterial findMaterialBySource(ItemSource source) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : plugin.materialLoader().all().values()) {
            if (ItemSourceUtil.matches(source, material.source())) {
                return material;
            }
        }
        return null;
    }

    private String resolveItemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "";
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()) {
            return MiniMessages.plain(itemStack.getItemMeta().customName());
        }
        try {
            return MiniMessages.plain(itemStack.effectiveName());
        } catch (Exception ignored) {
            return itemStack.getType().name();
        }
    }

    private Component resolveDisplayComponent(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()) {
            return itemStack.getItemMeta().customName();
        }
        try {
            return itemStack.effectiveName();
        } catch (Exception ignored) {
            return Component.text(itemStack.getType().name());
        }
    }
}
