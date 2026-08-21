package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;

public final class AffixGuiService {

    public static final String TEMPLATE_ID = "affix_strengthen_gui";

    private final EmakiStrengthenPlugin plugin;
    private final GuiService guiService;
    private final ThreadOwnership threadOwnership;
    private final AffixSelectionService selectionService;
    private final AffixGuiRenderer renderer;
    private final AffixGuiInteractionController interactionController;
    private final PlayerSessionMap<AffixGuiSession> sessions = new PlayerSessionMap<>(AffixGuiSession::player);

    public AffixGuiService(EmakiStrengthenPlugin plugin,
            GuiService guiService,
            ThreadOwnership threadOwnership,
            AffixSelectionService selectionService,
            AffixLayerCodec layerCodec) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.threadOwnership = threadOwnership;
        this.selectionService = selectionService;
        this.renderer = new AffixGuiRenderer(plugin);
        this.interactionController = new AffixGuiInteractionController(
                plugin, renderer, selectionService, layerCodec);
    }

    public boolean open(Player player, String recipeId) {
        if (player == null || guiService == null || plugin.enhancementAttemptService() == null) {
            return false;
        }
        if (threadOwnership != null && !threadOwnership.isEntityOwned(player)) {
            return false;
        }
        if (!plugin.enhancementAttemptService().accepting()) {
            plugin.messageService().send(player, "gui.open_failed");
            return false;
        }
        EnhancementRecipe recipe = resolveRecipe(player, recipeId);
        if (recipe == null) {
            return false;
        }
        GuiTemplate template = plugin.guiTemplateLoader() == null
                ? null
                : plugin.guiTemplateLoader().get(TEMPLATE_ID);
        if (template == null) {
            plugin.messageService().send(player, "gui.open_failed");
            return false;
        }
        AffixGuiSession state = new AffixGuiSession(player, recipe);
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(),
                (guiSession, slot) -> renderer.renderSlot(state, slot),
                interactionController.createSessionHandler(state, () -> sessions.remove(player, state))
        ));
        if (session == null) {
            return false;
        }
        state.setGuiSession(session);
        sessions.put(state);
        interactionController.refresh(state);
        return true;
    }

    public void clearAllSessions() {
        clearAllSessionsAsync().exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to close all affix GUI sessions: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> clearAllSessionsAsync() {
        return guiService.closeAllAsync().whenComplete((_, _) -> {
            sessions.clear();
            selectionService.clearAll();
        });
    }

    private EnhancementRecipe resolveRecipe(Player player, String recipeId) {
        if (plugin.enhancementRecipeLoader() == null) {
            plugin.messageService().send(player, "strengthen.error.no_recipe");
            return null;
        }
        if (Texts.isNotBlank(recipeId)) {
            EnhancementRecipe recipe = plugin.enhancementRecipeLoader().get(recipeId.trim());
            if (recipe == null || !AffixTargetProvider.PROVIDER_ID.equals(Texts.lower(recipe.target().provider()))) {
                plugin.messageService().send(player, "command.affix.recipe_not_found",
                        Map.of("recipe", recipeId.trim()));
                return null;
            }
            return recipe;
        }
        List<EnhancementRecipe> candidates = plugin.enhancementRecipeLoader().all().values().stream()
                .filter(recipe -> AffixTargetProvider.PROVIDER_ID.equals(Texts.lower(recipe.target().provider())))
                .toList();
        if (candidates.isEmpty()) {
            plugin.messageService().send(player, "command.affix.no_recipe");
            return null;
        }
        if (candidates.size() > 1) {
            plugin.messageService().send(player, "command.affix.ambiguous_recipe", Map.of(
                    "recipes", String.join(", ", candidates.stream().map(EnhancementRecipe::id).toList())
            ));
            return null;
        }
        return candidates.getFirst();
    }

    AffixGuiSession session(Player player) {
        return sessions.get(player);
    }
}
