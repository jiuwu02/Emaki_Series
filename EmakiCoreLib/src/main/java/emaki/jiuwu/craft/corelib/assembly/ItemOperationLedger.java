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

/**
 * 物品操作账本 — CoreLib 层的统一 Name/Lore 操作 + 回退服务。
 * <p>
 * 插件只需调用 {@link #apply} 执行 name_actions / lore_actions，账本会自动记录到 PDC。
 * 需要回退时调用 {@link #revert} 或 {@link #revertAll}，账本会精确撤销对应的修改。
 * <pre>{@code
 * ItemOperationLedger ledger = new ItemOperationLedger();
 *
 * // 镶嵌宝石时
 * ledger.apply(itemStack, "gem:slot_0", "gem", nameActions, loreActions, variables);
 *
 * // 取出宝石时
 * ledger.revert(itemStack, "gem:slot_0");
 *
 * // 清空某个命名空间的所有操作
 * ledger.revertAll(itemStack, "gem");
 * }</pre>
 */
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

    // ==================== 对外统一 API ====================

    /**
     * 对物品执行 name_actions / lore_actions 操作，并自动记录到 PDC 账本。
     *
     * @param itemStack       目标物品
     * @param operationId     操作唯一标识（如 "gem:slot_0"、"strengthen:star_3"）
     * @param sourceNamespace 来源命名空间（如 "gem"、"strengthen"、"forge"）
     * @param nameActions     名称操作（CoreLib 标准 name_actions 格式），可为 null
     * @param loreActions     Lore 操作（CoreLib 标准 lore_actions 格式），可为 null
     * @param variables       模板变量
     * @return true 如果操作成功执行并记录
     */
    public boolean apply(ItemStack itemStack,
            String operationId,
            String sourceNamespace,
            Object nameActions,
            Object loreActions,
            Map<String, ?> variables) {
        return executor.execute(itemStack, operationId, sourceNamespace, nameActions, loreActions, variables).success();
    }

    /**
     * 根据 operationId 精确回退一条操作。
     *
     * @param itemStack   目标物品
     * @param operationId 要回退的操作 ID
     * @return true 如果找到并成功回退
     */
    public boolean revert(ItemStack itemStack, String operationId) {
        return reverter.revert(itemStack, operationId).success();
    }

    /**
     * 回退指定命名空间的所有操作（按逆序回退）。
     *
     * @param itemStack       目标物品
     * @param sourceNamespace 命名空间
     * @return 回退的操作数量
     */
    public int revertAll(ItemStack itemStack, String sourceNamespace) {
        return reverter.revertAll(itemStack, sourceNamespace).revertedCount();
    }

    // ==================== 查询 API ====================

    /**
     * 读取物品上的所有操作记录。
     */
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

    /**
     * 根据 operationId 查找操作记录。
     */
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

    /**
     * 查找指定命名空间的所有操作记录。
     */
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

    /**
     * 检查物品是否有操作记录。
     */
    public boolean hasOperations(ItemStack itemStack) {
        return PDC.has(itemStack, PARTITION, FIELD, PersistentDataType.STRING);
    }

    /**
     * 清空物品上的所有操作记录（不回退修改，仅清除记录）。
     */
    public void clear(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        PDC.remove(itemStack, PARTITION, FIELD);
    }

    // ==================== 内部方法（供 Executor/Reverter 使用） ====================

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
