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
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class ItemOperationLedger {

    private static final String FIELD = "operations";
    private static final String PRESENTATION_SNAPSHOT_FIELD = "presentation_snapshot";
    private static final String ASSEMBLY_SCHEMA_VERSION_FIELD = "schema_version";
    private static final String ASSEMBLY_BASE_SOURCE_FIELD = "base_source";
    private static final String ASSEMBLY_BASE_CUSTOM_NAME_FIELD = "base_custom_name";
    static final String EXTERNAL_CUSTOM_NAME_FIELD = "external_custom_name";

    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final PdcService pdc;
    private final PdcPartition partition;
    private final NamespacedKey operationsKey;
    private final NamespacedKey presentationSnapshotKey;
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
        this.pdc = new PdcService("emaki", "pdc", this.debugLoggerSupplier.get());
        this.partition = pdc.partition("item");
        this.operationsKey = partition.key(FIELD);
        this.presentationSnapshotKey = partition.key(PRESENTATION_SNAPSHOT_FIELD);
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
        return apply(itemStack, operationId, sourceNamespace, nameActions, loreActions, variables, read(itemStack));
    }

    public boolean apply(ItemStack itemStack,
                         String operationId,
                         String sourceNamespace,
                         Object nameActions,
                         Object loreActions,
                         Map<String, ?> variables,
                         ReadResult readResult) {
        return apply(itemStack, readResult, operationId, sourceNamespace, nameActions, loreActions, variables).success();
    }

    public UpdateResult apply(ItemStack itemStack,
                              ReadResult readResult,
                              String operationId,
                              String sourceNamespace,
                              Object nameActions,
                              Object loreActions,
                              Map<String, ?> variables) {
        if (readResult == null || readResult.corrupt()) {
            return UpdateResult.failure(safeReadResult(readResult));
        }
        return apply(itemStack, readResult.entries(), operationId, sourceNamespace, nameActions, loreActions, variables);
    }

    UpdateResult apply(ItemStack itemStack,
                       List<ItemOperationEntry> entries,
                       String operationId,
                              String sourceNamespace,
                              Object nameActions,
                              Object loreActions,
                              Map<String, ?> variables) {
        return apply(null, itemStack, entries, operationId, sourceNamespace, nameActions, loreActions, variables);
    }

    public boolean apply(ActionContext context,
                         ItemStack itemStack,
                         String operationId,
                         String sourceNamespace,
                         Object nameActions,
                         Object loreActions,
                         Map<String, ?> variables) {
        return apply(context, itemStack, operationId, sourceNamespace, nameActions, loreActions, variables, read(itemStack));
    }

    public boolean apply(ActionContext context,
                         ItemStack itemStack,
                         String operationId,
                         String sourceNamespace,
                         Object nameActions,
                         Object loreActions,
                         Map<String, ?> variables,
                         ReadResult readResult) {
        return apply(context, itemStack, readResult, operationId, sourceNamespace,
                nameActions, loreActions, variables).success();
    }

    public UpdateResult apply(ActionContext context,
                              ItemStack itemStack,
                              ReadResult readResult,
                              String operationId,
                              String sourceNamespace,
                              Object nameActions,
                              Object loreActions,
                              Map<String, ?> variables) {
        if (readResult == null || readResult.corrupt()) {
            return UpdateResult.failure(safeReadResult(readResult));
        }
        return apply(context, itemStack, readResult.entries(), operationId, sourceNamespace,
                nameActions, loreActions, variables);
    }

    UpdateResult apply(ActionContext context,
                       ItemStack itemStack,
                       List<ItemOperationEntry> entries,
                       String operationId,
                              String sourceNamespace,
                              Object nameActions,
                              Object loreActions,
                              Map<String, ?> variables) {
        return applyInternal(context, itemStack, entries, operationId, sourceNamespace,
                nameActions, loreActions, variables);
    }

    public boolean revert(ItemStack itemStack, String operationId) {
        return revert(itemStack, operationId, read(itemStack));
    }

    public boolean revert(ItemStack itemStack, String operationId, ReadResult readResult) {
        return revert(itemStack, readResult, operationId).success();
    }

    public UpdateResult revert(ItemStack itemStack,
                               ReadResult readResult,
                               String operationId) {
        if (readResult == null || readResult.corrupt()) {
            return UpdateResult.failure(safeReadResult(readResult));
        }
        return revert(itemStack, readResult.entries(), operationId);
    }

    UpdateResult revert(ItemStack itemStack,
                        List<ItemOperationEntry> entries,
                        String operationId) {
        ReadResult readResult = ReadResult.valid(normalizeEntries(entries));
        ItemOperationReverter.RevertResult result = reverter.revert(itemStack, operationId, readResult.entries());
        return result.success()
                ? UpdateResult.success(result.entries())
                : UpdateResult.failure(readResult);
    }

    public int revertAll(ItemStack itemStack, String sourceNamespace) {
        return reverter.revertAll(itemStack, sourceNamespace, read(itemStack)).revertedCount();
    }

    public UpdateResult revertAll(ItemStack itemStack, String sourceNamespace, ReadResult readResult) {
        ReadResult safeReadResult = safeReadResult(readResult);
        if (safeReadResult.corrupt()) {
            return UpdateResult.failure(safeReadResult);
        }
        ItemOperationReverter.RevertResult result = reverter.revertAll(itemStack, sourceNamespace, safeReadResult);
        return result.success()
                ? UpdateResult.success(result.entries())
                : UpdateResult.failure(safeReadResult);
    }

    DebugLogger debugLogger() {
        return debugLoggerSupplier.get();
    }

    public ReadResult read(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !operationsFieldPresent(itemStack)) {
            return ReadResult.absent();
        }
        String payload = pdc.get(itemStack, partition, FIELD, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return ReadResult.corrupt(List.of());
        }
        try {
            ItemOperationCodec.DecodeResult decoded = ItemOperationCodec.decodeStrict(parsePayload(payload));
            List<ItemOperationEntry> entries = normalizeEntries(decoded.entries());
            if (!decoded.complete() || entries.isEmpty()) {
                return ReadResult.corrupt(entries);
            }
            return ReadResult.valid(entries);
        } catch (RuntimeException _) {
            return ReadResult.corrupt(List.of());
        }
    }

    public List<ItemOperationEntry> readAll(ItemStack itemStack) {
        return read(itemStack).entries();
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
        return read(itemStack).status() == ReadStatus.VALID;
    }


    public void clear(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        pdc.remove(itemStack, partition, FIELD);
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
        ReadResult readResult = read(itemStack);
        if (readResult.corrupt()) {
            return;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readResult.entries());
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
        ReadResult readResult = read(itemStack);
        if (readResult.corrupt()) {
            return null;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readResult.entries());
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
        ReadResult readResult = read(itemStack);
        if (readResult.corrupt()) {
            return List.of();
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readResult.entries());
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
        boolean storedExternal = pdc.has(
                original,
                partition,
                EXTERNAL_CUSTOM_NAME_FIELD,
                PersistentDataType.STRING
        );
        String externalName = currentIsExternal
                ? currentName
                : storedExternal
                        ? Texts.toStringSafe(pdc.get(
                                original,
                                partition,
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
            pdc.set(
                    itemStack,
                    partition,
                    EXTERNAL_CUSTOM_NAME_FIELD,
                    PersistentDataType.STRING,
                    update.externalCustomName()
            );
        } else {
            pdc.remove(itemStack, partition, EXTERNAL_CUSTOM_NAME_FIELD);
        }
    }

    SnapshotUpdate preparePresentationSnapshotUpdate(ItemStack original,
                                                     ItemStack managedProjection,
                                                     boolean assemblyNameOverlay) {
        if (!pdc.has(original, partition, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING)) {
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
        pdc.set(itemStack, partition, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING, update.payload());
    }

    private UpdateResult applyInternal(ActionContext context,
                                       ItemStack itemStack,
                                       List<ItemOperationEntry> entries,
                                       String operationId,
                                       String sourceNamespace,
                                       Object nameActions,
                                       Object loreActions,
                                       Map<String, ?> variables) {
        List<ItemOperationEntry> entriesBefore = normalizeEntries(entries);
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return UpdateResult.failure(entriesBefore);
        }
        int replacementIndex = operationIndex(entriesBefore, operationId);
        int insertionIndex = replacementIndex < 0 ? entriesBefore.size() : replacementIndex;
        List<ItemOperationEntry> retainedEntries = new ArrayList<>(entriesBefore);
        if (replacementIndex >= 0) {
            retainedEntries.remove(replacementIndex);
        }

        ItemStack managedTemplate = managedDisplayTemplate(itemStack);
        if (managedTemplate == null) {
            return UpdateResult.failure(entriesBefore);
        }
        ItemOperationBaseView baseView = replayer.resolveBaseView(managedTemplate, entriesBefore);
        boolean assemblyNameOverlay = hasAssemblyNameOverlay(itemStack, baseView);
        ItemOperationReplayer.ReplayResult oldProjection = replayer.renderFromBase(
                managedTemplate,
                baseView,
                entriesBefore
        );
        if (oldProjection.itemStack() == null) {
            return UpdateResult.failure(entriesBefore);
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
            return UpdateResult.failure(entriesBefore);
        }
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
            return UpdateResult.failure(entriesBefore);
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
            return UpdateResult.failure(entriesBefore);
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
            return UpdateResult.failure(entriesBefore);
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
            return UpdateResult.failure(entriesBefore);
        }
        replaceAll(itemStack, newProjection.entries());
        writePresentationSnapshotUpdate(itemStack, snapshotUpdate);
        writeCustomNameUpdate(itemStack, customNameUpdate);
        return UpdateResult.success(newProjection.entries());
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
                || !pdc.has(itemStack, partition, ASSEMBLY_SCHEMA_VERSION_FIELD, PersistentDataType.INTEGER)
                || !pdc.has(itemStack, partition, ASSEMBLY_BASE_SOURCE_FIELD, PersistentDataType.STRING)) {
            return false;
        }
        String baseCustomName = pdc.get(
                itemStack,
                partition,
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

    private boolean operationsFieldPresent(ItemStack itemStack) {
        if (pdc.has(itemStack, partition, FIELD, PersistentDataType.STRING)) {
            return true;
        }
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null && itemMeta.getPersistentDataContainer().getKeys().contains(operationsKey);
    }

    private boolean presentationSnapshotFieldPresent(ItemStack itemStack) {
        if (pdc.has(itemStack, partition, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING)) {
            return true;
        }
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null
                && itemMeta.getPersistentDataContainer().getKeys().contains(presentationSnapshotKey);
    }

    private ItemPresentationSnapshot readPresentationSnapshot(ItemStack itemStack) {
        String payload = pdc.get(itemStack, partition, PRESENTATION_SNAPSHOT_FIELD, PersistentDataType.STRING);
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

    private ReadResult safeReadResult(ReadResult readResult) {
        return readResult == null ? ReadResult.corrupt(List.of()) : readResult;
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
            pdc.remove(itemStack, partition, FIELD);
            return;
        }
        List<Map<String, Object>> encoded = ItemOperationCodec.encode(normalized);
        String payload = YamlFiles.dump(Map.of("ops", encoded));
        pdc.set(itemStack, partition, FIELD, PersistentDataType.STRING, payload);
    }

    private Object parsePayload(String payload) {
        if (Texts.isBlank(payload)) {
            return null;
        }
        return YamlFiles.load(payload).get("ops");
    }

    public record UpdateResult(boolean success, ReadResult readResult) {

        public UpdateResult {
            readResult = readResult == null ? ReadResult.corrupt(List.of()) : readResult;
            if (success && readResult.corrupt()) {
                success = false;
            }
        }

        static UpdateResult success(List<ItemOperationEntry> entries) {
            return new UpdateResult(true, ReadResult.valid(entries));
        }

        public static UpdateResult success(ReadResult readResult) {
            return new UpdateResult(true, readResult);
        }

        static UpdateResult failure(List<ItemOperationEntry> entries) {
            return new UpdateResult(false, ReadResult.valid(entries));
        }

        public static UpdateResult failure(ReadResult readResult) {
            return new UpdateResult(false, readResult);
        }

        public List<ItemOperationEntry> entries() {
            return readResult.entries();
        }
    }

    public enum ReadStatus {
        ABSENT,
        VALID,
        CORRUPT
    }

    public record ReadResult(ReadStatus status, List<ItemOperationEntry> entries) {

        public ReadResult {
            status = status == null ? ReadStatus.CORRUPT : status;
            entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
            if (status == ReadStatus.ABSENT && !entries.isEmpty()
                    || status == ReadStatus.VALID && entries.isEmpty()) {
                status = ReadStatus.CORRUPT;
            } else if (status == ReadStatus.VALID) {
                try {
                    ItemOperationCodec.DecodeResult decoded = ItemOperationCodec.decodeStrict(
                            ItemOperationCodec.encode(entries)
                    );
                    if (!decoded.complete() || decoded.entries().size() != entries.size()) {
                        status = ReadStatus.CORRUPT;
                    }
                } catch (RuntimeException exception) {
                    status = ReadStatus.CORRUPT;
                }
            }
        }

        public static ReadResult absent() {
            return new ReadResult(ReadStatus.ABSENT, List.of());
        }

        public static ReadResult valid(List<ItemOperationEntry> entries) {
            List<ItemOperationEntry> safeEntries = entries == null ? List.of() : List.copyOf(entries);
            return safeEntries.isEmpty() ? absent() : new ReadResult(ReadStatus.VALID, safeEntries);
        }

        public static ReadResult corrupt(List<ItemOperationEntry> entries) {
            return new ReadResult(ReadStatus.CORRUPT, entries);
        }

        public boolean corrupt() {
            return status == ReadStatus.CORRUPT;
        }
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
