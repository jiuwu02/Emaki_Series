package emaki.jiuwu.craft.item.listener;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

import emaki.jiuwu.craft.item.model.RefreshFullReason;
import emaki.jiuwu.craft.item.model.RefreshScope;
import emaki.jiuwu.craft.corelib.item.ClickRefreshDecision;

public final class InventoryRefreshClassifier {

    public static final int OFF_HAND_SLOT = 40;
    public static final int FIRST_ARMOR_SLOT = 36;
    public static final int LAST_PLAYER_SLOT = 40;

    public Result classifyClick(ClickContext context) {
        if (context == null || context.action() == null || context.click() == null) {
            return Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT);
        }
        InventoryAction action = context.action();
        ClickType click = context.click();

        ClickRefreshDecision decision = ClickRefreshDecision.of(action, click);
        if (decision == ClickRefreshDecision.SKIP) {
            return Result.skip();
        }
        if (decision == ClickRefreshDecision.FULL_COLLECT_TO_CURSOR) {
            return Result.full(RefreshFullReason.COLLECT_TO_CURSOR);
        }
        if (decision == ClickRefreshDecision.FULL_DOUBLE_CLICK) {
            return Result.full(RefreshFullReason.DOUBLE_CLICK);
        }
        if (decision == ClickRefreshDecision.FULL_UNKNOWN_ACTION) {
            return Result.full(RefreshFullReason.UNKNOWN_ACTION);
        }
        if (decision == ClickRefreshDecision.FULL_UNKNOWN_CLICK) {
            return Result.full(RefreshFullReason.UNKNOWN_CLICK);
        }
        if (decision == ClickRefreshDecision.FULL_UNSUPPORTED_CONTEXT) {
            return Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT);
        }

        boolean hotbarClick = click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND;
        if (hotbarClick != (action == InventoryAction.HOTBAR_SWAP)) {
            return Result.full(RefreshFullReason.ACTION_CLICK_MISMATCH);
        }
        boolean shiftClick = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!shiftClick) {
                return Result.full(RefreshFullReason.ACTION_CLICK_MISMATCH);
            }
            if (context.clickedArea() == ClickedArea.TOP) {
                return Result.full(RefreshFullReason.TOP_TO_PLAYER_SHIFT);
            }
            if (context.clickedArea() != ClickedArea.PLAYER) {
                return Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT);
            }
            return localPlayerSlot(context.playerSlot());
        }
        if (shiftClick) {
            return Result.full(RefreshFullReason.ACTION_CLICK_MISMATCH);
        }

        if (hotbarClick) {
            LinkedHashSet<Integer> dirtySlots = new LinkedHashSet<>();
            if (context.clickedArea() == ClickedArea.PLAYER) {
                if (context.playerSlot() < 0 || context.playerSlot() > LAST_PLAYER_SLOT) {
                    return Result.full(RefreshFullReason.SLOT_CONVERSION_FAILED);
                }
                dirtySlots.add(context.playerSlot());
            } else if (context.clickedArea() != ClickedArea.TOP) {
                return Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT);
            }
            if (click == ClickType.NUMBER_KEY) {
                if (context.hotbarButton() < 0 || context.hotbarButton() > 8) {
                    return Result.full(RefreshFullReason.SLOT_CONVERSION_FAILED);
                }
                dirtySlots.add(context.hotbarButton());
            } else {
                dirtySlots.add(OFF_HAND_SLOT);
            }
            return Result.local(dirtySlots, touchesContribution(dirtySlots));
        }

        if (context.clickedArea() == ClickedArea.PLAYER) {
            return localPlayerSlot(context.playerSlot());
        }
        if (context.clickedArea() == ClickedArea.TOP || context.clickedArea() == ClickedArea.OUTSIDE) {
            return Result.skip();
        }
        return Result.full(RefreshFullReason.UNSUPPORTED_CONTEXT);
    }

    public Result classifyDrag(Set<Integer> convertedPlayerSlots, boolean conversionFailed) {
        if (conversionFailed) {
            return Result.full(RefreshFullReason.SLOT_CONVERSION_FAILED);
        }
        if (convertedPlayerSlots == null || convertedPlayerSlots.isEmpty()) {
            return Result.skip();
        }
        LinkedHashSet<Integer> dirtySlots = new LinkedHashSet<>();
        for (Integer slot : convertedPlayerSlots) {
            if (slot == null || slot < 0 || slot > LAST_PLAYER_SLOT) {
                return Result.full(RefreshFullReason.SLOT_CONVERSION_FAILED);
            }
            dirtySlots.add(slot);
        }
        return Result.local(dirtySlots, touchesContribution(dirtySlots));
    }

    private Result localPlayerSlot(int slot) {
        if (slot < 0 || slot > LAST_PLAYER_SLOT) {
            return Result.full(RefreshFullReason.SLOT_CONVERSION_FAILED);
        }
        return Result.local(Set.of(slot), touchesContribution(Set.of(slot)));
    }

    private boolean touchesContribution(Set<Integer> dirtySlots) {
        if (dirtySlots == null || dirtySlots.isEmpty()) {
            return false;
        }
        for (Integer slot : dirtySlots) {
            if (slot != null && ((slot >= 0 && slot <= 8) || slot >= FIRST_ARMOR_SLOT && slot <= LAST_PLAYER_SLOT)) {
                return true;
            }
        }
        return false;
    }

    public enum ClickedArea {
        PLAYER,
        TOP,
        OUTSIDE,
        UNKNOWN
    }

    public record ClickContext(
            InventoryAction action,
            ClickType click,
            ClickedArea clickedArea,
            int rawSlot,
            int playerSlot,
            int hotbarButton) {

        public ClickContext {
            clickedArea = clickedArea == null ? ClickedArea.UNKNOWN : clickedArea;
        }
    }

    public record Result(
            RefreshScope scope,
            Set<Integer> dirtySlots,
            boolean contributionDirty,
            Set<RefreshFullReason> fullReasons) {

        public Result {
            scope = scope == null ? RefreshScope.SKIP : scope;
            dirtySlots = dirtySlots == null || dirtySlots.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(dirtySlots));
            fullReasons = fullReasons == null || fullReasons.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(fullReasons));
        }

        public static Result skip() {
            return new Result(RefreshScope.SKIP, Set.of(), false, Set.of());
        }

        public static Result local(Set<Integer> dirtySlots, boolean contributionDirty) {
            return new Result(RefreshScope.LOCAL, dirtySlots, contributionDirty, Set.of());
        }

        public static Result full(RefreshFullReason reason) {
            return new Result(RefreshScope.FULL, Set.of(), true, Set.of(reason));
        }
    }
}
