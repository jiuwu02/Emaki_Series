package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.QueueEntry;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * Builds the contents of every dynamic slot in a station window.
 *
 * <p>Dynamic slots return a built stack; everything else returns {@code null} so CoreLib falls back to the
 * layout's own item definition. That keeps decorative slots entirely in the administrator's hands.
 *
 * <p>Amounts shown here follow {@link AmountDisplay}'s discipline: the stack size stays inside vanilla's
 * legal range and the real {@code long} lives in lore. None of these stacks may ever be handed to a player.
 */
public final class StationGuiRenderer {

    private final ItemSourceService itemSourceService;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;
    private final ConfiguredGuiSupport guiSupport;
    private final StationMaterialView materialView;

    /**
     * Creates the renderer.
     *
     * @param itemSourceService   CoreLib's item-source service, used to build material icons
     * @param itemServiceSupplier supplies CoreLib's configured-item service
     * @param guiSupport          reads the layout's virtual items and texts
     * @param materialView        supplies the available counts for the active channel
     */
    public StationGuiRenderer(ItemSourceService itemSourceService,
            Supplier<ConfiguredItemService> itemServiceSupplier,
            ConfiguredGuiSupport guiSupport,
            StationMaterialView materialView) {
        this.itemSourceService = itemSourceService;
        this.itemServiceSupplier = itemServiceSupplier;
        this.guiSupport = guiSupport;
        this.materialView = materialView;
    }

    /**
     * Renders one slot.
     *
     * @param session      the station session
     * @param resolvedSlot the slot being rendered
     * @return the stack to place, or {@code null} to fall back to the layout definition
     */
    public ItemStack render(StationGuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (session == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = StationSlotType.normalize(slot.type());
        if (type.isEmpty()) {
            type = StationSlotType.normalize(slot.key());
        }
        return switch (type) {
            case StationSlotType.INPUT -> renderInput(session, resolvedSlot);
            case StationSlotType.RECIPE_PREVIEW -> renderRecipePreview(session, slot);
            case StationSlotType.MATERIAL_LIST -> renderMaterialEntry(session, slot, resolvedSlot);
            case StationSlotType.CHANNEL_TOGGLE -> renderChannelToggle(session, slot);
            case StationSlotType.BATCH_MULTIPLIER -> renderBatch(session, slot);
            case StationSlotType.MAX_CRAFTABLE -> renderMaxCraftable(session, slot);
            case StationSlotType.OUTPUT_TOGGLE -> renderOutputToggle(session, slot);
            case StationSlotType.QUEUE_VIEW -> renderQueueEntry(session, slot, resolvedSlot);
            case StationSlotType.CONFIRM -> renderConfirm(session, slot);
            default -> null;
        };
    }

    /**
     * Builds the title placeholders for a session.
     *
     * @param session the station session
     * @return the substitutions
     */
    public Map<String, Object> titleReplacements(StationGuiSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("station_name", session.station().displayName());
        values.put("station", session.station().id());
        values.put("player", session.viewer().getName());
        return values;
    }

    private ItemStack renderInput(StationGuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        ItemStack held = session.inputs().get(resolvedSlot.inventorySlot());
        if (held == null || held.getType().isAir()) {
            return null;
        }
        return held.clone();
    }

    private ItemStack renderRecipePreview(StationGuiSession session, GuiSlot slot) {
        RecipeDefinition recipe = session.selectedRecipe();
        if (recipe == null) {
            return guiSupport.build(session.station().layoutId(), "virtual_items.no_recipe",
                    Map.of(), fallbackNoRecipe());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe_name", recipe.displayName());
        values.put("recipe", recipe.id());
        values.put("duration", formatDuration(
                recipe.effectiveDurationMillis(session.station().queueSettings().speedMultiplier())));
        values.put("result_name", displayNameOf(recipe));
        values.put("result_amount", AmountDisplay.compact(totalOutput(recipe, session.batch())));
        values.put("batch", AmountDisplay.compact(session.batch()));
        values.put("alternatives", String.valueOf(session.currentMatch().otherCount()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderMaterialEntry(StationGuiSession session,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        RecipeDefinition recipe = session.selectedRecipe();
        if (recipe == null) {
            return null;
        }
        List<MaterialRequirement> requirements = recipe.requirements();
        int pageSize = Math.max(1, slot.slots().size());
        int offset = session.materialPage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= requirements.size()) {
            return null;
        }
        MaterialRequirement requirement = requirements.get(offset);
        ItemSourceRef primary = requirement.sources().getFirst();
        ItemStack icon = itemSourceService == null ? null : itemSourceService.createItem(primary, 1);
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.BARRIER);
        }
        long required = requirement.totalFor(session.batch());
        long owned = materialView.ownedOf(session, requirement);
        icon.setAmount(AmountDisplay.renderedStackSize(required));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("required", AmountDisplay.compact(required));
        values.put("required_exact", AmountDisplay.precise(required));
        values.put("owned", AmountDisplay.compact(owned));
        values.put("owned_exact", AmountDisplay.precise(owned));
        values.put("satisfied", owned >= required ? "true" : "false");
        values.put("material", displayNameOf(primary));
        values.put("page", String.valueOf(session.materialPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(requirements.size(), pageSize)));
        String path = owned >= required
                ? "virtual_items.material_satisfied"
                : "virtual_items.material_missing";
        return guiSupport.apply(session.station().layoutId(), path, icon, values);
    }

    private ItemStack renderChannelToggle(StationGuiSession session, GuiSlot slot) {
        boolean storageReady = materialView.storageUsable() && session.station().storageChannel();
        String stateText = switch (session.channel()) {
            case STORAGE -> guiSupport.text(session.station().layoutId(), "texts.channel_state.storage",
                    "Storage", Map.of());
            case BACKPACK -> storageReady
                    ? guiSupport.text(session.station().layoutId(), "texts.channel_state.backpack",
                            "Backpack", Map.of())
                    : guiSupport.text(session.station().layoutId(),
                            "texts.channel_state.storage_unavailable", "Backpack", Map.of());
        };
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("channel", session.channel().token());
        values.put("channel_state", stateText);
        values.put("storage_available", storageReady ? "true" : "false");
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderBatch(StationGuiSession session, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("batch", AmountDisplay.compact(session.batch()));
        values.put("batch_exact", AmountDisplay.precise(session.batch()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderMaxCraftable(StationGuiSession session, GuiSlot slot) {
        long max = session.currentMatch().maxBatch();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("max", AmountDisplay.compact(max));
        values.put("max_exact", AmountDisplay.precise(max));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderOutputToggle(StationGuiSession session, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("output", session.outputRouting().token());
        values.put("output_state", guiSupport.text(session.station().layoutId(),
                "texts.output_state." + session.outputRouting().token(),
                session.outputRouting().token(), Map.of()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderQueueEntry(StationGuiSession session,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        CraftQueue queue = materialView.queueOf(session);
        if (queue == null) {
            return null;
        }
        List<QueueEntry> entries = queue.entries();
        int offset = resolvedSlot.slotIndex();
        if (offset < 0 || offset >= entries.size()) {
            return null;
        }
        QueueEntry entry = entries.get(offset);
        long remaining = entry.remainingMillis(session.station().progressMode(),
                System.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("index", String.valueOf(offset + 1));
        values.put("recipe", entry.recipeId());
        values.put("batch", AmountDisplay.compact(entry.batch()));
        values.put("state", entry.state().token());
        values.put("remaining", formatDuration(remaining));
        String path = entry.state() == QueueEntryState.PENDING_CLAIM
                ? "virtual_items.queue_pending"
                : "virtual_items.queue_entry";
        ItemStack base = GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
        return guiSupport.apply(session.station().layoutId(), path, base, values);
    }

    private ItemStack renderConfirm(StationGuiSession session, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("batch", AmountDisplay.compact(session.batch()));
        values.put("block_reason", session.blockReason());
        if (!session.blockReason().isEmpty()) {
            return guiSupport.build(session.station().layoutId(), "virtual_items.confirm_blocked",
                    values, fallbackBlocked(session.blockReason()));
        }
        RecipeDefinition recipe = session.selectedRecipe();
        values.put("recipe", recipe == null ? "" : recipe.id());
        values.put("recipe_name", recipe == null ? "" : recipe.displayName());
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private String displayNameOf(RecipeDefinition recipe) {
        if (recipe.outputs().isEmpty()) {
            return recipe.displayName();
        }
        return displayNameOf(recipe.outputs().getFirst().source());
    }

    private String displayNameOf(ItemSourceRef source) {
        if (itemSourceService == null) {
            return source.identifier();
        }
        String name = itemSourceService.displayName(source);
        return name == null || name.isBlank() ? source.identifier() : name;
    }

    private static long totalOutput(RecipeDefinition recipe, long batch) {
        if (recipe.outputs().isEmpty()) {
            return 0L;
        }
        return recipe.outputs().getFirst().totalFor(batch);
    }

    /**
     * Formats a duration as {@code h:mm:ss} or {@code m:ss}.
     *
     * @param millis the duration
     * @return the formatted text
     */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static ConfiguredItemDefinition fallbackNoRecipe() {
        return new ConfiguredItemDefinition("GRAY_DYE", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<gray>No matching recipe</gray>")));
    }

    private static ConfiguredItemDefinition fallbackBlocked(String reason) {
        List<String> lore = new ArrayList<>();
        lore.add("<red>" + reason + "</red>");
        return new ConfiguredItemDefinition("BARRIER", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<red>Cannot craft</red>"),
                "minecraft:lore", ItemComponentPatch.set(lore)));
    }
}
