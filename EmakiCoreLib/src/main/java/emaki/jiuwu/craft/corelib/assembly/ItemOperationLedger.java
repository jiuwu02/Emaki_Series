package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class ItemOperationLedger {

    private static final PdcService PDC = new PdcService("emaki");
    private static final PdcPartition PARTITION = PDC.partition("item");
    private static final String FIELD = "operations";

    private final ItemOperationExecutor executor;
    private final ItemOperationReverter reverter;

    public ItemOperationLedger() {
        this.executor = new ItemOperationExecutor(this);
        this.reverter = new ItemOperationReverter(this);
    }


    public boolean apply(ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        return executor.execute(itemStack, operationId, sourceNamespace, nameActions, loreActions, variables).success();
    }

    public boolean revert(ItemStack itemStack, String operationId) {
        return reverter.revert(itemStack, operationId).success();
    }

    public int revertAll(ItemStack itemStack, String sourceNamespace) {
        return reverter.revertAll(itemStack, sourceNamespace).revertedCount();
    }


    public List<ItemOperationEntry> readAll(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        String payload = PDC.get(itemStack, PARTITION, FIELD, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return List.of();
        }
        Object parsed = parsePayload(payload);
        return ItemOperationCodec.decode(parsed);
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
        return result;
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


    void append(ItemStack itemStack, ItemOperationEntry entry) {
        if (itemStack == null || itemStack.getType().isAir() || entry == null || entry.isEmpty()) {
            return;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        entries.add(entry);
        writeAll(itemStack, entries);
    }

    ItemOperationEntry remove(ItemStack itemStack, String operationId) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(operationId)) {
            return null;
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        ItemOperationEntry removed = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (operationId.equals(entries.get(i).operationId())) {
                removed = entries.remove(i);
                break;
            }
        }
        if (removed != null) {
            writeAll(itemStack, entries);
        }
        return removed;
    }

    List<ItemOperationEntry> removeByNamespace(ItemStack itemStack, String sourceNamespace) {
        if (itemStack == null || itemStack.getType().isAir() || Texts.isBlank(sourceNamespace)) {
            return List.of();
        }
        List<ItemOperationEntry> entries = new ArrayList<>(readAll(itemStack));
        List<ItemOperationEntry> removed = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (sourceNamespace.equals(entries.get(i).sourceNamespace())) {
                removed.add(0, entries.remove(i));
            }
        }
        if (!removed.isEmpty()) {
            writeAll(itemStack, entries);
        }
        return removed;
    }

    private void writeAll(ItemStack itemStack, List<ItemOperationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            PDC.remove(itemStack, PARTITION, FIELD);
            return;
        }
        List<Map<String, Object>> encoded = ItemOperationCodec.encode(entries);
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
}
