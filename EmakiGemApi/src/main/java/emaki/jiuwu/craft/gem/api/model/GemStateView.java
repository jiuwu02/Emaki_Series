package emaki.jiuwu.craft.gem.api.model;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only snapshot of the gem layer stored on one piece of equipment.
 *
 * <p>Slots are ordered by index and always cover every socket the equipment definition declares, so a
 * caller can render the full socket strip without cross-referencing the definition.
 *
 * @param itemDefinitionId canonical lowercase id of the equipment definition
 * @param slots            every socket slot, ordered by index
 * @param updatedAt        epoch millis of the last change to the gem layer
 */
public record GemStateView(@NotNull String itemDefinitionId,
                           @NotNull List<GemSlotView> slots,
                           long updatedAt) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param itemDefinitionId equipment definition id
     * @param slots            socket slots
     * @param updatedAt        last change timestamp
     */
    public GemStateView {
        itemDefinitionId = itemDefinitionId == null ? "" : itemDefinitionId;
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /**
     * Looks up one slot by index.
     *
     * @param index the slot index
     * @return the slot when it exists, otherwise an empty optional
     */
    public @NotNull Optional<GemSlotView> slot(int index) {
        return slots.stream().filter(slot -> slot.index() == index).findFirst();
    }

    /** {@return every slot that currently holds a gem} */
    public @NotNull List<GemSlotView> occupiedSlots() {
        return slots.stream().filter(GemSlotView::occupied).toList();
    }

    /** {@return how many slots are opened} */
    public int openedCount() {
        return (int) slots.stream().filter(GemSlotView::opened).count();
    }

    /** {@return how many slots currently hold a gem} */
    public int inlaidCount() {
        return (int) slots.stream().filter(GemSlotView::occupied).count();
    }
}
