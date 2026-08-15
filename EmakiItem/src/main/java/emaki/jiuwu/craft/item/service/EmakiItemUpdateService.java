package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;
import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;

public final class EmakiItemUpdateService {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemIdResolver idResolver;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGatewayAdapter attributeGateway;
    private final EmakiItemAssemblyService assemblyService;
    private final ItemOperationLedger operationLedger;
    private final PdcService pdcService;
    private final PdcPartition itemPartition;

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway) {
        this(itemLoader, idResolver, itemFactory, identifier, attributeGateway, null);
    }

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway,
            EmakiItemAssemblyService assemblyService) {
        this(itemLoader, idResolver, itemFactory, identifier, attributeGateway, assemblyService, null);
    }

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway,
            EmakiItemAssemblyService assemblyService,
            DebugLogger debugLogger) {
        this.itemLoader = itemLoader;
        this.idResolver = idResolver;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.assemblyService = assemblyService;
        this.operationLedger = new ItemOperationLedger(debugLogger);
        this.pdcService = new PdcService("emaki", "pdc", debugLogger);
        this.itemPartition = pdcService.partition("item");
    }

    public ItemStack updateIfNeeded(ItemStack original, String trigger) {
        return updateIfNeeded(original, List.of(Texts.toStringSafe(trigger)));
    }

    public ItemStack updateIfNeeded(ItemStack original,
                                    String trigger,
                                    ItemOperationLedger.ReadResult readResult) {
        return updateIfNeeded(original, List.of(Texts.toStringSafe(trigger)), readResult);
    }

    public ItemStack updateIfNeeded(ItemStack original, Iterable<String> triggers) {
        return updateIfNeeded(original, orderedTriggers(triggers), itemLoader.snapshot(), null).itemStack();
    }

    public ItemStack updateIfNeeded(ItemStack original,
                                    Iterable<String> triggers,
                                    ItemOperationLedger.ReadResult readResult) {
        return updateIfNeeded(original, orderedTriggers(triggers), itemLoader.snapshot(), readResult).itemStack();
    }

    private UpdateOutcome updateIfNeeded(ItemStack original,
            List<String> triggers,
            EmakiItemLoader.Snapshot definitions,
            ItemOperationLedger.ReadResult suppliedReadResult) {
        if (original == null || original.getType().isAir()) {
            return UpdateOutcome.ignored(original);
        }
        String id = identifier.identify(original);
        if (id.isBlank()) {
            return UpdateOutcome.ignored(original);
        }
        ItemOperationLedger.ReadResult readResult = suppliedReadResult == null
                ? operationLedger.read(original)
                : suppliedReadResult;
        if (readResult.corrupt()) {
            String effectiveTrigger = triggers.isEmpty() ? "" : triggers.getFirst();
            return UpdateOutcome.invalid(original, effectiveTrigger);
        }
        EmakiItemAlias alias = idResolver == null ? null : idResolver.aliasFor(id);
        if (alias != null) {
            String effectiveTrigger = triggers.isEmpty() ? "" : triggers.getFirst();
            return new UpdateOutcome(migrateAlias(original, id, alias, definitions, readResult), effectiveTrigger, true, true);
        }
        EmakiItemDefinition definition = definitions.get(id);
        if (definition == null && idResolver != null) {
            definition = idResolver.resolveDefinition(id);
        }
        if (definition == null) {
            return UpdateOutcome.ignored(original);
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        String effectiveTrigger = updateConfig.effectiveTrigger(triggers);
        if (effectiveTrigger == null) {
            return UpdateOutcome.ignored(original);
        }
        if (identifier.updateVersion(original) >= definition.updatePolicy().version()) {
            return new UpdateOutcome(original, effectiveTrigger, true, true);
        }
        return new UpdateOutcome(
                rebuild(original, definition, updateConfig, definition.id(), readResult),
                effectiveTrigger,
                true,
                true
        );
    }

    public ItemStack forceUpdate(ItemStack original) {
        return forceUpdate(original, null);
    }

    public ItemStack forceUpdate(ItemStack original, ItemOperationLedger.ReadResult suppliedReadResult) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        String id = identifier.identify(original);
        if (Texts.isBlank(id)) {
            return original;
        }
        ItemOperationLedger.ReadResult readResult = suppliedReadResult == null
                ? operationLedger.read(original)
                : suppliedReadResult;
        if (readResult.corrupt()) {
            return original;
        }
        EmakiItemAlias alias = idResolver == null ? null : idResolver.aliasFor(id);
        if (alias != null) {
            return migrateAlias(original, id, alias, itemLoader.snapshot(), readResult);
        }
        EmakiItemDefinition definition = idResolver == null ? itemLoader.get(id) : idResolver.resolveDefinition(id);
        if (definition == null) {
            return original;
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        return updateConfig.enabled()
                ? rebuild(original, definition, updateConfig, definition.id(), readResult)
                : original;
    }

    public int updatePlayerItems(Player player, String trigger) {
        return updatePlayerItemsDetailed(player, List.of(Texts.toStringSafe(trigger)), Set.of(), true, Set.of()).changed();
    }

    public int updatePlayerItems(Player player, String trigger, Set<Integer> dirtySlots) {
        return updatePlayerItemsDetailed(player, List.of(Texts.toStringSafe(trigger)), dirtySlots, false, Set.of()).changed();
    }

    public ItemRefreshResult updatePlayerItemsDetailed(Player player,
            Iterable<String> triggers,
            Set<Integer> dirtySlots,
            boolean forceFull,
            Set<RefreshFullReason> requestedFullReasons) {
        return updatePlayerItemsDetailed(
                player,
                triggers,
                dirtySlots,
                forceFull,
                requestedFullReasons,
                null);
    }

    public ItemRefreshResult updatePlayerItemsDetailed(Player player,
            Iterable<String> triggers,
            Set<Integer> dirtySlots,
            boolean forceFull,
            Set<RefreshFullReason> requestedFullReasons,
            ItemRefreshBatch sharedBatch) {
        long started = System.nanoTime();
        RefreshScope requestedScope = forceFull ? RefreshScope.FULL
                : dirtySlots == null || dirtySlots.isEmpty() ? RefreshScope.SKIP : RefreshScope.LOCAL;
        if (player == null || requestedScope == RefreshScope.SKIP) {
            return new ItemRefreshResult(requestedScope, RefreshScope.SKIP, RefreshScope.SKIP,
                    requestedFullReasons, false, true, 0, 0, 0, 0, 0, 0, "", System.nanoTime() - started);
        }
        List<String> orderedTriggers = orderedTriggers(triggers);
        PlayerInventory inventory = player.getInventory();
        ItemRefreshBatch refreshBatch = sharedBatch != null && sharedBatch.matches(inventory)
                ? sharedBatch
                : new ItemRefreshBatch(inventory, operationLedger);
        int ledgerDecodesBefore = refreshBatch.ledgerDecodes();
        TreeSet<Integer> slots = new TreeSet<>();
        if (forceFull) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                slots.add(slot);
            }
        } else {
            dirtySlots.stream().filter(Objects::nonNull).forEach(slots::add);
        }
        EmakiItemLoader.Snapshot definitions = itemLoader.snapshot();
        int scanned = 0;
        int changed = 0;
        int conflicts = 0;
        boolean cacheValid = true;
        boolean considered = false;
        String effectiveTrigger = "";
        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            scanned++;
            SlotUpdateResult slotResult = updateInventorySlot(
                    inventory, slot, orderedTriggers, definitions, refreshBatch);
            changed += slotResult.changed();
            conflicts += slotResult.conflict() ? 1 : 0;
            cacheValid &= slotResult.cacheValid();
            considered |= slotResult.considered();
            if (Texts.isBlank(effectiveTrigger) && Texts.isNotBlank(slotResult.effectiveTrigger())) {
                effectiveTrigger = slotResult.effectiveTrigger();
            }
        }
        RefreshScope actualScope = considered ? requestedScope : RefreshScope.SKIP;
        LinkedHashSet<RefreshFullReason> reasons = new LinkedHashSet<>();
        if (requestedFullReasons != null) {
            reasons.addAll(requestedFullReasons);
        }
        if (conflicts > 0) {
            reasons.add(RefreshFullReason.COMPARE_CONFLICT);
        }
        if (!cacheValid) {
            reasons.add(RefreshFullReason.CACHE_INVALID);
        }
        int ledgerDecodes = refreshBatch.ledgerDecodes() - ledgerDecodesBefore;
        return new ItemRefreshResult(requestedScope, actualScope, RefreshScope.SKIP, reasons,
                false, cacheValid && conflicts == 0, scanned, 0, changed, conflicts, ledgerDecodes, 0,
                effectiveTrigger, System.nanoTime() - started);
    }

    private SlotUpdateResult updateInventorySlot(PlayerInventory inventory,
            int slot,
            List<String> triggers,
            EmakiItemLoader.Snapshot definitions,
            ItemRefreshBatch refreshBatch) {
        ItemRefreshBatch.SlotSnapshot slotSnapshot = refreshBatch.capture(slot);
        ItemStack snapshot = slotSnapshot == null ? null : slotSnapshot.expected();
        ItemOperationLedger.ReadResult readResult = slotSnapshot == null
                ? ItemOperationLedger.ReadResult.absent()
                : slotSnapshot.ledgerRead();
        UpdateOutcome outcome = updateIfNeeded(snapshot, triggers, definitions, readResult);
        ItemStack updated = outcome.itemStack();
        if (sameItem(snapshot, updated)) {
            return new SlotUpdateResult(0, false, outcome.considered(), outcome.cacheValid(),
                    outcome.effectiveTrigger());
        }
        if (!sameItem(inventory.getItem(slot), snapshot)) {
            refreshBatch.recapture(slot);
            return new SlotUpdateResult(0, true, outcome.considered(), false,
                    outcome.effectiveTrigger());
        }
        inventory.setItem(slot, updated);
        refreshBatch.recapture(slot);
        return new SlotUpdateResult(1, false, outcome.considered(), outcome.cacheValid(),
                outcome.effectiveTrigger());
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || first.getType().isAir()) {
            return second == null || second.getType().isAir();
        }
        return first.equals(second);
    }

    private ItemStack migrateAlias(ItemStack original,
            String currentId,
            EmakiItemAlias alias,
            EmakiItemLoader.Snapshot definitions,
            ItemOperationLedger.ReadResult readResult) {
        if (alias == null) {
            return original;
        }
        EmakiItemDefinition definition = definitions == null ? itemLoader.get(alias.targetId()) : definitions.get(alias.targetId());
        if (definition == null) {
            return original;
        }
        boolean migratePdc = alias.migratePdc();
        boolean rewriteDisplay = alias.rewriteDisplay();
        if (!migratePdc && !rewriteDisplay) {
            return original;
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        String identityId = migratePdc ? definition.id() : currentId;
        if (rewriteDisplay) {
            return rebuild(original, definition, updateConfig, identityId, readResult);
        }
        ItemStack migrated = original.clone();
        writeIdentity(migrated, definition, identityId);
        return migrated;
    }

    private ItemStack rebuild(ItemStack original,
            EmakiItemDefinition definition,
            ItemUpdateConfig updateConfig,
            String identityId,
            ItemOperationLedger.ReadResult readResult) {
        int amount = updateConfig.preserveAmount() ? original.getAmount() : 1;
        int oldDamage = readDamage(original);
        EmakiItemFactory.PreparedBuild prepared = itemFactory.prepareBuild(definition);
        if (prepared == null) {
            return original;
        }
        MergeResult merged = mergeAssemblyAndLedger(original, prepared, amount, readResult);
        if (merged == null || merged.itemStack() == null) {
            return original;
        }
        EmakiItemFactory.FinishedBuild finished = itemFactory.finishBuild(
                merged.itemStack(), definition, prepared.variables(), merged.readResult());
        if (!finished.success() || finished.itemStack() == null) {
            return original;
        }
        ItemStack rebuilt = finished.itemStack();
        rebuilt.setAmount(Math.max(1, Math.min(amount, rebuilt.getMaxStackSize())));
        if (!definition.id().equals(identityId)) {
            writeIdentity(rebuilt, definition, identityId);
        }
        if (updateConfig.preserveDamage()) {
            applyDamage(rebuilt, oldDamage);
        }
        if (updateConfig.preserveUnknownAttributeSources()) {
            attributeGateway.copyPayloads(original, rebuilt, Set.of("emakiitem", EmakiItemPdcWriter.SET_ATTRIBUTE_SOURCE_ID));
        }
        return rebuilt;
    }

    private MergeResult mergeAssemblyAndLedger(ItemStack original,
            EmakiItemFactory.PreparedBuild prepared,
            int amount,
            ItemOperationLedger.ReadResult readResult) {
        if (readResult == null || readResult.corrupt()) {
            return null;
        }
        ItemStack rebuiltBase = prepared.itemStack().clone();
        boolean hasAssemblyData = hasAssemblyData(original);
        boolean canReplayAssembly = assemblyService != null && assemblyService.isEmakiItem(original);
        boolean hasLedger = readResult.status() == ItemOperationLedger.ReadStatus.VALID;
        if (!canReplayAssembly) {
            if (hasAssemblyData || hasLedger) {
                copyPersistentData(original, rebuiltBase, false);
            }
            return new MergeResult(rebuiltBase, readResult);
        }

        ItemStack assemblyState = original.clone();

        ItemOperationLedger.UpdateResult assemblyRevert = operationLedger.discardNamespaces(
                assemblyState, readResult, EmakiItemFactory.OWNED_DISPLAY_NAMESPACES);
        if (!assemblyRevert.success() || assemblyRevert.entries().stream().anyMatch(entry -> entry != null
                && EmakiItemFactory.OWNED_DISPLAY_NAMESPACES.contains(entry.sourceNamespace()))) {
            return null;
        }
        writeAssemblyBasePresentation(assemblyState, rebuiltBase);
        ItemSourceRef source = ItemSourceUtil.parse(prepared.itemDefinition().source());
        ItemStack assembled = assemblyService.preview(
                new EmakiItemAssemblyRequest(source, amount, assemblyState, List.of()),
                assemblyRevert.readResult()
        );
        if (assembled == null) {
            copyPersistentData(original, rebuiltBase, false);
            return new MergeResult(rebuiltBase, readResult);
        }

        ConfiguredItemDefinition nonPresentation = withoutPresentationPatches(prepared.itemDefinition());
        ItemBuildResult patched = EmakiCoreLibApi.applyConfiguredItem(assembled, nonPresentation);
        ItemStack merged = patched.success() && patched.itemStack() != null ? patched.itemStack() : assembled;
        copyPersistentData(original, merged, false);
        return new MergeResult(merged, readResult);
    }

    private ConfiguredItemDefinition withoutPresentationPatches(ConfiguredItemDefinition definition) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        definition.components().forEach((componentId, patch) -> {
            if (!"minecraft:custom_name".equals(componentId) && !"minecraft:lore".equals(componentId)) {
                patches.put(componentId, patch);
            }
        });
        return new ConfiguredItemDefinition(null, 1, patches);
    }

    private boolean hasAssemblyData(ItemStack itemStack) {
        return pdcService.has(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER)
                && pdcService.has(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
    }

    private void writeAssemblyBasePresentation(ItemStack assemblyState, ItemStack rebuiltBase) {
        ItemMeta itemMeta = rebuiltBase == null ? null : rebuiltBase.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (ItemTextBridge.hasCustomName(itemMeta)) {
            pdcService.set(assemblyState, itemPartition, "base_custom_name", PersistentDataType.STRING,
                    MiniMessages.serialize(ItemTextBridge.customName(itemMeta)));
        } else {
            pdcService.remove(assemblyState, itemPartition, "base_custom_name");
        }
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        if (lore == null || lore.isEmpty()) {
            pdcService.remove(assemblyState, itemPartition, "base_lore");
        } else {
            pdcService.set(assemblyState, itemPartition, "base_lore", PersistentDataType.STRING,
                    YamlFiles.dump(Map.of("lore", lore)));
        }
    }

    private void copyPersistentData(ItemStack from, ItemStack to, boolean replace) {
        if (from == null || to == null) {
            return;
        }
        ItemMeta fromMeta = from.getItemMeta();
        ItemMeta toMeta = to.getItemMeta();
        if (fromMeta == null || toMeta == null) {
            return;
        }
        fromMeta.getPersistentDataContainer().copyTo(toMeta.getPersistentDataContainer(), replace);
        to.setItemMeta(toMeta);
    }

    private void writeIdentity(ItemStack itemStack, EmakiItemDefinition definition, String identityId) {
        if (itemStack == null || definition == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Integer updateVersion = definition.updatePolicy().updateEnabled() ? definition.updatePolicy().version() : null;
        identifier.writeIdentity(itemMeta, identityId, definition.definitionSignature(), updateVersion);
        itemStack.setItemMeta(itemMeta);
    }

    private int readDamage(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta instanceof Damageable damageable ? Math.max(0, damageable.getDamage()) : 0;
    }

    private void applyDamage(ItemStack itemStack, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof Damageable damageable)) {
            return;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : Integer.MAX_VALUE;
        damageable.setDamage(Math.max(0, Math.min(damage, maxDamage)));
        itemStack.setItemMeta(itemMeta);
    }

    private List<String> orderedTriggers(Iterable<String> triggers) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (triggers != null) {
            for (String trigger : triggers) {
                String normalized = Texts.toStringSafe(trigger);
                if (Texts.isNotBlank(normalized)) {
                    ordered.add(normalized);
                }
            }
        }
        return ordered.isEmpty() ? List.of() : List.copyOf(ordered);
    }

    private record MergeResult(ItemStack itemStack, ItemOperationLedger.ReadResult readResult) {

        private MergeResult {
            readResult = readResult == null
                    ? ItemOperationLedger.ReadResult.corrupt(List.of())
                    : readResult;
        }
    }

    private record UpdateOutcome(ItemStack itemStack,
            String effectiveTrigger,
            boolean considered,
            boolean cacheValid) {

        private UpdateOutcome {
            effectiveTrigger = Texts.toStringSafe(effectiveTrigger);
        }

        private static UpdateOutcome ignored(ItemStack itemStack) {
            return new UpdateOutcome(itemStack, "", false, true);
        }

        private static UpdateOutcome invalid(ItemStack itemStack, String effectiveTrigger) {
            return new UpdateOutcome(itemStack, effectiveTrigger, true, false);
        }
    }

    private record SlotUpdateResult(int changed,
            boolean conflict,
            boolean considered,
            boolean cacheValid,
            String effectiveTrigger) {
    }

    public interface PdcAttributeGatewayAdapter {
        void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds);
    }
}
