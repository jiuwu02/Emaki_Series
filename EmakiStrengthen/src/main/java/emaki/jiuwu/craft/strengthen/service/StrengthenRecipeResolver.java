package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;

public final class StrengthenRecipeResolver {

    private static final Logger LOGGER = Logger.getLogger(StrengthenRecipeResolver.class.getName());

    private static final double EPSILON = 1.0E-9D;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

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
        ItemSourceRef baseSource = resolveBaseSource(itemStack);
        String shorthand = ItemSourceUtil.toShorthand(baseSource);
        Map<String, Double> stats = aggregateStats(itemStack, isEmaki);
        List<String> loreLines = extractLore(itemStack);
        String slotGroup = resolveSlotGroup(itemStack, baseSource);
        String resolvedRecipeId = resolveRecipeId(explicitRecipeId, shorthand, baseSource, slotGroup, loreLines, stats,
                itemStack);
        return new ResolvedItem(baseSource, shorthand, stats, loreLines, slotGroup, isEmaki, resolvedRecipeId);
    }

    public String resolveRecipeId(String recipeId) {
        if (Texts.isBlank(recipeId) || plugin.recipeLoader() == null) {
            return "";
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(recipeId);
        return recipe == null ? "" : recipe.id();
    }

    public ItemSourceRef resolveBaseSource(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (itemSourceService == null) {
            return null;
        }
        if (itemAssemblyService != null && itemAssemblyService.isEmakiItem(itemStack)) {
            ItemSourceRef stored = itemAssemblyService.readBaseSource(itemStack);
            if (stored != null) {
                return stored;
            }
        }
        return itemSourceService.identifyItem(itemStack);
    }

    private String resolveRecipeId(String explicitRecipeId,
            String shorthand,
            ItemSourceRef baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats,
            ItemStack itemStack) {
        return selectRecipeId(
                explicitRecipeId,
                recipeId -> plugin.recipeLoader().get(recipeId) != null,
                plugin.recipeLoader().ordered(),
                shorthand,
                slotGroup,
                loreLines,
                stats,
                recipeId -> plugin.recipeLoader().recipeMatcher(recipeId),
                buildMatchContext(itemStack, baseSource)
        );
    }

    private static @Nullable MatchContext buildMatchContext(@Nullable ItemStack itemStack,
            @Nullable ItemSourceRef baseSource) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return MatchContext.of(itemStack, baseSource, null);
    }

    static String selectRecipeId(String explicitRecipeId,
            Predicate<String> recipeExists,
            List<StrengthenRecipe> orderedRecipes,
            String shorthand,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        return selectRecipeId(explicitRecipeId, recipeExists, orderedRecipes, shorthand, slotGroup,
                loreLines, stats, recipeId -> null, null);
    }

    static String selectRecipeId(String explicitRecipeId,
            Predicate<String> recipeExists,
            List<StrengthenRecipe> orderedRecipes,
            String shorthand,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats,
            Function<String, Matcher> matcherLookup,
            @Nullable MatchContext matchContext) {
        Predicate<String> exists = recipeExists == null ? recipeId -> false : recipeExists;
        Function<String, Matcher> matchers = matcherLookup == null ? recipeId -> null : matcherLookup;
        if (Texts.isNotBlank(explicitRecipeId) && exists.test(explicitRecipeId)) {
            return explicitRecipeId;
        }
        if (orderedRecipes != null) {
            for (StrengthenRecipe recipe : orderedRecipes) {
                if (recipe == null || !exists.test(recipe.id())) {
                    continue;
                }
                if (!matchesRecipe(recipe, shorthand, slotGroup, stats)) {
                    continue;
                }
                if (satisfiesRecipeMatcher(matchers.apply(recipe.id()), matchContext)) {
                    return recipe.id();
                }
            }
        }
        return resolveHeuristicRecipeId(exists, slotGroup, loreLines, stats);
    }

    static boolean satisfiesRecipeMatcher(@Nullable Matcher matcher, @Nullable MatchContext matchContext) {
        if (matcher == null) {
            return true;
        }
        if (matchContext == null) {
            return false;
        }
        try {
            return matcher.test(matchContext);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warning("配方 Matcher 判定抛出异常，视为不匹配: " + String.valueOf(exception.getMessage()));
            return false;
        }
    }

    static boolean matchesRecipe(StrengthenRecipe recipe,
            String shorthand,
            String slotGroup,
            Map<String, Double> stats) {
        if (recipe == null || !recipe.matchingConfigured()) {
            return false;
        }
        if (!matchesSourcePatterns(recipe.sourcePatterns(), shorthand)) {
            return false;
        }
        if (!recipe.slotGroups().isEmpty() && !recipe.slotGroups().contains(Texts.lower(slotGroup))) {
            return false;
        }
        return matchesStatsAny(recipe.statsAny(), stats);
    }

    private static boolean matchesSourcePatterns(List<String> patterns, String shorthand) {
        if (patterns.isEmpty()) {
            return true;
        }
        String value = Texts.toStringSafe(shorthand);
        if (Texts.isBlank(value)) {
            return false;
        }
        for (String pattern : patterns) {
            if (Texts.isBlank(pattern)) {
                continue;
            }
            Pattern compiled = PATTERN_CACHE.computeIfAbsent(pattern,
                    p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE));
            if (compiled.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStatsAny(List<String> statIds, Map<String, Double> stats) {
        if (statIds.isEmpty()) {
            return true;
        }
        for (String statId : statIds) {
            if (Math.abs(stats.getOrDefault(Texts.lower(statId), 0D)) > EPSILON) {
                return true;
            }
        }
        return false;
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

    private String resolveSlotGroup(ItemStack itemStack, ItemSourceRef baseSource) {
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

    public record ResolvedItem(ItemSourceRef baseSource,
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

    public static void clearPatternCache() {
        PATTERN_CACHE.clear();
    }
}
