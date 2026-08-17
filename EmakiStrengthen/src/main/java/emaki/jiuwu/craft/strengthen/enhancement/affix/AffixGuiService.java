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

/**
 * 词条强化 GUI 的入口服务。
 *
 * <p>只由显式的 {@code affix} 子命令触发，不接管 {@code /emakistrengthen open}——整件星级强化的
 * 入口、模板与结算保持原样（计划 3.1）。
 *
 * <p><strong>线程：</strong>{@link #open} 必须在玩家所属实体线程调用。
 */
public final class AffixGuiService {

    /** 词条 GUI 的模板 ID，对应 {@code gui/affix_strengthen_gui.yml}。 */
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
            AffixTargetProvider targetProvider,
            AffixLayerCodec layerCodec) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.threadOwnership = threadOwnership;
        this.selectionService = selectionService;
        this.renderer = new AffixGuiRenderer(plugin);
        this.interactionController = new AffixGuiInteractionController(
                plugin, renderer, selectionService, targetProvider, layerCodec);
    }

    /**
     * 打开词条强化 GUI。
     *
     * @param player   发起玩家
     * @param recipeId 指定配方 ID；为空时自动解析唯一的 {@code mode: affix} 配方
     * @return 是否成功打开
     */
    public boolean open(Player player, String recipeId) {
        if (player == null || guiService == null || plugin.enhancementAttemptService() == null) {
            return false;
        }
        if (threadOwnership != null && !threadOwnership.isEntityOwned(player)) {
            return false;
        }
        if (plugin.attemptService() != null && !plugin.attemptService().accepting()) {
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

    /** 关闭全部词条 GUI 会话，物品由各会话的 onClose 归还。 */
    public void clearAllSessions() {
        clearAllSessionsAsync().exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to close all affix GUI sessions: " + throwable.getMessage());
            return null;
        });
    }

    /** {@return 关闭全部词条 GUI 会话的异步结果} */
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
            // 候选不唯一时不猜测：猜错会让玩家用另一套费用/概率强化（计划 3.1）。
            plugin.messageService().send(player, "command.affix.ambiguous_recipe", Map.of(
                    "recipes", String.join(", ", candidates.stream().map(EnhancementRecipe::id).toList())
            ));
            return null;
        }
        return candidates.getFirst();
    }

    /** {@return 该玩家当前的词条 GUI 会话；没有时为 {@code null}} */
    AffixGuiSession session(Player player) {
        return sessions.get(player);
    }
}
