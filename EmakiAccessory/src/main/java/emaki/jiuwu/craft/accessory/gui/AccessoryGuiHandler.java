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

public final class AccessoryGuiHandler implements GuiSessionHandler {

    public interface Callbacks {

        Plugin plugin();

        boolean canWrite(Player viewer, PlayerAccessories accessories);

        void onContentsChanged(Player viewer, PlayerAccessories accessories);

        void onWindowClosed(Player viewer, PlayerAccessories accessories);

        void reject(Player viewer, String messageKey, Map<String, ?> replacements);
    }

    private final Callbacks callbacks;
    private final AccessoryGuiService guiService;
    private final AccessoryUniqueService uniqueService;
    private final PlayerAccessories accessories;
    private boolean closed;

    public AccessoryGuiHandler(Callbacks callbacks,
            AccessoryGuiService guiService,
            AccessoryUniqueService uniqueService,
            PlayerAccessories accessories) {
        this.callbacks = callbacks;
        this.guiService = guiService;
        this.uniqueService = uniqueService;
        this.accessories = accessories;
    }

    public Plugin owner() {
        return callbacks.plugin();
    }

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

            }
        }
    }

    @Override
    public void onPlayerInventoryClick(GuiSession session, GuiClickContext click) {

        if (click.isBlockedTransfer() || rejectedClick(click.clickType())) {
            click.setCancelled(true);
            callbacks.reject(click.viewer(), "gui.transfer_unsupported", Map.of());
        }
    }

    @Override
    public void onClose(GuiSession session, GuiCloseContext close) {

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

    private String declaredSlot(ItemStack candidate) {
        String declared = AccessorySlotDeclaration.read(candidate);
        return Texts.isBlank(declared) ? EquipmentSlotMatcher.SLOT_ALL : declared;
    }

    private void commit(GuiSession session, Player viewer) {
        callbacks.onContentsChanged(viewer, accessories);
        guiService.refresh(session);
    }

    private boolean rejectedClick(GuiClickType clickType) {
        return switch (clickType) {
            case DOUBLECLICK, NUMBER_KEY, SWAP_OFFHAND, DROP, CONTROL_DROP -> true;
            default -> false;
        };
    }
}
