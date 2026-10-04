package emaki.jiuwu.craft.gem.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

public final class GemHeldItemStage implements CoreActionStage {

    public enum Operation {

        OPEN_SOCKET("gem_open_socket", "在目标手持装备上开启一个插槽。"),

        INLAY("gem_inlay", "将副手宝石镶嵌到目标手持装备的插槽中。"),

        EXTRACT("gem_extract", "从目标手持装备的插槽中取出宝石。"),

        CLEAR_LAYER("gem_clear_layer", "移除目标手持装备上的宝石层。");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiGemPlugin plugin;
    private final Operation operation;

    public GemHeldItemStage(@NotNull EmakiGemPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "gem";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case OPEN_SOCKET -> List.of(
                    CoreStageParameter.required("opener", CoreStageParameterType.STRING, "开孔器 ID"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.INTEGER, "-1",
                            "目标插槽"),
                    CoreStageParameter.optional("bypass", CoreStageParameterType.BOOLEAN, "false",
                            "跳过开孔器物品要求"));
            case INLAY, EXTRACT -> List.of(
                    CoreStageParameter.required("slot", CoreStageParameterType.INTEGER, "目标插槽"),
                    CoreStageParameter.optional("bypass_cost", CoreStageParameterType.BOOLEAN, "false",
                            "跳过配置的花费"));
            case CLEAR_LAYER -> List.of();
        };
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        return switch (operation) {
            case OPEN_SOCKET -> openSocket(target, arguments);
            case INLAY -> inlay(target, arguments);
            case EXTRACT -> extract(target, arguments);
            case CLEAR_LAYER -> clearLayer(target);
        };
    }

    private CoreActionOutcome openSocket(Player target, CoreResolvedArguments arguments) {
        if (plugin.socketOpenerService() == null) {
            return unavailable();
        }
        SocketOpenerService.OpenResult result = plugin.socketOpenerService().openDirect(
                target,
                target.getInventory().getItemInMainHand(),
                target.getInventory().getItemInOffHand(),
                arguments.getString("opener"),
                arguments.getInt("slot", -1),
                arguments.getBoolean("bypass", false));
        if (!result.result().success()) {
            return rejected(result.result().messageKey());
        }
        target.getInventory().setItemInMainHand(result.updatedEquipment());
        target.getInventory().setItemInOffHand(result.updatedOpener());
        return CoreActionOutcome.success(data(result.result().placeholders()));
    }

    private CoreActionOutcome inlay(Player target, CoreResolvedArguments arguments) {
        if (plugin.inlayService() == null) {
            return unavailable();
        }
        ItemStack gem = target.getInventory().getItemInOffHand();
        GemInlayService.InlayResult result = plugin.inlayService().inlayDirect(
                target,
                target.getInventory().getItemInMainHand(),
                gem,
                arguments.getInt("slot", -1),
                arguments.getBoolean("bypass_cost", false),
                false);
        if (!result.result().success()) {

            if (result.result().inputConsumed()) {
                gem.subtract(1);
            }
            return rejected(result.result().messageKey());
        }
        target.getInventory().setItemInMainHand(result.updatedEquipment());
        gem.subtract(1);
        result.commit();
        return CoreActionOutcome.success(data(result.result().placeholders()));
    }

    private CoreActionOutcome extract(Player target, CoreResolvedArguments arguments) {
        if (plugin.inlayService() == null) {
            return unavailable();
        }
        GemInlayService.ExtractDirectResult result = plugin.inlayService().extractDirect(
                target,
                target.getInventory().getItemInMainHand(),
                arguments.getInt("slot", -1),
                arguments.getBoolean("bypass_cost", false));
        if (!result.result().success()) {
            return rejected(result.result().messageKey());
        }
        target.getInventory().setItemInMainHand(result.updatedEquipment());
        if (result.returnedGem() != null) {
            InventoryItemUtil.giveOrDrop(target, result.returnedGem());
        }
        result.commit();
        return CoreActionOutcome.success(data(result.result().placeholders()));
    }

    private CoreActionOutcome clearLayer(Player target) {
        if (plugin.stateService() == null) {
            return unavailable();
        }
        ItemStack updated = plugin.stateService().clearGemLayer(target.getInventory().getItemInMainHand());
        if (updated == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.gem.clear_failed");
        }
        target.getInventory().setItemInMainHand(updated);
        return CoreActionOutcome.success(Map.of(
                "has_layer", plugin.stateService().hasStoredLayer(updated)));
    }

    private static CoreActionOutcome unavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.stage.gem.service_unavailable");
    }

    private static CoreActionOutcome rejected(String messageKey) {
        return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                messageKey == null ? "action.stage.gem.rejected" : messageKey);
    }

    private static Map<String, Object> data(Map<String, ?> placeholders) {
        return placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
