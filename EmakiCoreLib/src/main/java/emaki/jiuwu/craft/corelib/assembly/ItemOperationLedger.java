package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class ItemOperationLedger {

    private static final PdcService PDC = new PdcService("emaki");
    private static final PdcPartition PARTITION = PDC.partition("item");
    private static final String FIELD = "operations";
    private static final String PRESENTATION_SNAPSHOT_FIELD = "presentation_snapshot";
    private static final NamespacedKey PRESENTATION_SNAPSHOT_KEY = PARTITION.key(PRESENTATION_SNAPSHOT_FIELD);
    private static final String ASSEMBLY_SCHEMA_VERSION_FIELD = "schema_version";
    private static final String ASSEMBLY_BASE_SOURCE_FIELD = "base_source";
    private static final String ASSEMBLY_BASE_CUSTOM_NAME_FIELD = "base_custom_name";
    static final String EXTERNAL_CUSTOM_NAME_FIELD = "external_custom_name";

    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final ItemOperationExecutor executor;
    private final ItemOperationReverter reverter;
    private final ItemOperationReplayer replayer;
    private final ItemLoreReconciler loreReconciler = new ItemLoreReconciler();

    public ItemOperationLedger() {
        this((Supplier<DebugLogger>) null);
    }

    public ItemOperationLedger(DebugLogger debugLogger) {
        this(() -> debugLogger);
    }

    public ItemOperationLedger(Supplier<DebugLogger> debugLoggerSupplier) {
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
        this.executor = new ItemOperationExecutor(this);
        this.reverter = new ItemOperationReverter(this);
        this.replayer = new ItemOperationReplayer();
    }

    public boolean apply(ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        return applyInternal(null, itemStack, operationId, sourceNamespace, nameActions, loreActions, variables);
    }

    public boolean apply(ActionContext context,
            ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        return applyInternal(context, itemStack, operationId, sourceNamespace, nameActions, loreActions, variables);
    }

    public boolean revert(ItemStack itemStack, String operationId) {
        return reverter.revert(itemStack, operationId).success();
    }

    public int revertAll(ItemStack itemStack, String sourceNamespace) {
        return reverter.revertAll(itemStack, sourceNamespace).revertedCount();
    }

    DebugLogger debugLogger() {
        return debugLoggerSupplier.get();
    }

    public List<ItemOperationEntry> readAll(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        String payload = PDC.get(itemStack, PARTITION, FIELD, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return List.of();
        }
        try {
            return normalizeEntries(ItemOperationCodec.decode(parsePayload(payload)));
        } catch (RuntimeException _) {
            return List.of();
        }
    }

    public ItemOperationEntry find(ItemStack itemStack, String operationId) {
        if (Texts.isBlank(operationId)) {
            return null;
        }
        for (ItemOperationEntry entry : readAll(itemStack)) {
            if (operationId.equals(entry.operationId())) {
                return entry;
            }
        }
        return null;
    }

    public List<ItemOperationEntry> findByNamespace(ItemStack itemStack, String sourceNamespace) {
        if (Texts.isBlank(sourceNamespace)) {
            return List.of();
        }
        List<ItemOperationEntry> result = new ArrayList<>();
        for (ItemOperationEntry entry : readAll(itemStack)) {
            if (sourceNamespace.equals(entry.sourceNamespace())) {
                result.add(entry);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public boolean hasOperations(ItemStack itemStack) {
        return PDC.has(itemStack, PARTITION, FIELD, PersistentDataType.STRING);
    }

    public void clear(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        PDC.remove(itemStack, PARTITION, FIELD);
    }

    List<ItemOperationEntry> replay(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        List<ItemOperationEntry> refreshed = normalizeEntries(replayer.replay(itemStack, normalizeEntries(entries)));
        writeAll(itemStack, refreshed);
        return refreshed;
    }

    List<ItemOperationEntry> replayFromBase(ItemStack itemStack,
            ItemOperationBaseView baseView,
            List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return entries == null ? List.of() : List.copyOf(entries);
        }
        List<ItemOperationEntry> refreshed = normalizeEntries(
                replayer.replayFromBase(itemStack, baseView, normalizeEntries(entries))
        );
        writeAll(itemStack, refreshed);
        return refreshed;
    }

    ItemOperationBaseView resolveBaseView(ItemStack itemStack, List<ItemOperationEntry> entries) {
        return replayer.resolveBaseView(itemStack, normalizeEntries(entries));
    }

    ItemOperationReplayer.ReplayResult renderFromBase(ItemStack template,
            ItemOperationBaseView baseView,
            List<ItemOperationEntry> entries) {
        return replayer.renderFromBase(template, baseView, normalizeEntries(entries));
    }

    void replaceAll(ItemStack itemStack, List<ItemOperationEntry> entries) {
        writeAll(itemStack, entries);
    }

    void append(ItemStack itemStack, ItemOperationEntry entry) {
        if (itemStack == null || itemStack.getType().isAir() || entry == null || entry.isEmpty()) {
            return;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        int existingIndex = operationIndex(entries, entry.operationId());
        if (existingIndex >= 0) {
            entries.set(existingIndex, entry);
        } else {
            entries.add(entry);
        }
        writeAll(itemStack, entries);
    }

    ItemOperationEntry remove(ItemStack itemStack, String operationId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return null;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        int index = operationIndex(entries, operationId);
        if (index < 0) {
            return null;
        }
        ItemOperationEntry removed = entries.remove(index);
        writeAll(itemStack, entries);
        return removed;
    }

    List<ItemOperationEntry> removeByNamespace(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return List.of();
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        List<ItemOperationEntry> removed = new ArrayList<>();
        entries.removeIf(entry -> {
            if (entry != null && sourceNamespace.equals(entry.sourceNamespace())) {
                removed.add(entry);
                return true;
            }
            return false;
        });
        if (!removed.isEmpty()) {
            writeAll(itemStack, entries);
        }
        return removed.isEmpty() ? List.of() : List.copyOf(removed);
    }

    ItemStack managedDisplayTemplate(ItemStack itemStack) {
        boolean snapshotStored = presentationSnapshotFieldPresent(itemStack);
        ItemPresentationSnapshot snapshot = readPresentationSnapshot(itemStack);
        if (snapshot == null) {
            return snapshotStored ? null : itemStack;
        }
        ItemStack managed = itemStack.clone();
        return writeDisplay(managed, snapshot.customName(), snapshot.lore()) ? managed : null;
    }

    CustomNameUpdate prepareCustomNameUpdate(ItemStack original,
            String oldManagedName,
            String newManagedName,
            boolean oldManagedOverlay,
            boolean newManagedOverlay) {
        String currentName = currentCustomName(original);
        boolean currentIsExternal = !currentName.equals(Texts.toStringSafe(oldManagedName));
        boolean storedExternal = PDC.has(
                original,
                PARTITION,
                EXTERNAL_CUSTOM_NAME_FIELD,
                PersistentDataType.STRING
        );
        String externalName = currentIsExternal
                ? currentName
                : storedExternal
                        ? Texts.toStringSafe(PDC.get(
                                original,
                                PARTITION,
                                EXTERNAL_CUSTOM_NAME_FIELD,
                                PersistentDataType.STRING
                        ))
                        : "";
        boolean preserveExternal = currentIsExternal || storedExternal;
        if (newManagedOverlay) {
            return new CustomNameUpdate(newManagedName, preserveExternal, externalName);
        }
        String finalName;
        if (currentIsExternal) {
            finalName = currentName;
        } else if (oldManagedOverlay && storedExternal) {
            finalName = externalName;
        } else {
            finalName = reconcileCustomName(oldManagedName, currentName, newManagedName, false);
        }
        return new CustomNameUpdate(finalName, false, "");
    }

    void writeCustomNameUpdate(ItemStack itemStack, CustomNameUpdate update) {
        if (itemStack == null || update == null) {
            return;
        }
        if (update.externalStored()) {
            PDC.set(
                    itemStack,
                    PARTITION,
                    EXTERNAL_CUSTOM_NAME_FIELD,
                    PersistentDataType.STRING,
                    update.externalCustomName()
            );
        } else {
            PDC.remove(itemStack, PARTITION, EXTERNAL_CUSTOM_NAME_FIELD);
        }
    }

    SnapshotUpdate preparePresentationSnapshotUpdate(ItemStack original,
            ItemStack managedProjection,
            boolean assemblyNameOverlay) {
        if (!PDC.has(original, PARTITION, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING)) {
            return SnapshotUpdate.NOT_REQUIRED;
        }
        ItemPresentationSnapshot snapshot = new ItemPresentationSnapshot(
                currentCustomName(managedProjection),
                currentLore(managedProjection),
                assemblyNameOverlay
        );
        String payload = ItemPresentationSnapshot.CODEC.encode(snapshot);
        return Texts.isBlank(payload) ? SnapshotUpdate.INVALID : new SnapshotUpdate(true, payload, true);
    }

    void writePresentationSnapshotUpdate(ItemStack itemStack, SnapshotUpdate update) {
        if (itemStack == null || update == null || !update.required() || !update.valid()) {
            return;
        }
        PDC.set(itemStack, PARTITION, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING, update.payload());
    }

    private boolean applyInternal(ActionContext context,
            ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return false;
        }
        List<ItemOperationEntry> entriesBefore = readAll(itemStack);
        int replacementIndex = operationIndex(entriesBefore, operationId);
        int insertionIndex = replacementIndex < 0 ? entriesBefore.size() : replacementIndex;
        List<ItemOperationEntry> retainedEntries = new ArrayList<>(entriesBefore);
        if (replacementIndex >= 0) {
            retainedEntries.remove(replacementIndex);
        }

        ItemStack managedTemplate = managedDisplayTemplate(itemStack);
        if (managedTemplate == null) {
            return false;
        }
        ItemOperationBaseView baseView = replayer.resolveBaseView(managedTemplate, entriesBefore);
        boolean assemblyNameOverlay = hasAssemblyNameOverlay(itemStack, baseView);
        ItemOperationReplayer.ReplayResult oldProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                entriesBefore
        );
        if (oldProjection.itemStack() == null) {
            return false;
        }

        int prefixSize = Math.min(insertionIndex, retainedEntries.size());
        List<ItemOperationEntry> prefixEntries = retainedEntries.subList(0, prefixSize);
        List<ItemOperationEntry> suffixEntries = retainedEntries.subList(prefixSize, retainedEntries.size());
        ItemOperationReplayer.ReplayResult prefixProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                prefixEntries
        );
        if (prefixProjection.itemStack() == null) {
            return false;
        }
        replaceAll(prefixProjection.itemStack(), prefixProjection.entries());
        ItemOperationExecutor.ExecutionResult execution = executor.execute(
                context,
                prefixProjection.itemStack(),
                operationId,
                sourceNamespace,
                nameActions,
                loreActions,
                variables
        );
        if (!execution.success() || execution.entry() == null) {
            return false;
        }

        List<ItemOperationEntry> orderedEntries = new ArrayList<>(prefixProjection.entries());
        orderedEntries.add(execution.entry());
        orderedEntries.addAll(suffixEntries);
        ItemOperationReplayer.ReplayResult newProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                orderedEntries
        );
        if (newProjection.itemStack() == null) {
            return false;
        }

        List<String> oldManagedLore = currentLore(oldProjection.itemStack());
        List<String> newManagedLore = currentLore(newProjection.itemStack());
        ItemLoreReconciler.Reconciliation reconciliation = loreReconciler.reconcile(
                oldManagedLore,
                currentLore(itemStack),
                newManagedLore
        );
        if (!loreReconciler.preservesExternalProjection(
                newManagedLore,
                reconciliation.lore(),
                reconciliation.externalLines())) {
            return false;
        }
        CustomNameUpdate customNameUpdate = prepareCustomNameUpdate(
                itemStack,
                currentCustomName(oldProjection.itemStack()),
                currentCustomName(newProjection.itemStack()),
                assemblyNameOverlay || hasNameOverlay(entriesBefore),
                assemblyNameOverlay || hasNameOverlay(newProjection.entries())
        );
        SnapshotUpdate snapshotUpdate = preparePresentationSnapshotUpdate(
                itemStack,
                newProjection.itemStack(),
                assemblyNameOverlay
        );
        if (!snapshotUpdate.valid()
                || !writeDisplay(itemStack, customNameUpdate.customName(), reconciliation.lore())) {
            return false;
        }
        replaceAll(itemStack, newProjection.entries());
        writePresentationSnapshotUpdate(itemStack, snapshotUpdate);
        writeCustomNameUpdate(itemStack, customNameUpdate);
        return true;
    }

    private String reconcileCustomName(String oldManagedName,
            String currentName,
            String newManagedName,
            boolean managedOverlayRemains) {
        if (currentName.equals(oldManagedName)) {
            return newManagedName;
        }
        if (newManagedName.equals(oldManagedName)) {
            return currentName;
        }
        return managedOverlayRemains ? newManagedName : currentName;
    }

    boolean hasAssemblyNameOverlay(ItemStack itemStack, ItemOperationBaseView baseView) {
        ItemPresentationSnapshot snapshot = readPresentationSnapshot(itemStack);
        if (snapshot != null && snapshot.assemblyNameOverlayKnown()) {
            return snapshot.assemblyNameOverlay();
        }
        if (baseView == null
                || !PDC.has(itemStack, PARTITION, ASSEMBLY_SCHEMA_VERSION_FIELD, PersistentDataType.INTEGER)
                || !PDC.has(itemStack, PARTITION, ASSEMBLY_BASE_SOURCE_FIELD, PersistentDataType.STRING)) {
            return false;
        }
        String baseCustomName = PDC.get(
                itemStack,
                PARTITION,
                ASSEMBLY_BASE_CUSTOM_NAME_FIELD,
                PersistentDataType.STRING
        );
        return !Texts.toStringSafe(baseCustomName).equals(Texts.toStringSafe(baseView.customName()));
    }

    private boolean hasNameOverlay(List<ItemOperationEntry> entries) {
        if (entries == null) {
            return false;
        }
        for (ItemOperationEntry entry : entries) {
            if (entry != null && entry.nameRecords() != null && !entry.nameRecords().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean writeDisplay(ItemStack itemStack, String customName, List<String> lore) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        ItemTextBridge.customName(itemMeta, Texts.isBlank(customName) ? null : MiniMessages.parse(customName));
        ItemTextBridge.setLoreLines(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return true;
    }

    private boolean presentationSnapshotFieldPresent(ItemStack itemStack) {
        if (PDC.has(itemStack, PARTITION, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING)) {
            return true;
        }
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null
                && itemMeta.getPersistentDataContainer().getKeys().contains(PRESENTATION_SNAPSHOT_KEY);
    }

    private ItemPresentationSnapshot readPresentationSnapshot(ItemStack itemStack) {
        String payload = PDC.get(itemStack, PARTITION, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return null;
        }
        return ItemPresentationSnapshot.decodeStrict(payload);
    }

    private List<String> currentLore(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        return lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
    }

    private String currentCustomName(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (!ItemTextBridge.hasCustomName(itemMeta)) {
            return "";
        }
        return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
    }

    private int operationIndex(List<ItemOperationEntry> entries, String operationId) {
        if (entries == null || Texts.isBlank(operationId)) {
            return -1;
        }
        for (int index = 0; index < entries.size(); index++) {
            ItemOperationEntry entry = entries.get(index);
            if (entry != null && operationId.equals(entry.operationId())) {
                return index;
            }
        }
        return -1;
    }

    private List<ItemOperationEntry> normalizeEntries(List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, ItemOperationEntry> byId = new LinkedHashMap<>();
        for (ItemOperationEntry entry : entries) {
            if (entry == null || entry.isEmpty() || Texts.isBlank(entry.operationId())) {
                continue;
            }
            byId.put(entry.operationId(), entry);
        }
        return byId.isEmpty() ? List.of() : List.copyOf(byId.values());
    }

    private void writeAll(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        List<ItemOperationEntry> normalized = normalizeEntries(entries);
        if (normalized.isEmpty()) {
            PDC.remove(itemStack, PARTITION, FIELD);
            return;
        }
        List<Map<String, Object>> encoded = ItemOperationCodec.encode(normalized);
        String payload = YamlFiles.dump(Map.of("ops", encoded));
        PDC.set(itemStack, PARTITION, FIELD, PersistentDataType.STRING, payload);
    }

    private Object parsePayload(String payload) {
        if (Texts.isBlank(payload)) {
            return null;
        }
        var section = YamlFiles.load(payload);
        Object ops = section.get("ops");
        return ops != null ? ops : ConfigNodes.toPlainData(section.asMap());
    }

    record CustomNameUpdate(String customName, boolean externalStored, String externalCustomName) {

        CustomNameUpdate {
            customName = Texts.toStringSafe(customName);
            externalCustomName = Texts.toStringSafe(externalCustomName);
        }
    }

    record SnapshotUpdate(boolean required, String payload, boolean valid) {

        static final SnapshotUpdate NOT_REQUIRED = new SnapshotUpdate(false, "", true);
        static final SnapshotUpdate INVALID = new SnapshotUpdate(true, "", false);

        SnapshotUpdate {
            payload = Texts.toStringSafe(payload);
        }
    }
}
