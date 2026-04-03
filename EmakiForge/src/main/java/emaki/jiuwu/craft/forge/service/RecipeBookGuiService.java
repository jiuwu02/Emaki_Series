package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class RecipeBookGuiService {

    private static final class BookSession {

        private final Player player;
        private final int page;
        private final int totalPages;
        private final List<Recipe> visibleRecipes;

        private BookSession(Player player, int page, int totalPages, List<Recipe> visibleRecipes) {
            this.player = player;
            this.page = page;
            this.totalPages = totalPages;
            this.visibleRecipes = List.copyOf(visibleRecipes);
        }
    }

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final Map<UUID, BookSession> openBooks = new LinkedHashMap<>();

    public RecipeBookGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public boolean openRecipeBook(Player player) {
        return openRecipeBook(player, 0);
    }

    public boolean openRecipeBook(Player player, int page) {
        if (player == null) {
            return false;
        }
        List<Recipe> recipes = new ArrayList<>(plugin.recipeLoader().all().values());
        if (recipes.isEmpty()) {
            plugin.messageService().send(player, "forge.error.no_recipe");
            return false;
        }
        GuiTemplate template = resolveTemplate();
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
        BookSession state = new BookSession(player, currentPage, totalPages, visibleRecipes);
        GuiSession guiSession = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of("page", currentPage + 1, "pages", totalPages),
                plugin.itemIdentifierService()::createItem,
                (session, slot) -> renderSlot(state, slot),
                new BookSessionHandler(state)
        ));
        if (guiSession == null) {
            return false;
        }
        openBooks.put(player.getUniqueId(), state);
        return true;
    }

    public boolean isRecipeBookInventory(Player player) {
        return player != null && openBooks.containsKey(player.getUniqueId());
    }

    public void removeRecipeBook(Player player) {
        if (player != null) {
            openBooks.remove(player.getUniqueId());
        }
    }

    public void clearAllBooks() {
        openBooks.clear();
    }

    private GuiTemplate resolveTemplate() {
        GuiTemplate template = plugin.guiTemplateLoader().get("recipe_book");
        if (template != null) {
            return template;
        }
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        slots.put("recipe_list", new GuiSlot(
                "recipe_list",
                defaultRecipeSlots(),
                "recipe_list",
                "BOOK",
                new emaki.jiuwu.craft.corelib.gui.ItemComponentParser.ItemComponents(
                        "<gray>暂无配方</gray>",
                        true,
                        List.of("<gray>这一页没有更多配方</gray>"),
                        null,
                        null,
                        Map.of(),
                        List.of()
                ),
                Map.of()
        ));
        slots.put("prev_page", new GuiSlot("prev_page", List.of(45), "prev_page", "ARROW",
                emaki.jiuwu.craft.corelib.gui.ItemComponentParser.empty(), Map.of()));
        slots.put("next_page", new GuiSlot("next_page", List.of(53), "next_page", "ARROW",
                emaki.jiuwu.craft.corelib.gui.ItemComponentParser.empty(), Map.of()));
        slots.put("close", new GuiSlot("close", List.of(49), "close", "BARRIER",
                emaki.jiuwu.craft.corelib.gui.ItemComponentParser.empty(), Map.of()));
        slots.put("footer_fill", new GuiSlot("footer_fill", List.of(46, 47, 48, 50, 51, 52), null, "GRAY_STAINED_GLASS_PANE",
                new emaki.jiuwu.craft.corelib.gui.ItemComponentParser.ItemComponents("<gray>", false, List.of(), null, null, Map.of(), List.of()), Map.of()));
        return new GuiTemplate("recipe_book", "<dark_gray>配方图鉴</dark_gray>", 6, slots);
    }

    private ItemStack renderSlot(BookSession state, GuiTemplate.ResolvedSlot slot) {
        if (slot == null || slot.definition() == null) {
            return null;
        }
        String type = normalizedType(slot.definition());
        if (!"recipe_list".equals(type)) {
            return GuiItemBuilder.build(
                    slot.definition().item(),
                    slot.definition().components(),
                    1,
                    Map.of("page", state.page + 1, "pages", state.totalPages),
                    plugin.itemIdentifierService()::createItem
            );
        }
        if (slot.slotIndex() >= state.visibleRecipes.size()) {
            return null;
        }
        return createRecipeItem(state.player, state.visibleRecipes.get(slot.slotIndex()));
    }

    private ItemStack createRecipeItem(Player player, Recipe recipe) {
        ItemStack itemStack = recipe == null || recipe.configuredOutputSource() == null
                ? null
                : plugin.itemIdentifierService().createItem(recipe.configuredOutputSource(), 1);
        if (itemStack == null) {
            itemStack = new ItemStack(Material.BOOK);
        }
        ItemStack clone = itemStack.clone();
        ItemMeta itemMeta = clone.getItemMeta();
        if (itemMeta != null) {
            itemMeta.customName(MiniMessages.parse(recipe.displayName()));
            List<String> lore = new ArrayList<>();
            boolean unlocked = !recipe.requiresPermission() || player.hasPermission(recipe.permission());
            lore.add(unlocked ? "<green>可用</green>" : "<red>未解锁</red>");
            lore.add("<gray>配方ID: " + recipe.id() + "</gray>");
            lore.add(plugin.playerDataStore().hasCrafted(player.getUniqueId(), recipe.id())
                    ? "<green>已完成过锻造</green>"
                    : "<gray>尚未完成锻造</gray>");
            lore.add("<yellow>点击打开锻造界面</yellow>");
            itemMeta.lore(lore.stream().map(MiniMessages::parse).toList());
            clone.setItemMeta(itemMeta);
        }
        return clone;
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
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (normalizedType(slot.definition())) {
                case "recipe_list" ->
                    handleRecipeOpen(slot.slotIndex());
                case "prev_page" -> {
                    if (state.page > 0) {
                        openRecipeBook(state.player, state.page - 1);
                    }
                }
                case "next_page" -> {
                    if (state.page + 1 < state.totalPages) {
                        openRecipeBook(state.player, state.page + 1);
                    }
                }
                case "close" ->
                    state.player.closeInventory();
                default -> {
                }
            }
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            openBooks.remove(state.player.getUniqueId());
        }

        private void handleRecipeOpen(int slotIndex) {
            if (slotIndex < 0 || slotIndex >= state.visibleRecipes.size()) {
                return;
            }
            Recipe recipe = state.visibleRecipes.get(slotIndex);
            if (recipe.requiresPermission() && !state.player.hasPermission(recipe.permission())) {
                plugin.messageService().send(state.player, "general.no_permission");
                return;
            }
            plugin.forgeGuiService().openForgeGui(state.player, recipe);
        }
    }
}
