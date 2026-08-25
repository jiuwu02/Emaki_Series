package emaki.jiuwu.craft.accessory.gui;

import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.accessory.config.AccessorySlotSourceConfig;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPageRegistry;
import emaki.jiuwu.craft.accessory.service.AccessorySlotDeclarations;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
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

        AccessorySlotSourceConfig slotSources();

        AccessoryPageRegistry pageRegistry();

        boolean canWrite(Player viewer, PlayerAccessories accessories);

        boolean canUsePage(Player viewer, String pageId);

        void onContentsChanged(Player viewer, PlayerAccessories accessories);

        void onWindowClosed(Player viewer, PlayerAccessories accessories);

        void onEnabledPageChanged(Player viewer, PlayerAccessories accessories);

        void onPageSwitchRequested(Player viewer, PlayerAccessories accessories, String pageId);

        void reject(Player viewer, String messageKey, Map<String, ?> replacements);
    }

    private final Callbacks callbacks;
    private final AccessoryGuiService guiService;
    private final AccessoryUniqueService uniqueService;
    private final PlayerAccessories accessories;
    private final String pageId;
    private boolean closed;
    private boolean switchingPage;

    public AccessoryGuiHandler(Callbacks callbacks,
            AccessoryGuiService guiService,
            AccessoryUniqueService uniqueService,
            PlayerAccessories accessories,
            String pageId) {
        this.callbacks = callbacks;
        this.guiService = guiService;
        this.uniqueService = uniqueService;
        this.accessories = accessories;
        this.pageId = Texts.normalizeId(pageId);
    }

    public Plugin owner() {
        return callbacks.plugin();
    }

    public PlayerAccessories accessories() {
        return accessories;
    }

    public String pageId() {
        return pageId;
    }

    public void beginPageSwitch() {
        switchingPage = true;
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
            case AccessoryGuiService.TYPE_PAGE_ENABLE -> handleEnableClick(session, viewer);
            case AccessoryGuiService.TYPE_PAGE_SWITCH -> handleSwitchClick(viewer, slot);
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
        if (switchingPage) {
            return;
        }
        callbacks.onWindowClosed(session == null ? null : session.viewer(), accessories);
    }

    private void handleAccessoryClick(GuiSession session,
            GuiClickContext click,
            Player viewer,
            GuiTemplate.ResolvedSlot slot) {
        String slotInstanceId = guiService.slotInstanceAt(pageId, slot.inventorySlot());
        if (Texts.isBlank(slotInstanceId)) {
            return;
        }
        if (!callbacks.pageRegistry().declaresSlot(pageId, slotInstanceId)) {
            return;
        }
        ItemStack cursor = click.cursorItem();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        ItemStack stored = accessories.itemAt(pageId, slotInstanceId);
        boolean slotEmpty = stored == null || stored.getType().isAir();

        if (cursorEmpty && slotEmpty) {
            return;
        }
        if (!callbacks.canWrite(viewer, accessories)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        if (cursorEmpty) {
            ItemStack removed = accessories.remove(pageId, slotInstanceId);
            click.setCursor(removed);
            commit(session, viewer);
            return;
        }
        if (!accepts(viewer, slotInstanceId, cursor)) {
            return;
        }
        ItemStack previous = accessories.put(pageId, slotInstanceId, cursor);
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
        String key = guiService.orphanKeyAt(accessories, pageId, slot.inventorySlot());
        if (Texts.isBlank(key)) {
            return;
        }
        if (!callbacks.canWrite(viewer, accessories)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        ItemStack removed = accessories.remove(pageId, key);
        click.setCursor(removed);
        commit(session, viewer);
    }

    private void handleEnableClick(GuiSession session, Player viewer) {
        if (!callbacks.canWrite(viewer, accessories)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        if (Texts.normalizeId(accessories.enabledPage()).equals(pageId)) {
            callbacks.reject(viewer, "gui.page_already_enabled", Map.of("page", pageId));
            return;
        }
        accessories.enabledPage(pageId);
        callbacks.onEnabledPageChanged(viewer, accessories);
        guiService.refresh(session);
    }

    private void handleSwitchClick(Player viewer, GuiTemplate.ResolvedSlot slot) {
        String target = guiService.switchTargetAt(pageId, slot.inventorySlot());
        if (Texts.isBlank(target) || target.equals(pageId)) {
            return;
        }
        if (!callbacks.pageRegistry().hasPage(target)) {
            callbacks.reject(viewer, "gui.page_unknown", Map.of("page", target));
            return;
        }
        if (!callbacks.canUsePage(viewer, target)) {
            callbacks.reject(viewer, "gui.page_no_permission", Map.of("page", target));
            return;
        }
        callbacks.onPageSwitchRequested(viewer, accessories, target);
    }

    private boolean accepts(Player viewer, String slotInstanceId, ItemStack candidate) {
        Set<String> declared = AccessorySlotDeclarations.read(candidate, callbacks.slotSources());
        if (!AccessorySlotDeclarations.matchesAny(slotInstanceId, declared)) {
            callbacks.reject(viewer, "gui.slot_mismatch", Map.of(
                    "slot", slotInstanceId,
                    "required", AccessorySlotDeclarations.describe(declared)
            ));
            return false;
        }
        String conflict = uniqueService.findConflict(accessories, pageId, candidate, slotInstanceId);
        if (Texts.isNotBlank(conflict)) {
            callbacks.reject(viewer, "gui.unique_conflict", Map.of(
                    "slot", slotInstanceId,
                    "conflict", conflict
            ));
            return false;
        }
        return true;
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
