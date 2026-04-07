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

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRule;
import net.kyori.adventure.text.Component;

public final class ProfileResolver {

    private static final double EPSILON = 1.0E-9D;

    private final EmakiStrengthenPlugin plugin;

    public ProfileResolver(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public ResolvedItem resolve(ItemStack itemStack, String explicitProfileId) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        boolean isEmaki = coreLib != null && coreLib.itemAssemblyService().isEmakiItem(itemStack);
        ItemSource baseSource = resolveBaseSource(itemStack);
        String shorthand = ItemSourceUtil.toShorthand(baseSource);
        Map<String, Double> stats = aggregateStats(itemStack, isEmaki);
        List<String> loreLines = extractLore(itemStack);
        String slotGroup = resolveSlotGroup(itemStack, baseSource);
        String resolvedProfileId = resolveProfileId(explicitProfileId, shorthand, baseSource, slotGroup, loreLines, stats);
        return new ResolvedItem(baseSource, shorthand, stats, loreLines, slotGroup, isEmaki, resolvedProfileId);
    }

    public ItemSource resolveBaseSource(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null) {
            return null;
        }
        if (coreLib.itemAssemblyService().isEmakiItem(itemStack)) {
            ItemSource stored = coreLib.itemAssemblyService().readBaseSource(itemStack);
            if (stored != null) {
                return stored;
            }
        }
        return coreLib.itemSourceService().identifyItem(itemStack);
    }

    private String resolveProfileId(String explicitProfileId,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        return selectProfileId(
                explicitProfileId,
                profileId -> plugin.profileLoader().get(profileId) != null,
                plugin.ruleLoader().ordered(),
                shorthand,
                baseSource,
                slotGroup,
                loreLines,
                stats
        );
    }

    static String selectProfileId(String explicitProfileId,
            Predicate<String> profileExists,
            List<StrengthenRule> orderedRules,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        Predicate<String> exists = profileExists == null ? profileId -> false : profileExists;
        if (Texts.isNotBlank(explicitProfileId) && exists.test(explicitProfileId)) {
            return explicitProfileId;
        }
        if (orderedRules != null) {
            for (StrengthenRule rule : orderedRules) {
                if (rule == null || !exists.test(rule.profileId())) {
                    continue;
                }
                if (matchesRule(rule, shorthand, baseSource, slotGroup, loreLines, stats)) {
                    return rule.profileId();
                }
            }
        }
        return resolveHeuristicProfileId(exists, slotGroup, loreLines, stats);
    }

    static boolean matchesRule(StrengthenRule rule,
            String shorthand,
            ItemSource baseSource,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        if (!rule.sourceTypes().isEmpty()) {
            String sourceType = baseSource == null || baseSource.getType() == null ? "" : Texts.lower(baseSource.getType().name());
            if (!rule.sourceTypes().contains(sourceType)) {
                return false;
            }
        }
        if (Texts.isNotBlank(rule.sourcePattern())) {
            String value = Texts.toStringSafe(shorthand);
            if (Texts.isBlank(value) || !Pattern.compile(rule.sourcePattern(), Pattern.CASE_INSENSITIVE).matcher(value).find()) {
                return false;
            }
        }
        if (Texts.isNotBlank(rule.slotGroup()) && !Texts.lower(rule.slotGroup()).equals(Texts.lower(slotGroup))) {
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

    static String resolveHeuristicProfileId(Predicate<String> profileExists,
            String slotGroup,
            List<String> loreLines,
            Map<String, Double> stats) {
        Predicate<String> exists = profileExists == null ? profileId -> false : profileExists;
        if (stats.getOrDefault("spell_attack", 0D) > EPSILON || containsLore(loreLines, "法术伤害")) {
            return existingOrFallback(exists, "weapon_spell", "weapon_physical");
        }
        if (stats.getOrDefault("projectile_attack", 0D) > EPSILON || containsLore(loreLines, "投射物伤害")) {
            return existingOrFallback(exists, "weapon_projectile", "weapon_physical");
        }
        if ("offhand".equals(slotGroup)) {
            return existingOrFallback(exists, "offhand_focus", "generic_visual");
        }
        if ("armor".equals(slotGroup)) {
            return existingOrFallback(exists, "armor_guard", "generic_visual");
        }
        if ("weapon".equals(slotGroup)) {
            return existingOrFallback(exists, "weapon_physical", "generic_visual");
        }
        return existingOrFallback(exists, "generic_visual", "");
    }

    private Map<String, Double> aggregateStats(ItemStack itemStack, boolean isEmaki) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (itemStack == null || itemStack.getType().isAir()) {
            return values;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (isEmaki && coreLib != null) {
            for (EmakiItemLayerSnapshot snapshot : coreLib.itemAssemblyService().readLayerSnapshots(itemStack).values()) {
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
        List<Component> lore = itemStack.getItemMeta().lore();
        if (lore == null) {
            return lines;
        }
        for (Component line : lore) {
            lines.add(Texts.stripMiniTags(MiniMessages.serialize(line)));
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

    private static String existingOrFallback(Predicate<String> profileExists, String primary, String fallback) {
        Predicate<String> exists = profileExists == null ? profileId -> false : profileExists;
        if (Texts.isNotBlank(primary) && exists.test(primary)) {
            return primary;
        }
        if (Texts.isNotBlank(fallback) && exists.test(fallback)) {
            return fallback;
        }
        return "";
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
            String profileId) {

        public ResolvedItem {
            stats = stats == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stats));
            loreLines = loreLines == null ? List.of() : List.copyOf(loreLines);
        }
    }
}
