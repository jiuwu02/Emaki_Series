package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;

public final class StrengthenRecipeResolver {

    private static final double EPSILON = 1.0E-9D;

    private final EmakiStrengthenPlugin plugin;
    private final EmakiItemAssemblyService itemAssemblyService;
    private final ItemSourceService itemSourceService;

    public StrengthenRecipeResolver(EmakiStrengthenPlugin plugin,
            EmakiItemAssemblyService itemAssemblyService,
            ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemAssemblyService = itemAssemblyService;
        this.itemSourceService = itemSourceService;
    }

    public ResolvedItem resolve(ItemStack itemStack, String explicitRecipeId) {
        boolean isEmaki = itemAssemblyService != null && itemAssemblyService.isEmakiItem(itemStack);
        ItemSource baseSource = resolveBaseSource(itemStack);
        String shorthand = ItemSourceUtil.toShorthand(baseSource);
        Map<String, Double> stats = aggregateStats(itemStack, isEmaki);
        List<String> loreLines = extractLore(itemStack);
        String slotGroup = resolveSlotGroup(itemStack, baseSource);
        String resolvedRecipeId = resolveRecipeId(explicitRecipeId, shorthand, baseSource, slotGroup, loreLines, stats);
        return new ResolvedItem(baseSource, shorthand, stats, loreLines, slotGroup, isEmaki, resolvedRecipeId);
    }

    public ItemSource resolveBaseSource(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (itemSourceService == null) {
            return null;
        }
        if (itemAssemblyService != null && itemAssemblyService.isEmakiItem(itemStack)) {
            ItemSource stored = itemAssemblyService.readBaseSource(itemStack);
            if (stored != null) {
                return stored;
            }
        }
        return itemSourceService.identifyItem(itemStack);
    }

    private String resolveRecipeId(String explicitRecipeId,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        return selectRecipeId(
                explicitRecipeId,
                recipeId -> plugin.recipeLoader().get(recipeId) != null,
                plugin.recipeLoader().ordered(),
                shorthand,
                baseSource,
                slotGroup,
                loreLines,
                stats
        );
    }

    static String selectRecipeId(String explicitRecipeId,
            Predicate<String> recipeExists,
            List<StrengthenRecipe> orderedRecipes,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        Predicate<String> exists = recipeExists == null ? recipeId -> false : recipeExists;
        if (Texts.isNotBlank(explicitRecipeId) && exists.test(explicitRecipeId)) {
            return explicitRecipeId;
        }
        if (orderedRecipes != null) {
            for (StrengthenRecipe recipe : orderedRecipes) {
                if (recipe == null || !exists.test(recipe.id())) {
                    continue;
                }
                if (matchesRecipe(recipe, shorthand, baseSource, slotGroup, loreLines, stats)) {
                    return recipe.id();
                }
            }
        }
        return resolveHeuristicRecipeId(exists, slotGroup, loreLines, stats);
    }

    static boolean matchesRecipe(StrengthenRecipe recipe,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        if (recipe == null || recipe.matchRule() == null || recipe.matchRule().empty()) {
            return false;
        }
        StrengthenRecipe.MatchRule rule = recipe.matchRule();
        if (!rule.sourceTypes().isEmpty()) {
            String sourceType = baseSource == null || baseSource.getType() == null ? "" : Texts.lower(baseSource.getType().name());
            if (!rule.sourceTypes().contains(sourceType)) {
                return false;
            }
        }
        if (!rule.sourceIds().isEmpty()) {
            String value = Texts.lower(shorthand);
            if (Texts.isBlank(value) || !rule.sourceIds().contains(value)) {
                return false;
            }
        }
        if (!rule.sourcePatterns().isEmpty()) {
            String value = Texts.toStringSafe(shorthand);
            if (Texts.isBlank(value)) {
                return false;
            }
            boolean matched = false;
            for (String pattern : rule.sourcePatterns()) {
                if (Texts.isBlank(pattern)) {
                    continue;
                }
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        if (!rule.slotGroups().isEmpty() && !rule.slotGroups().contains(Texts.lower(slotGroup))) {
            return false;
        }
        for (String fragment : rule.loreContains()) {
            if (!containsLore(loreLines, fragment)) {
                return false;
            }
        }
        if (!rule.statsAny().isEmpty()) {
            boolean matched = false;
            for (String statId : rule.statsAny()) {
                if (Math.abs(stats.getOrDefault(Texts.lower(statId), 0D)) > EPSILON) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    static String resolveHeuristicRecipeId(Predicate<String> recipeExists,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        Predicate<String> exists = recipeExists == null ? recipeId -> false : recipeExists;
        if (stats.getOrDefault("spell_attack", 0D) > EPSILON || containsLore(loreLines, "法术伤害")) {
            return exists.test("weapon_spell") ? "weapon_spell" : "";
        }
        if (stats.getOrDefault("projectile_attack", 0D) > EPSILON || containsLore(loreLines, "投射物伤害")) {
            return exists.test("weapon_projectile") ? "weapon_projectile" : "";
        }
        if ("offhand".equals(slotGroup)) {
            return exists.test("offhand_focus") ? "offhand_focus" : "";
        }
        if ("armor".equals(slotGroup)) {
            return exists.test("armor_guard") ? "armor_guard" : "";
        }
        if ("weapon".equals(slotGroup)) {
            return exists.test("weapon_physical") ? "weapon_physical" : "";
        }
        return exists.test("generic_visual") ? "generic_visual" : "";
    }

    private Map<String, Double> aggregateStats(ItemStack itemStack, boolean isEmaki) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (itemStack == null || itemStack.getType().isAir()) {
            return values;
        }
        if (isEmaki && itemAssemblyService != null) {
            for (EmakiItemLayerSnapshot snapshot : itemAssemblyService.readLayerSnapshots(itemStack).values()) {
                if (snapshot == null || snapshot.stats() == null) {
                    continue;
                }
                for (EmakiStatContribution contribution : snapshot.stats()) {
                    if (contribution == null || Texts.isBlank(contribution.statId())) {
                        continue;
                    }
                    values.merge(Texts.lower(contribution.statId()), contribution.amount(), Double::sum);
                }
            }
        }
        for (String line : extractLore(itemStack)) {
            if (line.contains("物理伤害")) {
                values.merge("physical_attack", 1D, Double::sum);
            }
            if (line.contains("法术伤害")) {
                values.merge("spell_attack", 1D, Double::sum);
            }
            if (line.contains("投射物伤害")) {
                values.merge("projectile_attack", 1D, Double::sum);
            }
        }
        return values;
    }

    private List<String> extractLore(ItemStack itemStack) {
        List<String> lines = new ArrayList<>();
        if (itemStack == null || !itemStack.hasItemMeta() || !itemStack.getItemMeta().hasLore()) {
            return lines;
        }
        List<String> lore = ItemTextBridge.loreLines(itemStack.getItemMeta());
        if (lore == null) {
            return lines;
        }
        for (String line : lore) {
            lines.add(Texts.stripMiniTags(line));
        }
        return lines;
    }

    static boolean containsLore(List<String> loreLines, String fragment) {
        if (Texts.isBlank(fragment) || loreLines == null) {
            return false;
        }
        String normalized = Texts.stripMiniTags(fragment);
        for (String line : loreLines) {
            if (line != null && line.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String resolveSlotGroup(ItemStack itemStack, ItemSource baseSource) {
        String name = itemStack == null || itemStack.getType() == null
                ? ""
                : itemStack.getType().name().toLowerCase(Locale.ROOT);
        String shorthand = ItemSourceUtil.toShorthand(baseSource);
        String combined = name + " " + Texts.lower(shorthand);
        if (combined.contains("shield") || combined.contains("totem")) {
            return "offhand";
        }
        if (combined.contains("helmet") || combined.contains("chestplate") || combined.contains("leggings") || combined.contains("boots")) {
            return "armor";
        }
        if (combined.contains("sword") || combined.contains("axe") || combined.contains("bow") || combined.contains("crossbow")
                || combined.contains("trident") || combined.contains("mace") || combined.contains("staff") || combined.contains("wand")) {
            return "weapon";
        }
        Material type = itemStack == null ? Material.AIR : itemStack.getType();
        if (type.isEdible() || type.isBlock()) {
            return "generic";
        }
        return "weapon";
    }

    public record ResolvedItem(ItemSource baseSource,
            String baseSourceSignature,
            Map<String, Double> stats,
            List<String> loreLines,
            String slotGroup,
            boolean emaki,
            String recipeId) {

        public ResolvedItem {
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            loreLines = loreLines == null ? List.of() : List.copyOf(loreLines);
        }
    }
}
