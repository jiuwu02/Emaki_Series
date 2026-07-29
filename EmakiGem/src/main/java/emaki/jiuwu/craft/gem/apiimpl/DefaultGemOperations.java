package emaki.jiuwu.craft.gem.apiimpl;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.GemOperations;
import emaki.jiuwu.craft.gem.api.event.GemExtractCompletedEvent;
import emaki.jiuwu.craft.gem.api.event.GemInlayCompletedEvent;
import emaki.jiuwu.craft.gem.api.model.GemExtractOutcome;
import emaki.jiuwu.craft.gem.api.model.GemInlayOutcome;
import emaki.jiuwu.craft.gem.api.model.GemSocketOpenOutcome;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemExtractService;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.GemStateService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

/**
 * {@link GemOperations} 的运行时实现。
 *
 * <p>两处关键责任：
 * <ul>
 * <li><strong>先校验线程归属。</strong>runtime 的事件 fire 点自带 {@code isEntityOwned} 守卫，
 * 非归属线程调用会静默跳过事件，导致监听器失去取消机会。这里提前拒绝，保证事件契约不被绕过。</li>
 * <li><strong>内部完成 commit。</strong>runtime 的 {@code inlayDirect}/{@code extractDirect} 返回待提交
 * 动作，漏提交会让操作日志停留在「已扣费未完成」，下次启动被恢复逻辑误判并重复退款。这里在返回前
 * 统一提交，且不把 Runnable 外泄。</li>
 * </ul>
 */
public final class DefaultGemOperations implements GemOperations {

    private final EmakiGemPlugin plugin;

    public DefaultGemOperations(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<GemInlayOutcome> inlay(@Nullable Player actor,
            @Nullable ItemStack equipment,
            @Nullable ItemStack gemItem,
            int slotIndex,
            boolean bypassCost) {
        if (actor == null) {
            return EmakiResult.invalidInput("gem.error.no_actor");
        }
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        if (gemItem == null || gemItem.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_gem_item");
        }
        GemInlayService inlayService = plugin.inlayService();
        if (inlayService == null || plugin.itemMatcher() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(actor)) {
            return EmakiResult.wrongThread();
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(gemItem);
        GemInlayService.InlayResult result =
                inlayService.inlayDirect(actor, equipment, gemItem, slotIndex, bypassCost, false);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.error.inlay_failed");
        }
        if (!result.result().success()) {
            return EmakiResult.failure(rejectionKind(result.result().messageKey()),
                    result.result().messageKey(),
                    result.result().placeholders());
        }
        if (result.updatedEquipment() == null) {
            return EmakiResult.internalError("gem.error.inlay_no_equipment");
        }
        result.commit();
        String gemId = instance == null ? "" : Texts.lower(instance.gemId());
        int gemLevel = instance == null ? 1 : instance.level();
        GemInlayOutcome outcome = new GemInlayOutcome(result.updatedEquipment(),
                result.result().inputConsumed(),
                slotIndex,
                gemId,
                gemLevel);
        Bukkit.getPluginManager().callEvent(new GemInlayCompletedEvent(actor,
                outcome.updatedEquipment(),
                slotIndex,
                gemId,
                gemLevel,
                outcome.inputConsumed()));
        return EmakiResult.success(outcome);
    }

    @Override
    public @NotNull EmakiResult<GemExtractOutcome> extract(@Nullable Player actor,
            @Nullable ItemStack equipment,
            int slotIndex,
            boolean bypassCost) {
        if (actor == null) {
            return EmakiResult.invalidInput("gem.error.no_actor");
        }
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        GemInlayService inlayService = plugin.inlayService();
        GemStateService stateService = plugin.stateService();
        if (inlayService == null || stateService == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(actor)) {
            return EmakiResult.wrongThread();
        }
        GemItemInstance existing = readAssignment(stateService, equipment, slotIndex);
        GemInlayService.ExtractDirectResult result =
                inlayService.extractDirect(actor, equipment, slotIndex, bypassCost);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.error.extract_failed");
        }
        GemExtractService.Result inner = result.result();
        if (!inner.success()) {
            return EmakiResult.failure(rejectionKind(inner.messageKey()), inner.messageKey(), inner.placeholders());
        }
        if (result.updatedEquipment() == null) {
            return EmakiResult.internalError("gem.error.extract_no_equipment");
        }
        result.commit();
        String gemId = existing == null ? "" : Texts.lower(existing.gemId());
        int gemLevel = existing == null ? 1 : existing.level();
        String returnMode = resolveReturnMode(gemId);
        GemExtractOutcome outcome = new GemExtractOutcome(result.updatedEquipment(),
                result.returnedGem(),
                slotIndex,
                gemId,
                gemLevel,
                returnMode);
        Bukkit.getPluginManager().callEvent(new GemExtractCompletedEvent(actor,
                outcome.updatedEquipment(),
                outcome.returnedGem(),
                slotIndex,
                gemId,
                gemLevel,
                returnMode));
        return EmakiResult.success(outcome);
    }

    @Override
    public @NotNull EmakiResult<GemSocketOpenOutcome> openSocket(@Nullable Player actor,
            @Nullable ItemStack equipment,
            @Nullable ItemStack openerItem,
            int slotIndex,
            boolean bypassRequirement) {
        if (actor == null) {
            return EmakiResult.invalidInput("gem.error.no_actor");
        }
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        if (openerItem == null || openerItem.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_opener");
        }
        SocketOpenerService openerService = plugin.socketOpenerService();
        if (openerService == null || plugin.itemMatcher() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(actor)) {
            return EmakiResult.wrongThread();
        }
        String openerId = resolveOpenerId(equipment, openerItem, slotIndex);
        SocketOpenerService.OpenResult result =
                openerService.openDirect(actor, equipment, openerItem, openerId, slotIndex, bypassRequirement);
        if (result == null || result.result() == null) {
            return EmakiResult.internalError("gem.error.open_failed");
        }
        if (!result.result().success()) {
            return EmakiResult.failure(rejectionKind(result.result().messageKey()),
                    result.result().messageKey(),
                    result.result().placeholders());
        }
        if (result.updatedEquipment() == null) {
            return EmakiResult.internalError("gem.error.open_no_equipment");
        }
        return EmakiResult.success(
                new GemSocketOpenOutcome(result.updatedEquipment(), result.updatedOpener(), slotIndex));
    }

    @Override
    public @NotNull EmakiResult<ItemStack> createGemItem(@Nullable String gemId, int level, int amount) {
        if (Texts.isBlank(gemId)) {
            return EmakiResult.invalidInput("gem.error.no_gem_id");
        }
        if (plugin.itemFactory() == null || plugin.gemLoader() == null) {
            return EmakiResult.unavailable();
        }
        GemDefinition definition = plugin.gemLoader().get(Texts.lower(gemId));
        if (definition == null) {
            return EmakiResult.notFound("gem.error.unknown_gem");
        }
        ItemStack created = plugin.itemFactory()
                .createGemItem(definition, Math.max(1, level), Math.max(1, amount));
        return created == null
                ? EmakiResult.internalError("gem.error.item_source_unresolved")
                : EmakiResult.success(created);
    }

    @Override
    public @NotNull EmakiResult<ItemStack> clearGems(@Nullable ItemStack equipment) {
        if (equipment == null || equipment.getType().isAir()) {
            return EmakiResult.invalidInput("gem.error.no_equipment");
        }
        GemStateService stateService = plugin.stateService();
        if (stateService == null) {
            return EmakiResult.unavailable();
        }
        ItemStack cleared = stateService.clearGemLayer(equipment);
        return cleared == null
                ? EmakiResult.notFound("gem.error.no_gem_layer")
                : EmakiResult.success(cleared);
    }

    @Override
    public @NotNull EmakiResult<Unit> openGui(@Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("gem.error.no_actor");
        }
        if (plugin.gemGuiService() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return plugin.gemGuiService().open(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "gem.error.gui_open_failed");
    }

    @Override
    public @NotNull EmakiResult<Unit> openSocketGui(@Nullable Player player, @Nullable ItemStack target) {
        if (player == null) {
            return EmakiResult.invalidInput("gem.error.no_actor");
        }
        if (plugin.gemGuiService() == null) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        boolean opened = target == null
                ? plugin.gemGuiService().openSocket(player)
                : plugin.gemGuiService().openSocket(player, target);
        return opened
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "gem.error.gui_open_failed");
    }

    /**
     * 读取指定槽位当前的宝石实例，用于在拆卸前留存身份信息。
     *
     * @param stateService 状态服务
     * @param equipment    装备物品
     * @param slotIndex    槽位索引
     * @return 宝石实例或 {@code null}
     */
    private static GemItemInstance readAssignment(GemStateService stateService, ItemStack equipment, int slotIndex) {
        GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return null;
        }
        GemState state = stateService.resolveState(equipment, itemDefinition);
        return state == null ? null : state.assignment(slotIndex);
    }

    /**
     * 查询宝石定义配置的返还模式。
     *
     * @param gemId 宝石 id
     * @return 返还模式；无法解析时返回空串
     */
    private String resolveReturnMode(String gemId) {
        if (Texts.isBlank(gemId) || plugin.gemLoader() == null) {
            return "";
        }
        GemDefinition definition = plugin.gemLoader().get(gemId);
        if (definition == null || definition.extractReturn() == null) {
            return "";
        }
        return Texts.toStringSafe(definition.extractReturn().mode());
    }

    /**
     * 解析开孔器 id：优先按目标槽位类型匹配，退化为通用匹配。
     *
     * @param equipment  装备物品
     * @param openerItem 开孔器物品
     * @param slotIndex  目标槽位
     * @return 开孔器 id；无法解析时返回空串
     */
    private String resolveOpenerId(ItemStack equipment, ItemStack openerItem, int slotIndex) {
        GemStateService stateService = plugin.stateService();
        String socketType = "";
        if (stateService != null) {
            GemItemDefinition itemDefinition = stateService.resolveItemDefinition(equipment);
            if (itemDefinition != null) {
                GemItemDefinition.SocketSlot slot = itemDefinition.slot(slotIndex);
                if (slot != null) {
                    socketType = Texts.toStringSafe(slot.type());
                }
            }
        }
        var config = plugin.itemMatcher().matchOpenerForType(openerItem, socketType);
        return config == null ? "" : Texts.toStringSafe(config.id());
    }

    /**
     * 把 runtime 的失败统一归类为业务拒绝。
     *
     * <p>runtime 对「监听器取消」与「前置条件不满足」使用同一个 message key
     * {@code gem.error.condition_not_met}，无法据此区分二者，因此不推断
     * {@link FailureKind#CANCELLED}——错报比不报更有害。调用方若需要区分，应自行监听 pre 事件。
     *
     * @param messageKey runtime message key
     * @return 失败种类，恒为 {@link FailureKind#REJECTED}
     */
    private static FailureKind rejectionKind(String messageKey) {
        return FailureKind.REJECTED;
    }
}
