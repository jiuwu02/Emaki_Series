package emaki.jiuwu.craft.skills.apiimpl;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillCatalog;
import emaki.jiuwu.craft.skills.api.model.PlayerSkillView;
import emaki.jiuwu.craft.skills.api.model.SkillDefinitionView;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradePreview;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

/** Runtime skill catalog. */
public final class DefaultSkillCatalog implements SkillCatalog {

    private final EmakiSkillsPlugin plugin;

    public DefaultSkillCatalog(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<SkillDefinitionView> skills() {
        if (!plugin.isEnabled() || plugin.skillDefinitionLoader() == null) {
            return List.of();
        }
        return plugin.skillDefinitionLoader().all().values().stream()
                .map(DefaultSkillCatalog::toView)
                .sorted(java.util.Comparator.comparing(SkillDefinitionView::id))
                .toList();
    }

    @Override
    public @NotNull Optional<SkillDefinitionView> skill(@Nullable String skillId) {
        if (!plugin.isEnabled() || plugin.skillRegistryService() == null || Texts.isBlank(skillId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugin.skillRegistryService().getDefinition(skillId))
                .map(DefaultSkillCatalog::toView);
    }

    @Override
    public @NotNull EmakiResult<PlayerSkillView> playerSkills(@Nullable Player player) {
        EmakiResult<PlayerSkillView> guard = guardPlayer(player);
        if (guard != null) {
            return guard;
        }
        PlayerSkillProfile profile = plugin.playerSkillDataStore().get(player);
        if (profile == null) {
            return EmakiResult.notFound("skills.player.profile_not_loaded");
        }
        List<PlayerSkillView.SkillSlotView> slots = profile.bindings().stream()
                .map(binding -> new PlayerSkillView.SkillSlotView(
                        binding.slotIndex(), binding.skillId(), binding.triggerId()))
                .toList();
        return EmakiResult.success(new PlayerSkillView(slots, plugin.castModeService().isCastModeEnabled(player)));
    }

    @Override
    public @NotNull EmakiResult<Integer> level(@Nullable Player player, @Nullable String skillId) {
        EmakiResult<Integer> guard = guardPlayer(player);
        if (guard != null) {
            return guard;
        }
        SkillDefinition definition = definition(skillId);
        return definition == null
                ? invalidOrMissing(skillId, "skills.skill.not_found")
                : EmakiResult.success(plugin.skillLevelService().currentLevel(player, definition));
    }

    @Override
    public @NotNull EmakiResult<SkillUpgradePreview> upgradePreview(@Nullable Player player,
            @Nullable String skillId) {
        EmakiResult<SkillUpgradePreview> guard = guardPlayer(player);
        if (guard != null) {
            return guard;
        }
        SkillDefinition definition = definition(skillId);
        if (definition == null) {
            return invalidOrMissing(skillId, "skills.skill.not_found");
        }
        if (definition.upgrade() == null || !definition.upgrade().enabled()) {
            return EmakiResult.rejected("skills.upgrade.disabled");
        }
        SkillUpgradeService.UpgradePreview preview = plugin.skillUpgradeService().preview(player, definition);
        if (preview == null) {
            return EmakiResult.internalError("skills.upgrade.preview_failed");
        }
        if (preview.currentLevel() >= preview.maxLevel()) {
            return EmakiResult.rejected("skills.upgrade.max_level");
        }
        return EmakiResult.success(toPreview(preview));
    }

    @Override
    public @NotNull EmakiResult<Boolean> hasLearned(@Nullable Player player, @Nullable String skillId) {
        EmakiResult<Boolean> guard = guardPlayer(player);
        if (guard != null) {
            return guard;
        }
        if (Texts.isBlank(skillId)) {
            return EmakiResult.invalidInput("skills.skill.id_required");
        }
        if (definition(skillId) == null) {
            return EmakiResult.notFound("skills.skill.not_found");
        }
        return EmakiResult.success(plugin.manualSkillSourceService().hasLearned(player, skillId));
    }

    @Override
    public @NotNull EmakiResult<Boolean> castModeEnabled(@Nullable Player player) {
        EmakiResult<Boolean> guard = guardPlayer(player);
        return guard != null ? guard : EmakiResult.success(plugin.castModeService().isCastModeEnabled(player));
    }

    @Override
    public @NotNull EmakiResult<Boolean> atMaxLevel(@Nullable Player player, @Nullable String skillId) {
        EmakiResult<Boolean> guard = guardPlayer(player);
        if (guard != null) {
            return guard;
        }
        SkillDefinition definition = definition(skillId);
        return definition == null
                ? invalidOrMissing(skillId, "skills.skill.not_found")
                : EmakiResult.success(plugin.skillLevelService().isMaxLevel(player, definition));
    }

    private <T> EmakiResult<T> guardPlayer(Player player) {
        if (!plugin.isEnabled() || plugin.playerSkillDataStore() == null) {
            return EmakiResult.unavailable();
        }
        if (player == null) {
            return EmakiResult.invalidInput("skills.player.required");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        return plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)
                ? null
                : EmakiResult.wrongThread();
    }

    private SkillDefinition definition(String skillId) {
        return Texts.isBlank(skillId) || plugin.skillRegistryService() == null
                ? null
                : plugin.skillRegistryService().getDefinition(skillId);
    }

    private static <T> EmakiResult<T> invalidOrMissing(String id, String missingKey) {
        return Texts.isBlank(id) ? EmakiResult.invalidInput("skills.skill.id_required") : EmakiResult.notFound(missingKey);
    }

    static SkillUpgradePreview toPreview(SkillUpgradeService.UpgradePreview preview) {
        return new SkillUpgradePreview(
                preview.definition().id(),
                preview.currentLevel(),
                preview.targetLevel(),
                preview.maxLevel(),
                preview.successRate(),
                preview.currencies().stream().map(cost -> new SkillUpgradePreview.CurrencyCost(
                        cost.provider(), cost.currencyId(), cost.amount(), cost.displayName())).toList(),
                preview.materials().stream().map(cost -> new SkillUpgradePreview.MaterialCost(
                        cost.item(), cost.amount(), cost.displayName())).toList(),
                preview.parameters().values());
    }

    static SkillDefinitionView toView(SkillDefinition definition) {
        return new SkillDefinitionView(
                definition.id(), definition.displayName(), definition.description(),
                definition.activationType().name(), definition.cooldownTicks(),
                definition.upgrade() == null ? 1 : definition.upgrade().maxLevel(),
                definition.enabled(), definition.tags(), definition.showInSlots(),
                definition.uiCategory(), definition.sortOrder());
    }
}
