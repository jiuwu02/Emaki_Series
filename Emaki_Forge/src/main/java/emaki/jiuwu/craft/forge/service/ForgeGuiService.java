package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ForgeGuiService {

    public static final class ForgeGuiSession {
        private final Player player;
        private final String templateId;
        private Recipe recipe;
        private Recipe previewRecipe;
        private GuiSession guiSession;
        private final Map<Integer, ItemStack> blueprintItems = new LinkedHashMap<>();
        private ItemStack targetItem;
        private final Map<Integer, ItemStack> requiredMaterialItems = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> optionalMaterialItems = new LinkedHashMap<>();
        private int currentCapacity;
        private int maxCapacity;
        private boolean processing;
        private boolean forgeCompleted;
        private String previewFingerprint = "";
        private long previewSeed = ThreadLocalRandom.current().nextLong();
        private long previewForgedAt = System.currentTimeMillis();
        private ForgeService.PreparedForge preparedForge;

        public ForgeGuiSession(Player player, Recipe recipe, String templateId) {
            this.player = player;
            this.recipe = recipe;
            this.templateId = templateId;
        }

        public Player player() {
            return player;
        }

        public String templateId() {
            return templateId;
        }

        public Recipe recipe() {
            return recipe;
        }

        public void setRecipe(Recipe recipe) {
            this.recipe = recipe;
        }

        public Recipe previewRecipe() {
            return previewRecipe;
        }

        public void setPreviewRecipe(Recipe previewRecipe) {
            this.previewRecipe = previewRecipe;
        }

        public GuiSession guiSession() {
            return guiSession;
        }

        public void setGuiSession(GuiSession guiSession) {
            this.guiSession = guiSession;
        }

        public Map<Integer, ItemStack> blueprintItems() {
            return blueprintItems;
        }

        public ItemStack targetItem() {
            return targetItem;
        }

        public void setTargetItem(ItemStack targetItem) {
            this.targetItem = targetItem;
        }

        public Map<Integer, ItemStack> requiredMaterialItems() {
            return requiredMaterialItems;
        }

        public Map<Integer, ItemStack> optionalMaterialItems() {
            return optionalMaterialItems;
        }

        public int currentCapacity() {
            return currentCapacity;
        }

        public void setCurrentCapacity(int currentCapacity) {
            this.currentCapacity = currentCapacity;
        }

        public int maxCapacity() {
            return maxCapacity;
        }

        public void setMaxCapacity(int maxCapacity) {
            this.maxCapacity = maxCapacity;
        }

        public boolean processing() {
            return processing;
        }

        public void setProcessing(boolean processing) {
            this.processing = processing;
        }

        public boolean forgeCompleted() {
            return forgeCompleted;
        }

        public void setForgeCompleted(boolean forgeCompleted) {
            this.forgeCompleted = forgeCompleted;
        }

        public String previewFingerprint() {
            return previewFingerprint;
        }

        public void setPreviewFingerprint(String previewFingerprint) {
            this.previewFingerprint = previewFingerprint == null ? "" : previewFingerprint;
        }

        public long previewSeed() {
            return previewSeed;
        }

        public long previewForgedAt() {
            return previewForgedAt;
        }

        public void refreshPreviewRoll() {
            this.previewSeed = ThreadLocalRandom.current().nextLong();
            this.previewForgedAt = System.currentTimeMillis();
        }

        public ForgeService.PreparedForge preparedForge() {
            return preparedForge;
        }

        public void setPreparedForge(ForgeService.PreparedForge preparedForge) {
            this.preparedForge = preparedForge;
        }

        public void clearStoredItems() {
            blueprintItems.clear();
            requiredMaterialItems.clear();
            optionalMaterialItems.clear();
            targetItem = null;
            preparedForge = null;
        }

        public ForgeService.GuiItems toGuiItems() {
            return new ForgeService.GuiItems(
                targetItem == null ? null : targetItem.clone(),
                copyItems(blueprintItems),
                copyItems(requiredMaterialItems),
                copyItems(optionalMaterialItems)
            );
        }

        private static Map<Integer, ItemStack> copyItems(Map<Integer, ItemStack> source) {
            Map<Integer, ItemStack> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
                ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(entry.getValue());
                if (itemStack != null) {
                    result.put(entry.getKey(), itemStack);
                }
            }
            return result;
        }
    }

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final ForgeGuiSessionStore sessionStore;
    private final ForgeGuiStateSupport stateSupport;
    private final ForgeGuiRenderer renderer;
    private final ForgeGuiInteractionController interactionController;

    public ForgeGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.sessionStore = new ForgeGuiSessionStore();
        this.stateSupport = new ForgeGuiStateSupport(plugin);
        this.renderer = new ForgeGuiRenderer(plugin, stateSupport);
        this.interactionController = new ForgeGuiInteractionController(plugin, sessionStore, stateSupport, renderer);
    }

    public boolean openForgeGui(Player player, Recipe recipe) {
        if (player == null) {
            return false;
        }
        String templateId = stateSupport.resolveTemplateId(recipe);
        var template = stateSupport.prepareTemplate(recipe, templateId);
        if (template == null) {
            return false;
        }
        ForgeGuiSession state = new ForgeGuiSession(player, recipe, templateId);
        stateSupport.refreshDerivedValues(state);
        GuiSession session = guiService.open(new GuiOpenRequest(
            plugin,
            player,
            template,
            renderer.titleReplacements(state),
            plugin.itemIdentifierService()::createItem,
            (guiSession, slot) -> renderer.renderSlot(state, slot),
            interactionController.createSessionHandler(state)
        ));
        if (session == null) {
            return false;
        }
        state.setGuiSession(session);
        sessionStore.put(state);
        return true;
    }

    public boolean openGeneralForgeGui(Player player) {
        return openForgeGui(player, null);
    }

    public ForgeGuiSession getSession(Player player) {
        return sessionStore.get(player);
    }

    public void removeSession(Player player) {
        sessionStore.remove(player);
    }

    public void clearAllSessions() {
        sessionStore.clear();
    }
}
