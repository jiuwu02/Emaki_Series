package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiClickType;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.config.GuiSettings;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.dismantle.DismantleGuiInteractionController;
import emaki.jiuwu.craft.station.dismantle.DismantleGuiRenderer;
import emaki.jiuwu.craft.station.dismantle.DismantleRecipeDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleService;
import emaki.jiuwu.craft.station.dismantle.DismantleStationDefinition;
import emaki.jiuwu.craft.station.dismantle.DismantleStationRegistry;
import emaki.jiuwu.craft.station.dismantle.DismantleViewState;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.material.StorageChannel;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.PlayerQueues;
import emaki.jiuwu.craft.station.queue.QueueCapacity;
import emaki.jiuwu.craft.station.queue.QueueService;
import emaki.jiuwu.craft.station.queue.QueueUnlockService;
import emaki.jiuwu.craft.station.queue.StationCraftService;
import emaki.jiuwu.craft.station.queue.StationQueueUnlockService;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.material.OutputDelivery;

final class StationGuiServiceDelegate {

    private final StationGuiService ownerRef;

    StationGuiServiceDelegate(StationGuiService ownerRef) {
        this.ownerRef = ownerRef;
    }

    List<StationCatalogEntry> catalogEntries(StationViewState state) {
        List<StationCatalogEntry> entries = new ArrayList<>();
        Player viewer = state.viewer();
        for (RecipeDefinition recipe : ownerRef.registrySupplier.get().recipesOf(state.station().id())) {
            if (!recipe.visible()) {
                continue;
            }
            if (recipe.hasPermission() && !viewer.hasPermission(recipe.permission())) {
                continue;
            }
            entries.add(new StationCatalogEntry(recipe, unlocked(viewer, recipe)));
        }
        return entries;
    }

    boolean unlocked(Player viewer, RecipeDefinition recipe) {
        if (!recipe.hasDisplayCondition()) {
            return true;
        }
        return ConditionEvaluator.evaluate(recipe.displayCondition(),
                text -> ownerRef.placeholders.apply(viewer, text),
                ConditionContext.of(viewer));
    }

    int purchasedOf(StationViewState state) {
        return ownerRef.unlockService == null
                ? 0
                : ownerRef.unlockService.purchased(state.viewer().getUniqueId(), state.station().id());
    }

}
