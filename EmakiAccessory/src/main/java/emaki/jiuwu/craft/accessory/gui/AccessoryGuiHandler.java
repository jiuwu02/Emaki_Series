package emaki.jiuwu.craft.accessory.gui;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

/**
 * Per-window click handling for one player's accessory contents.
 *
 * <p>One instance per open window, bound to the payload being edited, so an administrator viewing
 * someone else's accessories never shares click state with the owner's own window.
 *
 * <p>Duplication defence is the most important thing here. CoreLib cancels top-inventory clicks and
 * all drags, but {@code GuiClickContext#isBlockedTransfer()} covers only shift-click,
 * {@code MOVE_TO_OTHER_INVENTORY}, {@code COLLECT_TO_CURSOR} and {@code DOUBLE_CLICK}; it does not
 * cover number-key swaps, offhand swaps or drop keys. Those bypass cursor semantics, and an offhand
 * swap is exactly the entry point behind RPGInventory's long-lived duplication bug, so they are
 * rejected explicitly rather than left to the generic guard.
 */
public final class AccessoryGuiHandler implements GuiSessionHandler {

    /** Host callbacks, so this handler needs no reference to the plugin type. */
    public interface Callbacks {

        /** {@return the owning plugin, used as the GUI session owner} */
        Plugin plugin();

        /**
         * {@return whether this viewer may modify the payload right now}
         *
         * @param viewer      the player clicking
         * @param accessories the payload being edited
         */
        boolean canWrite(Player viewer, PlayerAccessories accessories);

        /**
         * Called after a successful mutation, to persist and recompute contributions.
         *
         * @param viewer      the player who made the change
         * @param accessories the mutated payload
         */
        void onContentsChanged(Player viewer, PlayerAccessories accessories);

        /**
         * Called once when the window closes.
         *
         * @param viewer      the player who closed the window
         * @param accessories the payload that was being edited
         */
        void onWindowClosed(Player viewer, PlayerAccessories accessories);

        /**
         * Reports a refused action to the player.
         *
         * @param viewer       the player to inform
         * @param messageKey   the lang key
         * @param replacements placeholder values
         */
        void reject(Player viewer, String messageKey, Map<String, ?> replacements);
    }

    private final Callbacks callbacks;
    private final AccessoryGuiService guiService;
    private final AccessoryUniqueService uniqueService;
    private final PlayerAccessories accessories;
    private boolean closed;

    /**
     * Creates a handler for one window.
     *
     * @param callbacks     host callbacks
     * @param guiService    the accessory window service
     * @param uniqueService the duplicate-accessory rule
     * @param accessories   the payload this window edits
     */
    public AccessoryGuiHandler(Callbacks callbacks,
            AccessoryGuiService guiService,
            AccessoryUniqueService uniqueService,
            PlayerAccessories accessories) {
        this.callbacks = callbacks;
        this.guiService = guiService;
        this.uniqueService = uniqueService;
        this.accessories = accessories;
    }

    /** {@return the owning plugin} */
    public Plugin owner() {
        return callbacks.plugin();
    }

    /** {@return the payload this window edits} */
    public PlayerAccessories accessories() {
        return accessories;
    }

    @Override
    public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
        click.setCancelled(true);
        if (slot == null || slot.definition() == null || !slot.definition().hasType()) {
            return;
        }
        Player viewer = click.viewer();
        if (rejectedClick(click.clickType())) {
            callbacks.reject(viewer, "gui.click_unsupported", Map.of());
            return;
        }
        String type = Texts.normalizeId(slot.definition().type());
        switch (type) {
            case AccessoryGuiService.TYPE_ACCESSORY_SLOT -> handleAccessoryClick(session, click, viewer, slot);
            case AccessoryGuiService.TYPE_ORPHAN_SLOT -> handleOrphanClick(session, click, viewer, slot);
            default -> {
                // Decorative cell: the click is already cancelled and nothing else should happen.
            }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {
        // CoreLib only auto-cancels the top inventory, so the player's own grid must be guarded here.
        // Shift-moving an accessory in would have to guess a target cell; refusing is both predictable
        // and keeps the single insertion path through an explicit cell click.
        if (click.isBlockedTransfer() || rejectedClick(click.clickType())) {
            click.setCancelled(true);
            callbacks.reject(click.viewer(), "gui.transfer_unsupported", Map.of());
        }
    }

    @Override
    public void onClose(GuiSession session, GuiCloseContext close) {
        // Two paths reach this: the Bukkit close event and an explicit GuiService close. Guard so the
        // persist-and-recompute work runs exactly once.
        if (closed) {
            return;
        }
        closed = true;
        callbacks.onWindowClosed(session == null ? null : session.viewer(), accessories);
    }

    private void handleAccessoryClick(GuiSession session,
            GuiClickContext click,
            Player viewer,
            GuiTemplate.ResolvedSlot slot) {
        String slotInstanceId = guiService.slotInstanceAt(slot.inventorySlot());
        if (Texts.isBlank(slotInstanceId)) {
            return;
        }
        ItemStack cursor = click.cursorItem();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        ItemStack stored = accessories.itemAt(slotInstanceId);
        boolean slotEmpty = stored == null || stored.getType().isAir();

        if (cursorEmpty && slotEmpty) {
            return;
        }
        if (!callbacks.canWrite(viewer, accessories)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        if (cursorEmpty) {
            ItemStack removed = accessories.remove(slotInstanceId);
            click.setCursor(removed);
            commit(session, viewer);
            return;
        }
        if (!accepts(viewer, slotInstanceId, cursor)) {
            return;
        }
        ItemStack previous = accessories.put(slotInstanceId, cursor);
        click.setCursor(previous);
        commit(session, viewer);
    }

    private void handleOrphanClick(GuiSession session,
            GuiClickContext click,
            Player viewer,
            GuiTemplate.ResolvedSlot slot) {
        ItemStack cursor = click.cursorItem();
        if (cursor != null && !cursor.getType().isAir()) {
            callbacks.reject(viewer, "gui.orphan_read_only", Map.of());
            return;
        }
        String key = guiService.orphanKeyAt(accessories, slot.inventorySlot());
        if (Texts.isBlank(key)) {
            return;
        }
        if (!callbacks.canWrite(viewer, accessories)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        ItemStack removed = accessories.remove(key);
        click.setCursor(removed);
        commit(session, viewer);
    }

    /**
     * Applies the two insertion rules: the item's declared slot, then the duplicate restriction.
     *
     * <p>Both refusals are reported. A silently refused insertion is the failure mode the research on
     * comparable plugins flagged repeatedly: the player concludes the plugin is broken rather than that
     * the item does not belong there.
     */
    private boolean accepts(Player viewer, String slotInstanceId, ItemStack candidate) {
        String requiredSlot = declaredSlot(candidate);
        if (!AccessoryPartRegistry.matchesAccessorySlot(slotInstanceId, requiredSlot)) {
            callbacks.reject(viewer, "gui.slot_mismatch", Map.of(
                    "slot", slotInstanceId,
                    "required", requiredSlot
            ));
            return false;
        }
        String conflict = uniqueService.findConflict(accessories, candidate, slotInstanceId);
        if (Texts.isNotBlank(conflict)) {
            callbacks.reject(viewer, "gui.unique_conflict", Map.of(
                    "slot", slotInstanceId,
                    "conflict", conflict
            ));
            return false;
        }
        return true;
    }

    /**
     * Reads the slot an item declares it belongs in.
     *
     * <p>Uses the EmakiSkills PDC codec, whose {@code active_slot} key is shared with EmakiAttribute, so
     * one declaration governs both. Blank means the item is unrestricted.
     */
    private String declaredSlot(ItemStack candidate) {
        String declared = AccessorySlotDeclaration.read(candidate);
        return Texts.isBlank(declared) ? EquipmentSlotMatcher.SLOT_ALL : declared;
    }

    private void commit(GuiSession session, Player viewer) {
        callbacks.onContentsChanged(viewer, accessories);
        guiService.refresh(session);
    }

    /**
     * Rejects click actions that bypass cursor semantics.
     *
     * <p>Mirrors EmakiStorage's guard deliberately: these five actions move items without the cursor
     * being the single source of truth, which is where duplication bugs in this plugin category come
     * from.
     */
    private boolean rejectedClick(GuiClickType clickType) {
        return switch (clickType) {
            case DOUBLECLICK, NUMBER_KEY, SWAP_OFFHAND, DROP, CONTROL_DROP -> true;
            default -> false;
        };
    }
}
