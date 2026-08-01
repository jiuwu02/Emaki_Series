package emaki.jiuwu.craft.gem.action.v2;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.service.GemInlayService;
import emaki.jiuwu.craft.gem.service.SocketOpenerService;

/**
 * Socket, inlay, extract and layer operations on the target's held gear.
 *
 * <p>The v2 counterpart of {@code GemHeldItemAction}. All four operations read the main hand as the gear and,
 * for three of them, the off hand as the consumable, then write both back.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: every operation reads and writes one player's inventory. {@code extract}
 * may drop the returned gem at the player's feet when the inventory is full, which is still that entity's
 * region, so it does not need a wider domain.</p>
 */
public final class GemHeldItemStage implements CoreActionStage {

    /** Which gem operation a stage instance performs. */
    public enum Operation {

        /** Open a socket on the held gear using the off-hand opener. */
        OPEN_SOCKET("gem_open_socket", "Opens a socket on the target's held gear."),

        /** Inlay the off-hand gem into a socket. */
        INLAY("gem_inlay", "Inlays the off-hand gem into a socket on the target's held gear."),

        /** Remove a gem from a socket and return it. */
        EXTRACT("gem_extract", "Extracts a gem from a socket on the target's held gear."),

        /** Strip the gem layer entirely. */
        CLEAR_LAYER("gem_clear_layer", "Removes the gem layer from the target's held gear.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiGemPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the gem services
     * @param operation which operation this instance performs
     */
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
                    CoreStageParameter.required("opener", CoreStageParameterType.STRING, "Socket opener id"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.INTEGER, "-1",
                            "Target socket slot"),
                    CoreStageParameter.optional("bypass", CoreStageParameterType.BOOLEAN, "false",
                            "Bypass the opener item requirement"));
            case INLAY, EXTRACT -> List.of(
                    CoreStageParameter.required("slot", CoreStageParameterType.INTEGER, "Target socket slot"),
                    CoreStageParameter.optional("bypass_cost", CoreStageParameterType.BOOLEAN, "false",
                            "Bypass the configured cost"));
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
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
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
            // v1 consumed the gem on a consuming failure too: the service already counted it as spent, so
            // skipping the subtract here would hand the player a free retry.
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
                    "action.v2.stage.gem.clear_failed");
        }
        target.getInventory().setItemInMainHand(updated);
        return CoreActionOutcome.success(Map.of(
                "has_layer", plugin.stateService().hasStoredLayer(updated)));
    }

    private static CoreActionOutcome unavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.v2.stage.gem.service_unavailable");
    }

    /**
     * Maps a refused gem operation onto the pipeline's failure model.
     *
     * <p>{@code REJECTED} rather than {@code INTERNAL_ERROR}: the service declined the operation by its own
     * rules, for example a missing opener or an occupied slot, which is a domain decision and not a fault.
     * The service's own message key is passed through so the existing player-facing text still applies.</p>
     */
    private static CoreActionOutcome rejected(String messageKey) {
        return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                messageKey == null ? "action.v2.stage.gem.rejected" : messageKey);
    }

    private static Map<String, Object> data(Map<String, ?> placeholders) {
        return placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
