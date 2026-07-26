package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.BoundSkillTrigger;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

public final class EquipmentSkillCollector {

    private static final int MAX_LOGGED_LORE_FAILURES = 128;

    private static final Map<EquipmentSlot, String> SLOT_NAMES = Map.of(
            EquipmentSlot.HAND, "main_hand",
            EquipmentSlot.OFF_HAND, "off_hand",
            EquipmentSlot.HEAD, "helmet",
            EquipmentSlot.CHEST, "chestplate",
            EquipmentSlot.LEGS, "leggings",
            EquipmentSlot.FEET, "boots"
    );

    private static final EquipmentSlot[] SCANNED_SLOTS = {
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final SkillPdcGateway skillPdcGateway;
    private final Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier;
    private final Supplier<AppConfig> appConfigSupplier;
    private final DebugLogger debugLogger;
    private final Logger logger;
    private final Map<LoreFailureKey, Boolean> loggedLoreFailures = new LinkedHashMap<>();

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier) {
        this(plugin, skillDefinitionsSupplier, AppConfig::defaults);
    }

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier,
            Supplier<AppConfig> appConfigSupplier) {
        this.skillDefinitionsSupplier = skillDefinitionsSupplier == null ? Map::of : skillDefinitionsSupplier;
        this.appConfigSupplier = appConfigSupplier == null ? AppConfig::defaults : appConfigSupplier;
        this.debugLogger = plugin instanceof DebugLoggerProvider provider ? provider.debugLogger() : null;
        this.skillPdcGateway = new SkillPdcGateway(debugLogger);
        this.logger = plugin == null ? null : plugin.getLogger();
    }

    public List<UnlockedSkillEntry> collect(Player player) {
        if (player == null) {
            return List.of();
        }
        List<UnlockedSkillEntry> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        Map<String, SkillDefinition> definitions = skillDefinitions();
        AppConfig.SkillSourceSettings sources = skillSources();
        UUID playerId = player.getUniqueId();
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = slotName(slot);
            ItemSkills parsed = resolveItemSkills(item, slotName, definitions, sources, playerId);
            for (String skillId : parsed.effectiveSkillIds()) {
                result.add(new UnlockedSkillEntry(skillId, "equipment", SkillSourceType.EQUIPMENT,
                        slotName, parsed.loreAliases().get(skillId)));
            }
        }
        return result;
    }

    public List<BoundSkillTrigger> collectBoundTriggers(Player player, String triggerId) {
        if (player == null || Texts.isBlank(triggerId)) {
            return List.of();
        }
        List<BoundSkillTrigger> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        Map<String, SkillDefinition> definitions = skillDefinitions();
        AppConfig.SkillSourceSettings sources = skillSources();
        String normalizedTrigger = Texts.normalizeId(triggerId).replace('-', '_');
        UUID playerId = player.getUniqueId();
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = slotName(slot);
            ItemSkills parsed = resolveItemSkills(item, slotName, definitions, sources, playerId);
            for (Map.Entry<String, String> entry : parsed.boundTriggers().entrySet()) {
                if (parsed.effectiveSkillIds().contains(entry.getKey())
                        && normalizedTrigger.equals(entry.getValue())) {
                    result.add(new BoundSkillTrigger(entry.getKey(), entry.getValue(), slotName));
                }
            }
        }
        return result;
    }

    private ItemSkills resolveItemSkills(ItemStack item,
            String slotName,
            Map<String, SkillDefinition> definitions,
            AppConfig.SkillSourceSettings sources,
            UUID playerId) {
        String activeSlot = sources.readPdcSkills()
                ? skillPdcGateway.readActiveSlot(item)
                : EquipmentSlotMatcher.SLOT_ALL;
        boolean pdcSlotMatches = !sources.readPdcSkills()
                || EquipmentSlotMatcher.matches(slotName, activeSlot);
        Map<String, String> loreAliases = sources.readLoreSkills()
                ? collectLoreSkillAliases(item, slotName, definitions, playerId)
                : Map.of();
        Set<String> pdcSkillIds = sources.readPdcSkills() && pdcSlotMatches
                ? normalizedSkillIds(skillPdcGateway.readSkillIds(item))
                : Set.of();
        Map<String, String> triggers = sources.readPdcSkills() && pdcSlotMatches
                ? skillPdcGateway.readBoundTriggers(item)
                : Map.of();
        Set<String> effective = resolveEffectiveSkillIds(loreAliases.keySet(), pdcSkillIds, sources);
        String rejection = resolveRejection(sources, pdcSlotMatches, effective);
        ItemSkills resolved = new ItemSkills(loreAliases, pdcSkillIds, effective, triggers, activeSlot, rejection);
        logSourceResolution(playerId, slotName, sources, resolved);
        return resolved;
    }

    private String resolveRejection(AppConfig.SkillSourceSettings sources,
            boolean pdcSlotMatches,
            Set<String> effectiveSkillIds) {
        if (!pdcSlotMatches) {
            return "active_slot_mismatch";
        }
        if (!sources.requireLorePdcMatch() || !effectiveSkillIds.isEmpty()) {
            return "";
        }
        return !sources.readLoreSkills() || !sources.readPdcSkills()
                ? "strict_reader_disabled"
                : "strict_no_common_ids";
    }

    private Set<String> resolveEffectiveSkillIds(Set<String> loreSkillIds,
            Set<String> pdcSkillIds,
            AppConfig.SkillSourceSettings sources) {
        if (sources.requireLorePdcMatch()) {
            if (!sources.readLoreSkills() || !sources.readPdcSkills()) {
                return Set.of();
            }
            Set<String> intersection = new LinkedHashSet<>();
            for (String skillId : pdcSkillIds) {
                if (loreSkillIds.contains(skillId)) {
                    intersection.add(skillId);
                }
            }
            return immutableSet(intersection);
        }
        Set<String> union = new LinkedHashSet<>();
        union.addAll(pdcSkillIds);
        union.addAll(loreSkillIds);
        return immutableSet(union);
    }

    private Map<String, String> collectLoreSkillAliases(ItemStack item,
            String slotName,
            Map<String, SkillDefinition> definitions,
            UUID playerId) {
        try {
            return collectLoreSkillAliases(item, definitions);
        } catch (RuntimeException | LinkageError failure) {
            logLoreFailure(playerId, slotName, item.getType(), failure);
            return Map.of();
        }
    }

    private Map<String, String> collectLoreSkillAliases(ItemStack item, Map<String, SkillDefinition> definitions) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return Map.of();
        }
        List<String> lore = ItemTextBridge.loreLines(meta);
        if (lore == null || lore.isEmpty() || definitions == null || definitions.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedLines = new ArrayList<>(lore.size());
        for (String line : lore) {
            normalizedLines.add(Texts.normalizeWhitespace(Texts.stripMiniTags(line)));
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        for (SkillDefinition definition : definitions.values()) {
            if (definition == null || definition.loreAliases().isEmpty()) {
                continue;
            }
            String skillId = Texts.normalizeId(definition.id());
            if (Texts.isBlank(skillId)) {
                continue;
            }
            for (String alias : definition.loreAliases()) {
                if (Texts.isBlank(alias)) {
                    continue;
                }
                if (matchesLoreAlias(normalizedLines, alias)) {
                    aliases.putIfAbsent(skillId, alias);
                    break;
                }
            }
        }
        return aliases.isEmpty() ? Map.of() : Map.copyOf(aliases);
    }

    private boolean matchesLoreAlias(List<String> normalizedLines, String alias) {
        for (String normalizedLine : normalizedLines) {
            if (normalizedLine.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizedSkillIds(List<String> skillIds) {
        Set<String> result = new LinkedHashSet<>();
        if (skillIds != null) {
            for (String skillId : skillIds) {
                String normalized = Texts.normalizeId(skillId);
                if (Texts.isNotBlank(normalized)) {
                    result.add(normalized);
                }
            }
        }
        return immutableSet(result);
    }

    private Set<String> immutableSet(Set<String> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    private Map<String, SkillDefinition> skillDefinitions() {
        Map<String, SkillDefinition> definitions = skillDefinitionsSupplier.get();
        return definitions == null ? Map.of() : definitions;
    }

    private AppConfig.SkillSourceSettings skillSources() {
        AppConfig config = appConfigSupplier.get();
        return config == null || config.skillSources() == null
                ? AppConfig.SkillSourceSettings.defaults()
                : config.skillSources();
    }

    private String slotName(EquipmentSlot slot) {
        return SLOT_NAMES.getOrDefault(slot, slot.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void logSourceResolution(UUID playerId,
            String slotName,
            AppConfig.SkillSourceSettings sources,
            ItemSkills resolved) {
        if (debugLogger == null) {
            return;
        }
        debugLogger.log("unlock", playerId, "unlock.equipment_sources", Map.of(
                "slot", slotName,
                "active_slot", resolved.activeSlot(),
                "read_lore", sources.readLoreSkills(),
                "read_pdc", sources.readPdcSkills(),
                "require_match", sources.requireLorePdcMatch(),
                "lore_ids", resolved.loreAliases().keySet(),
                "pdc_ids", resolved.pdcSkillIds(),
                "effective_ids", resolved.effectiveSkillIds(),
                "triggers", resolved.boundTriggers(),
                "reason", resolved.rejection()
        ));
    }

    private void logLoreFailure(UUID playerId, String slotName, Material material, Throwable failure) {
        if (logger == null) {
            return;
        }
        LoreFailureKey key = new LoreFailureKey(playerId, slotName, material, failure.getClass());
        synchronized (loggedLoreFailures) {
            if (loggedLoreFailures.containsKey(key) || loggedLoreFailures.size() >= MAX_LOGGED_LORE_FAILURES) {
                return;
            }
            loggedLoreFailures.put(key, Boolean.TRUE);
        }
        logger.warning("[EquipmentSkillCollector] Lore read failed: player=" + playerId
                + ", slot=" + slotName
                + ", material=" + material
                + ", exception=" + failure.getClass().getName());
    }

    private record ItemSkills(Map<String, String> loreAliases,
            Set<String> pdcSkillIds,
            Set<String> effectiveSkillIds,
            Map<String, String> boundTriggers,
            String activeSlot,
            String rejection) {

        private ItemSkills {
            loreAliases = loreAliases == null || loreAliases.isEmpty() ? Map.of() : Map.copyOf(loreAliases);
            pdcSkillIds = pdcSkillIds == null || pdcSkillIds.isEmpty() ? Set.of() : Set.copyOf(pdcSkillIds);
            effectiveSkillIds = effectiveSkillIds == null || effectiveSkillIds.isEmpty() ? Set.of() : Set.copyOf(effectiveSkillIds);
            boundTriggers = boundTriggers == null || boundTriggers.isEmpty() ? Map.of() : Map.copyOf(boundTriggers);
            activeSlot = activeSlot == null ? EquipmentSlotMatcher.SLOT_ALL : activeSlot;
            rejection = rejection == null ? "" : rejection;
        }
    }

    private record LoreFailureKey(UUID playerId, String slotName, Material material, Class<?> failureType) {
    }
}
