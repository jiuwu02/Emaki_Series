package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.model.StrengthenMaterial;
import emaki.jiuwu.craft.strengthen.model.StrengthenProfile;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenAttemptService implements EmakiStrengthenApi {

    private final EmakiStrengthenPlugin plugin;
    private final ProfileResolver profileResolver;
    private final ChanceCalculator chanceCalculator;
    private final StrengthenSnapshotBuilder snapshotBuilder;
    private final StrengthenActionCoordinator actionCoordinator;

    public StrengthenAttemptService(EmakiStrengthenPlugin plugin,
            ProfileResolver profileResolver,
            ChanceCalculator chanceCalculator,
            StrengthenSnapshotBuilder snapshotBuilder,
            StrengthenActionCoordinator actionCoordinator) {
        this.plugin = plugin;
        this.profileResolver = profileResolver;
        this.chanceCalculator = chanceCalculator;
        this.snapshotBuilder = snapshotBuilder;
        this.actionCoordinator = actionCoordinator;
    }

    @Override
    public boolean canStrengthen(ItemStack itemStack) {
        return readState(itemStack).eligible();
    }

    @Override
    public StrengthenState readState(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return StrengthenState.ineligible("strengthen.error.no_target", null, "");
        }
        ProfileResolver.ResolvedItem resolved = profileResolver.resolve(itemStack, readStoredProfileId(itemStack));
        if (resolved.baseSource() == null) {
            return StrengthenState.ineligible("strengthen.error.no_source", null, "");
        }
        StoredState stored = readStoredState(itemStack, resolved.baseSource(), resolved.baseSourceSignature());
        String profileId = Texts.isNotBlank(stored.profileId()) ? stored.profileId() : resolved.profileId();
        boolean eligible = Texts.isNotBlank(profileId) && plugin.profileLoader().get(profileId) != null;
        String reason = eligible ? "" : "strengthen.error.no_profile";
        return new StrengthenState(
                eligible,
                reason,
                stored.hasLayer(),
                resolved.baseSource(),
                resolved.baseSourceSignature(),
                profileId,
                stored.currentStar(),
                stored.crackLevel(),
                stored.milestoneFlags(),
                stored.successCount(),
                stored.failureCount(),
                stored.lastAttemptAt()
        );
    }

    @Override
    public AttemptPreview preview(Player player, AttemptContext context) {
        ItemStack targetItem = context == null ? null : context.targetItem();
        StrengthenState state = readState(targetItem);
        if (!state.eligible()) {
            return new AttemptPreview(false, state.eligibleReason(), state, null, state.currentStar(), state.currentStar(), 0D, 0L,
                    state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), null, null, null, null);
        }
        StrengthenProfile profile = plugin.profileLoader().get(state.profileId());
        if (profile == null) {
            return new AttemptPreview(false, "strengthen.error.no_profile", state, null, state.currentStar(), state.currentStar(), 0D, 0L,
                    state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), null, null, null, null);
        }
        if (state.currentStar() >= plugin.appConfig().maxStar()) {
            return new AttemptPreview(false, "strengthen.error.already_max", state, profile, state.currentStar(), state.currentStar(), 0D, 0L,
                    state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), null, null, null, null);
        }
        MaterialSelection selection = resolveMaterials(context);
        if (selection.baseMaterial() == null) {
            return new AttemptPreview(false, selection.errorKey().isBlank() ? "strengthen.error.base_required" : selection.errorKey(), state, profile,
                    state.currentStar(), state.currentStar() + 1, 0D, 0L, state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(),
                    null, selection.supportMaterial(), selection.protectionMaterial(), selection.breakthroughMaterial());
        }

        int targetStar = state.currentStar() + 1;
        int requiredBreakthroughTier = requiredBreakthroughTier(targetStar);
        if (requiredBreakthroughTier > 0) {
            if (selection.breakthroughMaterial() == null || selection.breakthroughMaterial().requiredFromTargetStar() != requiredBreakthroughTier) {
                return new AttemptPreview(false, "strengthen.error.breakthrough_required", state, profile, state.currentStar(), targetStar, 0D, 0L,
                        state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), selection.baseMaterial(), selection.supportMaterial(),
                        selection.protectionMaterial(), selection.breakthroughMaterial());
            }
        } else if (selection.breakthroughMaterial() != null) {
            return new AttemptPreview(false, "strengthen.error.invalid_breakthrough_material", state, profile, state.currentStar(), targetStar, 0D, 0L,
                    state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), selection.baseMaterial(), selection.supportMaterial(),
                    selection.protectionMaterial(), selection.breakthroughMaterial());
        }

        boolean protectionApplied = selection.protectionMaterial() != null && targetStar >= selection.protectionMaterial().protectionMinTargetStar();
        if (selection.protectionMaterial() != null && !protectionApplied) {
            return new AttemptPreview(false, "strengthen.error.invalid_protection_material", state, profile, state.currentStar(), targetStar, 0D, 0L,
                    state.currentStar(), state.crackLevel(), false, Map.of(), Set.of(), selection.baseMaterial(), selection.supportMaterial(),
                    selection.protectionMaterial(), selection.breakthroughMaterial());
        }

        double successRate = chanceCalculator.calculateSuccessRate(plugin.appConfig(), state.currentStar(), state.crackLevel(), selection.supportMaterial());
        ChanceCalculator.FailureResolution failure = chanceCalculator.resolveFailure(plugin.appConfig(), state.currentStar(), state.crackLevel(), protectionApplied);
        long cost = chanceCalculator.calculateCurrencyCost(profile, targetStar);
        Set<Integer> unlockingMilestones = new LinkedHashSet<>();
        for (StrengthenProfile.Milestone milestone : profile.reachedMilestones(targetStar)) {
            if (milestone.star() > state.currentStar()) {
                unlockingMilestones.add(milestone.star());
            }
        }
        return new AttemptPreview(true, "", state, profile, state.currentStar(), targetStar, successRate, cost,
                failure.resultingStar(), failure.resultingCrack(), protectionApplied,
                profile.deltaStats(state.currentStar(), targetStar), unlockingMilestones,
                selection.baseMaterial(), selection.supportMaterial(), selection.protectionMaterial(), selection.breakthroughMaterial());
    }

    @Override
    public AttemptResult attempt(Player player, AttemptContext context) {
        AttemptPreview preview = preview(player, context);
        if (!preview.eligible()) {
            return AttemptResult.failure(preview.errorKey(), preview, replacements(preview, preview.currentStar(), preview.currencyCost()));
        }
        ActionResult economyResult = removeCurrency(player, preview.currencyCost());
        if (!economyResult.success()) {
            String key = economyResult.errorType() == ActionErrorType.INSUFFICIENT_BALANCE
                    ? "strengthen.error.insufficient_funds"
                    : "strengthen.error.economy_provider_unavailable";
            return AttemptResult.failure(key, preview, replacements(preview, preview.currentStar(), preview.currencyCost()));
        }

        boolean success = ThreadLocalRandom.current().nextDouble(100D) < preview.successRate();
        StrengthenState currentState = preview.state();
        int star = success ? preview.targetStar() : preview.failureStar();
        int crack = success ? 0 : preview.failureCrack();
        MilestoneProgress milestoneProgress = collectMilestoneProgress(
                currentState.milestoneFlags(),
                success ? preview.unlockingMilestones() : Set.of()
        );

        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                currentState.baseSource(),
                currentState.baseSourceSignature(),
                preview.profile().id(),
                star,
                crack,
                milestoneProgress.updatedFlags(),
                currentState.successCount() + (success ? 1 : 0),
                currentState.failureCount() + (success ? 0 : 1),
                System.currentTimeMillis()
        );
        ItemStack rebuilt = rebuildWithState(context == null ? null : context.targetItem(), updated, buildMaterialsSignature(preview));
        if (rebuilt == null) {
            refundCurrency(player, preview.currencyCost());
            return AttemptResult.failure("strengthen.error.rebuild_failed", preview, replacements(preview, star, preview.currencyCost()));
        }

        if (success && player != null) {
            actionCoordinator.triggerMilestoneActions(player, preview.profile(), milestoneProgress.newlyReached(), rebuilt, star);
            broadcastFirstReach(player, rebuilt, milestoneProgress.newlyReached());
        }
        return new AttemptResult(success, "", replacements(preview, star, preview.currencyCost()), preview, rebuilt, star, crack);
    }

    @Override
    public ItemStack rebuild(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        StrengthenState state = readState(itemStack);
        if (!state.hasLayer() || Texts.isBlank(state.profileId())) {
            return itemStack;
        }
        return rebuildWithState(itemStack, state, readStoredMaterialsSignature(itemStack));
    }

    public ItemStack applyAdminState(ItemStack itemStack, Integer star, Integer crack, String profileId) {
        StrengthenState current = readState(itemStack);
        if (current.baseSource() == null) {
            return null;
        }
        String effectiveProfile = Texts.isNotBlank(profileId) ? profileId : current.profileId();
        if (plugin.profileLoader().get(effectiveProfile) == null) {
            return null;
        }
        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                current.baseSource(),
                current.baseSourceSignature(),
                effectiveProfile,
                star == null ? current.currentStar() : Numbers.clamp(star, 0, plugin.appConfig().maxStar()),
                crack == null ? current.crackLevel() : Numbers.clamp(crack, 0, plugin.appConfig().maxCrack()),
                current.milestoneFlags(),
                current.successCount(),
                current.failureCount(),
                System.currentTimeMillis()
        );
        return rebuildWithState(itemStack, updated, readStoredMaterialsSignature(itemStack));
    }

    public ItemStack consumeCleanseMaterial(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return null;
        }
        StrengthenState state = readState(itemStack);
        if (!state.hasLayer() || state.crackLevel() <= 0) {
            return null;
        }
        MaterialSlot found = findInventoryMaterial(player.getInventory(), StrengthenMaterial.Role.CLEANSE);
        if (found == null || found.material() == null || found.material().crackRemove() <= 0) {
            return null;
        }
        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                state.baseSource(),
                state.baseSourceSignature(),
                state.profileId(),
                state.currentStar(),
                Numbers.clamp(state.crackLevel() - found.material().crackRemove(), 0, plugin.appConfig().maxCrack()),
                state.milestoneFlags(),
                state.successCount(),
                state.failureCount(),
                System.currentTimeMillis()
        );
        ItemStack rebuilt = rebuildWithState(itemStack, updated, readStoredMaterialsSignature(itemStack));
        if (rebuilt == null) {
            return null;
        }
        ItemStack stack = player.getInventory().getItem(found.slot());
        if (stack != null) {
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(found.slot(), null);
            } else {
                player.getInventory().setItem(found.slot(), stack);
            }
        }
        return rebuilt;
    }

    public StrengthenMaterial resolveConfiguredMaterial(ItemStack itemStack) {
        return findMaterial(itemStack);
    }

    private String readStoredProfileId(ItemStack itemStack) {
        StoredState state = readStoredState(itemStack, profileResolver.resolveBaseSource(itemStack), "");
        return state.profileId();
    }

    private StoredState readStoredState(ItemStack itemStack, ItemSource baseSource, String fallbackSignature) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || itemStack == null || !coreLib.itemAssemblyService().isEmakiItem(itemStack)) {
            String signature = Texts.isBlank(fallbackSignature) ? ItemSourceUtil.toShorthand(baseSource) : fallbackSignature;
            return new StoredState(false, "", 0, 0, Set.of(), 0, 0, 0L, "", signature);
        }
        EmakiItemLayerSnapshot snapshot = coreLib.itemAssemblyService().readLayerSnapshot(itemStack, "strengthen");
        if (snapshot == null) {
            String signature = Texts.isBlank(fallbackSignature) ? ItemSourceUtil.toShorthand(baseSource) : fallbackSignature;
            return new StoredState(false, "", 0, 0, Set.of(), 0, 0, 0L, "", signature);
        }
        Map<String, Object> audit = snapshot.audit();
        return new StoredState(
                true,
                Texts.toStringSafe(audit.get("profile_id")),
                Numbers.tryParseInt(audit.get("current_star"), 0),
                Numbers.tryParseInt(audit.get("crack_level"), 0),
                parseFlagSet(audit.get("first_reach_flags")),
                Numbers.tryParseInt(audit.get("success_count"), 0),
                Numbers.tryParseInt(audit.get("failure_count"), 0),
                Numbers.tryParseLong(audit.get("last_attempt_at"), 0L),
                Texts.toStringSafe(audit.get("materials_signature")),
                Texts.isBlank(Texts.toStringSafe(audit.get("base_source_signature")))
                        ? fallbackSignature
                        : Texts.toStringSafe(audit.get("base_source_signature"))
        );
    }

    private Set<Integer> parseFlagSet(Object raw) {
        Set<Integer> flags = new LinkedHashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                Integer value = Numbers.tryParseInt(entry, null);
                if (value != null) {
                    flags.add(value);
                }
            }
        } else if (raw != null) {
            Integer value = Numbers.tryParseInt(raw, null);
            if (value != null) {
                flags.add(value);
            }
        }
        return flags;
    }

    private MaterialSelection resolveMaterials(AttemptContext context) {
        StrengthenMaterial base = findMaterial(context == null ? null : context.baseMaterial());
        StrengthenMaterial support = findMaterial(context == null ? null : context.supportMaterial());
        StrengthenMaterial protection = findMaterial(context == null ? null : context.protectionMaterial());
        StrengthenMaterial breakthrough = findMaterial(context == null ? null : context.breakthroughMaterial());
        if (context != null && context.baseMaterial() != null && (base == null || base.role() != StrengthenMaterial.Role.BASE)) {
            return new MaterialSelection(null, support, protection, breakthrough, "strengthen.error.invalid_base_material");
        }
        if (support != null && support.role() != StrengthenMaterial.Role.SUPPORT) {
            return new MaterialSelection(base, null, protection, breakthrough, "strengthen.error.invalid_support_material");
        }
        if (protection != null && protection.role() != StrengthenMaterial.Role.PROTECTION) {
            return new MaterialSelection(base, support, null, breakthrough, "strengthen.error.invalid_protection_material");
        }
        if (breakthrough != null && breakthrough.role() != StrengthenMaterial.Role.BREAKTHROUGH) {
            return new MaterialSelection(base, support, protection, null, "strengthen.error.invalid_breakthrough_material");
        }
        return new MaterialSelection(base, support, protection, breakthrough, "");
    }

    private StrengthenMaterial findMaterial(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        ItemSource source = profileResolver.resolveBaseSource(itemStack);
        if (source == null) {
            return null;
        }
        for (StrengthenMaterial material : plugin.materialLoader().all().values()) {
            if (material != null && ItemSourceUtil.matches(source, material.source())) {
                return material;
            }
        }
        return null;
    }

    private int requiredBreakthroughTier(int targetStar) {
        int required = 0;
        for (StrengthenMaterial material : plugin.materialLoader().all().values()) {
            if (material == null || material.role() != StrengthenMaterial.Role.BREAKTHROUGH) {
                continue;
            }
            if (material.requiredFromTargetStar() <= targetStar) {
                required = Math.max(required, material.requiredFromTargetStar());
            }
        }
        return required;
    }

    private ActionResult removeCurrency(Player player, long cost) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Player is required.");
        }
        if (coreLib == null || coreLib.economyManager() == null) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "CoreLib economy is unavailable.");
        }
        return coreLib.economyManager().remove(player, plugin.appConfig().economyProvider(), plugin.appConfig().economyCurrencyId(), cost);
    }

    private void refundCurrency(Player player, long cost) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (player == null || cost <= 0L || coreLib == null || coreLib.economyManager() == null) {
            return;
        }
        coreLib.economyManager().add(player, plugin.appConfig().economyProvider(), plugin.appConfig().economyCurrencyId(), cost);
    }

    private ItemStack rebuildWithState(ItemStack itemStack, StrengthenState state, String materialsSignature) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        StrengthenProfile profile = plugin.profileLoader().get(state.profileId());
        if (profile == null) {
            return null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null) {
            return null;
        }
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.buildLayerSnapshot(profile, state, materialsSignature);
        ItemStack rebuilt = coreLib.itemAssemblyService().preview(new EmakiItemAssemblyRequest(
                state.baseSource(),
                Math.max(1, itemStack.getAmount()),
                itemStack,
                List.of(snapshot)
        ));
        if (rebuilt != null) {
            rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
        }
        return rebuilt;
    }

    private String buildMaterialsSignature(AttemptPreview preview) {
        List<Object> signatureData = new ArrayList<>();
        appendMaterial(signatureData, preview.baseMaterial());
        appendMaterial(signatureData, preview.supportMaterial());
        appendMaterial(signatureData, preview.protectionMaterial());
        appendMaterial(signatureData, preview.breakthroughMaterial());
        return SignatureUtil.stableSignature(signatureData);
    }

    private void appendMaterial(List<Object> target, StrengthenMaterial material) {
        if (target != null && material != null) {
            target.add(Map.of("id", material.id(), "role", material.role().name()));
        }
    }

    private String readStoredMaterialsSignature(ItemStack itemStack) {
        StoredState state = readStoredState(itemStack, profileResolver.resolveBaseSource(itemStack), "");
        return state.materialsSignature();
    }

    private Map<String, Object> replacements(AttemptPreview preview, int star, long cost) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("star", star);
        replacements.put("cost", cost);
        replacements.put("currency", plugin.appConfig().economyCurrencyName());
        if (preview != null && preview.profile() != null) {
            replacements.put("profile", preview.profile().displayName());
        }
        return replacements;
    }

    private void broadcastFirstReach(Player player, ItemStack resultItem, Set<Integer> newlyReached) {
        if (player == null || newlyReached == null || newlyReached.isEmpty()) {
            return;
        }
        String showItem = actionCoordinator.buildShowItem(resultItem);
        if (newlyReached.contains(8)) {
            String message = plugin.messageService().message("strengthen.broadcast.local_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", 8
            ));
            double radius = plugin.appConfig().localBroadcastRadius();
            Bukkit.getOnlinePlayers().stream()
                    .filter(viewer -> viewer.getWorld().equals(player.getWorld()) && viewer.getLocation().distanceSquared(player.getLocation()) <= radius * radius)
                    .forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
        for (int star : List.of(10, 12)) {
            if (!newlyReached.contains(star)) {
                continue;
            }
            String message = plugin.messageService().message("strengthen.broadcast.global_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", star
            ));
            Bukkit.getOnlinePlayers().forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
    }

    private MaterialSlot findInventoryMaterial(PlayerInventory inventory, StrengthenMaterial.Role role) {
        if (inventory == null || role == null) {
            return null;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            StrengthenMaterial material = findMaterial(inventory.getItem(slot));
            if (material != null && material.role() == role) {
                return new MaterialSlot(slot, material);
            }
        }
        return null;
    }

    private record MaterialSelection(StrengthenMaterial baseMaterial,
            StrengthenMaterial supportMaterial,
            StrengthenMaterial protectionMaterial,
            StrengthenMaterial breakthroughMaterial,
            String errorKey) {

    }

    private record MaterialSlot(int slot, StrengthenMaterial material) {

    }

    static MilestoneProgress collectMilestoneProgress(Set<Integer> currentFlags, Set<Integer> reachedNow) {
        Set<Integer> updated = new LinkedHashSet<>(currentFlags == null ? Set.of() : currentFlags);
        Set<Integer> newlyReached = new LinkedHashSet<>();
        if (reachedNow != null) {
            for (Integer milestone : reachedNow) {
                if (milestone != null && updated.add(milestone)) {
                    newlyReached.add(milestone);
                }
            }
        }
        return new MilestoneProgress(Set.copyOf(updated), Set.copyOf(newlyReached));
    }

    private record StoredState(boolean hasLayer,
            String profileId,
            int currentStar,
            int crackLevel,
            Set<Integer> milestoneFlags,
            int successCount,
            int failureCount,
            long lastAttemptAt,
            String materialsSignature,
            String baseSourceSignature) {

    }

    record MilestoneProgress(Set<Integer> updatedFlags, Set<Integer> newlyReached) {

    }
}
