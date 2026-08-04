package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgeGuiState;
import emaki.jiuwu.craft.forge.ForgeRuntimeSnapshot;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class RecipeBookGuiService {

    private static final class BookSession {

        private final Player player;
        private final int page;
        private final int totalPages;
        private final List<Recipe> visibleRecipes;
        private final ForgeRuntimeSnapshot runtimeSnapshot;

        private BookSession(Player player, int page, int totalPages, List<Recipe> visibleRecipes,
                            ForgeRuntimeSnapshot runtimeSnapshot) {
            this.player = player;
            this.page = page;
            this.totalPages = totalPages;
            this.visibleRecipes = List.copyOf(visibleRecipes);
            this.runtimeSnapshot = runtimeSnapshot;
        }
    }

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final ConfiguredGuiSupport guiSupport;
    private final Map<UUID, BookSession> openBooks = new ConcurrentHashMap<>();

    public RecipeBookGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.guiSupport = new ConfiguredGuiSupport(plugin);
    }

    public boolean openRecipeBook(Player player) {
        return openRecipeBook(player, 0);
    }

    public boolean openRecipeBook(Player player, int page) {
        return openRecipeBook(player, page, plugin.runtimeSnapshot());
    }

    private boolean openRecipeBook(Player player, int page, ForgeRuntimeSnapshot runtime) {
        if (player == null || runtime == null) {
            return false;
        }
        ForgeGuiState guiState = runtime.guiState();
        if (guiState != ForgeGuiState.READY) {
            if (runtime.messageService() != null) {
                runtime.messageService().send(player,
                        "forge.error.runtime." + guiState.name().toLowerCase(java.util.Locale.ROOT));
            }
            return false;
        }
        List<Recipe> recipes = new ArrayList<>(runtime.recipeLoader().all().values());
        if (recipes.isEmpty()) {
            runtime.messageService().send(player, "forge.error.no_recipe");
            return false;
        }
        GuiTemplate template = resolveTemplate(runtime);
        List<Integer> recipeSlots = slotsForType(template, "recipe_list");
        if (recipeSlots.isEmpty()) {
            return false;
        }
        int itemsPerPage = recipeSlots.size();
        int totalPages = Math.max(1, (recipes.size() + itemsPerPage - 1) / itemsPerPage);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * itemsPerPage;
        List<Recipe> visibleRecipes = new ArrayList<>();
        for (int index = 0; index < itemsPerPage && start + index < recipes.size(); index++) {
            visibleRecipes.add(recipes.get(start + index));
        }
        if (!isRuntimeCurrent(runtime)) {
            return false;
        }
        BookSession state = new BookSession(player, currentPage, totalPages, visibleRecipes, runtime);
        GuiSession guiSession = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of("page", currentPage + 1, "pages", totalPages),
                (session, slot) -> renderSlot(state, slot),
                new BookSessionHandler(state)
        ));
        if (guiSession == null) {
            return false;
        }
        if (!isRuntimeCurrent(runtime)
                || player.getOpenInventory().getTopInventory() != guiSession.getInventory()) {
            player.closeInventory();
            return false;
        }
        UUID playerId = player.getUniqueId();
        openBooks.put(playerId, state);
        if (!isRuntimeCurrent(runtime)
                || openBooks.get(playerId) != state
                || player.getOpenInventory().getTopInventory() != guiSession.getInventory()) {
            openBooks.remove(playerId, state);
            player.closeInventory();
            return false;
        }
        return true;
    }

    private boolean isRuntimeCurrent(ForgeRuntimeSnapshot runtime) {
        return runtime != null
                && plugin.runtimeSnapshot() == runtime
                && plugin.isGenerationActive(runtime.generation());
    }

    public boolean isRecipeBookInventory(Player player) {
        return player != null && openBooks.containsKey(player.getUniqueId());
    }

    public void removeRecipeBook(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        BookSession state = openBooks.get(playerId);
        if (state != null && state.player == player) {
            openBooks.remove(playerId, state);
        }
    }

    public void removeRecipeBookOwner(Player player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<UUID, BookSession> entry : openBooks.entrySet()) {
            BookSession state = entry.getValue();
            if (state != null && state.player == player) {
                openBooks.remove(entry.getKey(), state);
            }
        }
    }

    public List<Player> openPlayersSnapshot() {
        return openBooks.values().stream()
                .map(session -> session.player)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public void clearAllBooks() {
        openBooks.clear();
    }

    private GuiTemplate resolveTemplate(ForgeRuntimeSnapshot runtime) {
        GuiTemplate template = runtime == null || runtime.guiTemplateLoader() == null
                ? null
                : runtime.guiTemplateLoader().get("recipe_book");
        if (template != null) {
            return template;
        }
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        slots.put("recipe_list", new GuiSlot(
                "recipe_list",
                defaultRecipeSlots(),
                "recipe_list",
                new ConfiguredItemDefinition("BOOK", 1, Map.of(
                        "minecraft:custom_name", ItemComponentPatch.set("<gray>暂无配方</gray>"),
                        "minecraft:lore", ItemComponentPatch.set(List.of("<gray>这一页没有更多配方</gray>"))
                )),
                Map.of()
        ));
        slots.put("prev_page", new GuiSlot("prev_page", List.of(45), "prev_page",
                new ConfiguredItemDefinition("ARROW", 1, Map.of()), Map.of()));
        slots.put("next_page", new GuiSlot("next_page", List.of(53), "next_page",
                new ConfiguredItemDefinition("ARROW", 1, Map.of()), Map.of()));
        slots.put("close", new GuiSlot("close", List.of(49), "close",
                new ConfiguredItemDefinition("BARRIER", 1, Map.of()), Map.of()));
        slots.put("footer_fill", new GuiSlot("footer_fill", List.of(46, 47, 48, 50, 51, 52), null,
                new ConfiguredItemDefinition("GRAY_STAINED_GLASS_PANE", 1, Map.of(
                        "minecraft:custom_name", ItemComponentPatch.set("<gray>")
                )), Map.of()));
        return new GuiTemplate("recipe_book", "<dark_gray>配方图鉴</dark_gray>", 6, slots);
    }

    private ItemStack renderSlot(BookSession state, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return null;
        }
        String type = normalizedType(slot.definition());
        if (!"recipe_list".equals(type)) {
            return GuiItemBuilder.build(
                    slot.definition().itemDefinition(),
                    Map.of("page", state.page + 1, "pages", state.totalPages),
                    plugin.coreLib().configuredItemService()
            );
        }
        if (slot.slotIndex() >= state.visibleRecipes.size()) {
            return null;
        }
        return createRecipeItem(state, state.visibleRecipes.get(slot.slotIndex()));
    }

    private ItemStack createRecipeItem(BookSession state, Recipe recipe) {
        Player player = state.player;
        ItemStack itemStack = recipe == null || recipe.configuredOutputSource() == null
                ? null
                : state.runtimeSnapshot.itemIdentifierService().createItem(recipe.configuredOutputSource(), 1);
        if (itemStack == null) {
            itemStack = new ItemStack(Material.BOOK);
        }
        boolean unlocked = !recipe.requiresPermission() || player.hasPermission(recipe.permission());
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("recipe_name", recipe.displayName());
        replacements.put("recipe_id", recipe.id());
        replacements.put("unlock_state", unlocked
                ? guiSupport.text(state.runtimeSnapshot, "recipe_book", "texts.recipe_entry.unlocked", "<green>可用</green>", Map.of())
                : guiSupport.text(state.runtimeSnapshot, "recipe_book", "texts.recipe_entry.locked", "<red>未解锁</red>", Map.of()));
        replacements.put("crafted_state", state.runtimeSnapshot.playerDataStore().hasCrafted(player.getUniqueId(), recipe.id())
                ? guiSupport.text(state.runtimeSnapshot, "recipe_book", "texts.recipe_entry.crafted", "<green>已完成过锻造</green>", Map.of())
                : guiSupport.text(state.runtimeSnapshot, "recipe_book", "texts.recipe_entry.uncrafted", "<gray>尚未完成锻造</gray>", Map.of()));
        replacements.put("click_hint", guiSupport.text(state.runtimeSnapshot, "recipe_book", "texts.recipe_entry.click_hint", "<yellow>点击打开锻造界面</yellow>", Map.of()));
        return guiSupport.apply(
                state.runtimeSnapshot,
                "recipe_book",
                "virtual_items.recipe_entry",
                itemStack,
                replacements,
                new ConfiguredItemDefinition(null, 1, Map.of(
                        "minecraft:custom_name", ItemComponentPatch.set("%recipe_name%"),
                        "minecraft:lore", ItemComponentPatch.set(List.of(
                                "%unlock_state%",
                                "<gray>配方ID: %recipe_id%</gray>",
                                "%crafted_state%",
                                "%click_hint%"
                        ))
                ))
        );
    }

    private List<Integer> slotsForType(GuiTemplate template, String type) {
        List<Integer> result = new ArrayList<>();
        if (template == null || Texts.isBlank(type)) {
            return result;
        }
        String normalized = Texts.lower(type);
        for (GuiSlot slot : template.slots().values()) {
            if (slot == null) {
                continue;
            }
            if (normalized.equals(Texts.lower(slot.type())) || normalized.equals(Texts.lower(slot.key()))) {
                result.addAll(slot.slots());
            }
        }
        return result;
    }

    private List<Integer> defaultRecipeSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    private String normalizedType(GuiSlot slot) {
        if (slot == null) {
            return "";
        }
        return Texts.isNotBlank(slot.type()) ? Texts.lower(slot.type()) : Texts.lower(slot.key());
    }

    private final class BookSessionHandler implements GuiSessionHandler {

        private final BookSession state;

        private BookSessionHandler(BookSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (!isRuntimeCurrent(state.runtimeSnapshot)
                    || openBooks.get(state.player.getUniqueId()) != state) {
                plugin.runtimeMetrics().recordGuiStale();
                state.runtimeSnapshot.messageService().send(state.player, "forge.error.runtime.stale_session");
                state.player.closeInventory();
                click.setCancelled(true);
                return;
            }
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (normalizedType(slot.definition())) {
                case "recipe_list" -> handleRecipeOpen(slot.slotIndex());
                case "prev_page" -> {
                    if (state.page > 0) {
                        openRecipeBook(state.player, state.page - 1, state.runtimeSnapshot);
                    }
                }
                case "next_page" -> {
                    if (state.page + 1 < state.totalPages) {
                        openRecipeBook(state.player, state.page + 1, state.runtimeSnapshot);
                    }
                }
                case "close" -> state.player.closeInventory();
                default -> {
                }
            }
        }

        @Override
        public void onClose(GuiSession session, GuiCloseContext close) {
            openBooks.remove(state.player.getUniqueId(), state);
        }

        private void handleRecipeOpen(int slotIndex) {
            if (slotIndex < 0 || slotIndex >= state.visibleRecipes.size()
                    || !isRuntimeCurrent(state.runtimeSnapshot)
                    || openBooks.get(state.player.getUniqueId()) != state) {
                return;
            }
            Recipe listedRecipe = state.visibleRecipes.get(slotIndex);
            Recipe recipe = listedRecipe == null || state.runtimeSnapshot.recipeLoader() == null
                    ? null
                    : state.runtimeSnapshot.recipeLoader().get(listedRecipe.id());
            if (recipe == null) {
                state.runtimeSnapshot.messageService().send(state.player, "forge.error.runtime.stale_session");
                state.player.closeInventory();
                return;
            }
            if (recipe.requiresPermission() && !state.player.hasPermission(recipe.permission())) {
                state.runtimeSnapshot.messageService().send(state.player, "general.no_permission");
                return;
            }
            ForgeGuiService forgeGuiService = state.runtimeSnapshot.forgeGuiService();
            if (forgeGuiService == null
                    || !forgeGuiService.openForgeGui(state.player, recipe, state.runtimeSnapshot)) {
                state.player.closeInventory();
            }
        }
    }
}
