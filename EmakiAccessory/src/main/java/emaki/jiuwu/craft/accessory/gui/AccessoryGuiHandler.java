package emaki.jiuwu.craft.accessory.gui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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

        PlayerAccessories view(UUID targetId);

        boolean edit(UUID targetId, long expectedGeneration, Consumer<PlayerAccessories> mutation);

        boolean canWrite(Player viewer, UUID targetId);

        boolean canUsePage(Player viewer, String pageId);

        void onWindowClosed(Player viewer, UUID targetId);

        void onPageSwitchRequested(Player viewer, UUID targetId, String pageId);

        void reject(Player viewer, String messageKey, Map<String, ?> replacements);
    }

    private final Callbacks callbacks;
    private final AccessoryGuiService guiService;
    private final AccessoryUniqueService uniqueService;
    private final UUID targetId;
    private final long generation;
    private final String pageId;
    private boolean closed;
    private boolean switchingPage;

    public AccessoryGuiHandler(Callbacks callbacks,
            AccessoryGuiService guiService,
            AccessoryUniqueService uniqueService,
            UUID targetId,
            long generation,
            String pageId) {
        this.callbacks = callbacks;
        this.guiService = guiService;
        this.uniqueService = uniqueService;
        this.targetId = targetId;
        this.generation = generation;
        this.pageId = Texts.normalizeId(pageId);
    }

    public Plugin owner() {
        return callbacks.plugin();
    }

    public UUID targetId() {
        return targetId;
    }

    public PlayerAccessories view() {
        return callbacks.view(targetId);
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
        callbacks.onWindowClosed(session == null ? null : session.viewer(), targetId);
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
        ItemStack stored = storedItem(slotInstanceId);
        boolean slotEmpty = stored == null || stored.getType().isAir();

        if (cursorEmpty && slotEmpty) {
            return;
        }
        if (!callbacks.canWrite(viewer, targetId)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        if (cursorEmpty) {
            if (!edit(accessories -> accessories.remove(pageId, slotInstanceId))) {
                stale(viewer);
                return;
            }
            click.setCursor(stored);
            commit(session);
            return;
        }
        if (!accepts(viewer, slotInstanceId, cursor)) {
            return;
        }
        if (!edit(accessories -> accessories.put(pageId, slotInstanceId, cursor))) {
            stale(viewer);
            return;
        }
        click.setCursor(stored);
        commit(session);
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
        String key = guiService.orphanKeyAt(view(), pageId, slot.inventorySlot());
        if (Texts.isBlank(key)) {
            return;
        }
        if (!callbacks.canWrite(viewer, targetId)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        PlayerAccessories view = view();
        ItemStack removed = view == null ? null : view.itemAt(pageId, key);
        if (!edit(accessories -> accessories.remove(pageId, key))) {
            stale(viewer);
            return;
        }
        click.setCursor(removed);
        commit(session);
    }

    private void handleEnableClick(GuiSession session, Player viewer) {
        if (!callbacks.canWrite(viewer, targetId)) {
            callbacks.reject(viewer, "gui.read_only", Map.of());
            return;
        }
        PlayerAccessories view = view();
        String enabled = view == null ? "" : Texts.normalizeId(view.enabledPage());
        if (enabled.equals(pageId)) {
            callbacks.reject(viewer, "gui.page_already_enabled", Map.of("page", pageId));
            return;
        }
        if (!edit(accessories -> accessories.enabledPage(pageId))) {
            stale(viewer);
            return;
        }
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
        callbacks.onPageSwitchRequested(viewer, targetId, target);
    }

    private boolean accepts(Player viewer, String slotInstanceId, ItemStack candidate) {
        Set<String> declared = AccessorySlotDeclarations.read(candidate, callbacks.slotSources());
        if (declared.isEmpty()) {
            callbacks.reject(viewer, "gui.not_declared", Map.of());
            return false;
        }
        if (!AccessorySlotDeclarations.matchesAny(slotInstanceId, declared)) {
            callbacks.reject(viewer, "gui.slot_mismatch", Map.of(
                    "slot", slotInstanceId,
                    "required", AccessorySlotDeclarations.describe(declared)
            ));
            return false;
        }
        String conflict = uniqueService.findConflict(view(), pageId, candidate, slotInstanceId);
        if (Texts.isNotBlank(conflict)) {
            callbacks.reject(viewer, "gui.unique_conflict", Map.of(
                    "slot", slotInstanceId,
                    "conflict", conflict
            ));
            return false;
        }
        return true;
    }

    private ItemStack storedItem(String slotInstanceId) {
        PlayerAccessories view = view();
        return view == null ? null : view.itemAt(pageId, slotInstanceId);
    }

    private boolean edit(Consumer<PlayerAccessories> mutation) {
        return callbacks.edit(targetId, generation, mutation);
    }

    private void stale(Player viewer) {
        callbacks.reject(viewer, "gui.session_stale", Map.of());
    }

    private void commit(GuiSession session) {
        guiService.refresh(session);
    }

    private boolean rejectedClick(GuiClickType clickType) {
        return switch (clickType) {
            case DOUBLECLICK, NUMBER_KEY, SWAP_OFFHAND, DROP, CONTROL_DROP -> true;
            default -> false;
        };
    }
}