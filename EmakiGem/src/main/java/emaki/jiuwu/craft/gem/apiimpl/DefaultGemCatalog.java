package emaki.jiuwu.craft.gem.apiimpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.GemCatalog;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.GemStateService;

/**
 * {@link GemCatalog} 的运行时实现。
 *
 * <p>只读委托，不写状态、不触发事件。runtime 侧「找不到返回 null / 空集合」的约定在这里被翻译为
 * {@code Optional.empty()} 或 {@link EmakiResult} 失败，使不可用与「确实没有」可区分。
 */
public final class DefaultGemCatalog implements GemCatalog {

    private final EmakiGemPlugin plugin;

    public DefaultGemCatalog(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<String> gemIds() {
        if (plugin.gemLoader() == null) {
            return List.of();
        }
        return plugin.gemLoader().all().keySet().stream().sorted().toList();
    }

    @Override
    public @NotNull Optional<GemDefinitionView> gem(@Nullable String gemId, int level) {
        if (Texts.isBlank(gemId) || plugin.gemLoader() == null) {
            return Optional.empty();
        }
        GemDefinition definition = plugin.gemLoader().get(Texts.lower(gemId));
        return definition == null
                ? Optional.empty()
                : Optional.of(GemApiMapper.toDefinitionView(definition, level));
    }

    @Override
    public @NotNull Optional<GemDefinitionView> identifyGem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin.itemMatcher() == null) {
            return Optional.empty();
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(itemStack);
        if (instance == null || plugin.gemLoader() == null) {
            return Optional.empty();
        }
        GemDefinition definition = plugin.gemLoader().get(Texts.lower(instance.gemId()));
        return definition == null
                ? Optional.empty()
                : Optional.of(GemApiMapper.toDefinitionView(definition, instance.level()));
    }

    @Override
    public boolean isOpenerItem(@Nullable ItemStack itemStack) {
        return itemStack != null
                && plugin.itemMatcher() != null
                && plugin.itemMatcher().isOpenerItem(itemStack);
    }

    @Override
    public @NotNull EmakiResult<GemStateView> state(@Nullable ItemStack equipment) {
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        GemStateService stateService = plugin.stateService();
        if (stateService == null) {
            return EmakiResult.unavailable();
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.error.not_socketable");
        }
        GemState state = stateService.resolveState(equipment, itemDefinition);
        return EmakiResult.success(GemApiMapper.toStateView(itemDefinition, state));
    }

    @Override
    public @NotNull Map<String, Double> aggregatedAttributes(@Nullable ItemStack equipment) {
        GemState state = resolveStateOrNull(equipment);
        if (state == null || plugin.snapshotBuilder() == null) {
            return Map.of();
        }
        return plugin.snapshotBuilder().aggregateAttributes(state);
    }

    @Override
    public @NotNull List<String> aggregatedSkillIds(@Nullable ItemStack equipment) {
        GemState state = resolveStateOrNull(equipment);
        if (state == null || plugin.snapshotBuilder() == null) {
            return List.of();
        }
        return plugin.snapshotBuilder().aggregateSkillIds(state);
    }

    @Override
    public @NotNull List<GemResonanceView> resonances(@Nullable ItemStack equipment) {
        GemState state = resolveStateOrNull(equipment);
        GemResonanceService resonanceService = plugin.resonanceService();
        if (state == null || resonanceService == null || plugin.gemLoader() == null) {
            return List.of();
        }
        List<GemResonanceService.GemEntry> entries = new java.util.ArrayList<>();
        for (GemItemInstance instance : state.socketAssignments().values()) {
            GemDefinition definition = plugin.gemLoader().get(Texts.lower(instance.gemId()));
            if (definition != null) {
                entries.add(new GemResonanceService.GemEntry(definition, instance.level()));
            }
        }
        if (entries.isEmpty()) {
            return List.of();
        }
        return resonanceService.evaluateWithLevels(entries).stream()
                .map(GemApiMapper::toResonanceView)
                .toList();
    }

    @Override
    public @NotNull EmakiResult<GemRelationshipCheck> canInlay(@Nullable ItemStack equipment,
            @Nullable String gemId,
            int slotIndex) {
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        if (Texts.isBlank(gemId)) {
            return EmakiResult.invalidInput("gem.error.no_gem_id");
        }
        GemStateService stateService = plugin.stateService();
        if (stateService == null || plugin.gemLoader() == null) {
            return EmakiResult.unavailable();
        }
        GemDefinition candidate = plugin.gemLoader().get(Texts.lower(gemId));
        if (candidate == null) {
            return EmakiResult.notFound("gem.error.unknown_gem");
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.error.not_socketable");
        }
        GemState state = stateService.resolveState(equipment, itemDefinition);
        if (state == null) {
            return EmakiResult.notFound("gem.error.no_state");
        }
        GemItemDefinition.SocketSlot slot = itemDefinition.slot(slotIndex);
        if (slot == null) {
            return EmakiResult.success(GemRelationshipCheck.deny("gem.error.invalid_slot", Map.of()));
        }
        if (!state.isOpened(slotIndex)) {
            return EmakiResult.success(GemRelationshipCheck.deny("gem.error.slot_not_opened", Map.of()));
        }
        if (state.assignment(slotIndex) != null) {
            return EmakiResult.success(GemRelationshipCheck.deny("gem.error.slot_occupied", Map.of()));
        }
        if (!candidate.supportsSocketType(slot.type())) {
            return EmakiResult.success(GemRelationshipCheck.deny("gem.error.socket_type_mismatch", Map.of()));
        }
        return EmakiResult.success(
                GemApiMapper.toRelationshipCheck(stateService.validateInlayRelationships(state, candidate)));
    }

    @Override
    public @NotNull EmakiResult<GemRelationshipCheck> canExtract(@Nullable ItemStack equipment, int slotIndex) {
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        GemStateService stateService = plugin.stateService();
        if (stateService == null) {
            return EmakiResult.unavailable();
        }
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.error.not_socketable");
        }
        GemState state = stateService.resolveState(equipment, itemDefinition);
        if (state == null) {
            return EmakiResult.notFound("gem.error.no_state");
        }
        if (state.assignment(slotIndex) == null) {
            return EmakiResult.success(GemRelationshipCheck.deny("gem.error.slot_empty", Map.of()));
        }
        return EmakiResult.success(
                GemApiMapper.toRelationshipCheck(stateService.validateExtractionRelationships(state, slotIndex)));
    }

    /**
     * 解析装备上的宝石状态，非可镶嵌装备或状态缺失时返回 {@code null}。
     *
     * @param equipment 装备物品
     * @return 宝石状态或 {@code null}
     */
    private GemState resolveStateOrNull(ItemStack equipment) {
        if (equipment == null || equipment.getType().isAir()) {
            return null;
        }
        GemStateService stateService = plugin.stateService();
        return stateService == null ? null : stateService.resolveState(equipment);
    }
}
