package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.model.BoundSkillTrigger;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

public final class EquipmentSkillCollector {

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

    private final Supplier<AppConfig> appConfigSupplier;
    private final Supplier<EaBridge> eaBridgeSupplier;
    private final DebugLogger debugLogger;

    public EquipmentSkillCollector(JavaPlugin plugin) {
        this(plugin, AppConfig::defaults, () -> null);
    }

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<AppConfig> appConfigSupplier) {
        this(plugin, appConfigSupplier, () -> null);
    }

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<AppConfig> appConfigSupplier,
            Supplier<EaBridge> eaBridgeSupplier) {
        this.appConfigSupplier = appConfigSupplier == null ? AppConfig::defaults : appConfigSupplier;
        this.eaBridgeSupplier = eaBridgeSupplier == null ? () -> null : eaBridgeSupplier;
        this.debugLogger = plugin instanceof DebugLoggerProvider provider ? provider.debugLogger() : null;
    }

    public List<UnlockedSkillEntry> collect(Player player) {
        if (player == null) {
            return List.of();
        }
        List<UnlockedSkillEntry> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        AppConfig.SkillSourceSettings sources = skillSources();
        UUID playerId = player.getUniqueId();
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = slotName(slot);
            ItemSkills parsed = resolveItemSkills(player, item, slotName, sources, playerId);
            for (String skillId : parsed.effectiveSkillIds()) {
                result.add(new UnlockedSkillEntry(skillId, "equipment", SkillSourceType.EQUIPMENT,
                        slotName, ""));
            }
        }
        return result;
    }

    public List<BoundSkillTrigger> collectBoundTriggers(Player player, String triggerId) {
        String normalizedTrigger = EquipmentSkillPdcCodec.normalizeTriggerId(triggerId);
        if (player == null || normalizedTrigger.isEmpty()) {
            return List.of();
        }
        List<BoundSkillTrigger> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        AppConfig.SkillSourceSettings sources = skillSources();
        UUID playerId = player.getUniqueId();
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = slotName(slot);
            ItemSkills parsed = resolveItemSkills(player, item, slotName, sources, playerId);
            for (Map.Entry<String, String> entry : parsed.boundTriggers().entrySet()) {
                if (parsed.effectiveSkillIds().contains(entry.getKey())
                        && normalizedTrigger.equals(entry.getValue())) {
                    result.add(new BoundSkillTrigger(entry.getKey(), entry.getValue(), slotName));
                }
            }
        }
        return result;
    }

    private ItemSkills resolveItemSkills(Player player,
            ItemStack item,
            String slotName,
            AppConfig.SkillSourceSettings sources,
            UUID playerId) {
        if (!isItemContributionActive(player, item, slotName)) {
            ItemSkills gated = new ItemSkills(
                    Set.of(),
                    Set.of(),
                    Map.of(),
                    EquipmentSkillPdcCodec.SLOT_ALL,
                    "item_condition_gate"
            );
            logSourceResolution(playerId, slotName, sources, gated);
            return gated;
        }
        EquipmentSkillPayload pdcPayload = sources.readPdcSkills()
                ? EquipmentSkillPdcCodec.read(item)
                : new EquipmentSkillPayload(List.of(), EquipmentSkillPdcCodec.SLOT_ALL, Map.of());
        String activeSlot = pdcPayload.activeSlot();
        boolean pdcSlotMatches = !sources.readPdcSkills()
                || EquipmentSkillPdcCodec.matchesSlot(slotName, activeSlot);
        Set<String> pdcSkillIds = sources.readPdcSkills() && pdcSlotMatches
                ? immutableSet(new LinkedHashSet<>(pdcPayload.skillIds()))
                : Set.of();
        Map<String, String> triggers = sources.readPdcSkills() && pdcSlotMatches
                ? pdcPayload.boundTriggers()
                : Map.of();
        Set<String> effective = resolveEffectiveSkillIds(pdcSkillIds, sources);
        String rejection = resolveRejection(sources, pdcSlotMatches, effective);
        ItemSkills resolved = new ItemSkills(pdcSkillIds, effective, triggers, activeSlot, rejection);
        logSourceResolution(playerId, slotName, sources, resolved);
        return resolved;
    }

    private boolean isItemContributionActive(Player player, ItemStack item, String slotName) {
        EaBridge eaBridge = eaBridgeSupplier.get();
        return eaBridge == null || eaBridge.isItemContributionActive(player, item, slotName);
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

    private Set<String> resolveEffectiveSkillIds(Set<String> pdcSkillIds,
            AppConfig.SkillSourceSettings sources) {
        if (sources.requireLorePdcMatch()) {
            return Set.of();
        }
        return immutableSet(pdcSkillIds);
    }

    private Set<String> immutableSet(Set<String> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    private AppConfig.SkillSourceSettings skillSources() {
        AppConfig config = appConfigSupplier.get();
        return config == null || config.skillSources() == null
                ? AppConfig.SkillSourceSettings.defaults()
                : config.skillSources();
    }

    private String slotName(EquipmentSlot slot) {
        return SLOT_NAMES.getOrDefault(slot, slot.name().toLowerCase(Locale.ROOT));
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
                "pdc_ids", resolved.pdcSkillIds(),
                "effective_ids", resolved.effectiveSkillIds(),
                "triggers", resolved.boundTriggers(),
                "reason", resolved.rejection()
        ));
    }

    private record ItemSkills(Set<String> pdcSkillIds,
            Set<String> effectiveSkillIds,
            Map<String, String> boundTriggers,
            String activeSlot,
            String rejection) {

        private ItemSkills {
            pdcSkillIds = pdcSkillIds == null || pdcSkillIds.isEmpty() ? Set.of() : Set.copyOf(pdcSkillIds);
            effectiveSkillIds = effectiveSkillIds == null || effectiveSkillIds.isEmpty() ? Set.of() : Set.copyOf(effectiveSkillIds);
            boundTriggers = boundTriggers == null || boundTriggers.isEmpty() ? Map.of() : Map.copyOf(boundTriggers);
            activeSlot = activeSlot == null ? EquipmentSkillPdcCodec.SLOT_ALL : activeSlot;
            rejection = rejection == null ? "" : rejection;
        }
    }
}
