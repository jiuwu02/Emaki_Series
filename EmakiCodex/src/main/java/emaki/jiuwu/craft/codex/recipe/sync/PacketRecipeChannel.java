package emaki.jiuwu.craft.codex.recipe.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.recipe.CookingCategory;
import com.github.retrooper.packetevents.protocol.recipe.CraftingCategory;
import com.github.retrooper.packetevents.protocol.recipe.Ingredient;
import com.github.retrooper.packetevents.protocol.recipe.data.CookedRecipeData;
import com.github.retrooper.packetevents.protocol.recipe.data.RecipeData;
import com.github.retrooper.packetevents.protocol.recipe.data.ShapedRecipeData;
import com.github.retrooper.packetevents.protocol.recipe.data.ShapelessRecipeData;
import com.github.retrooper.packetevents.protocol.recipe.data.StoneCuttingRecipeData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeclareRecipes;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

/**
 * Channel 1: PacketEvents-backed recipe delivery for target servers on 1.21.1 and
 * below, where the recipe protocol has not been rewritten and
 * {@link WrapperPlayServerDeclareRecipes} still carries the legacy
 * {@code Recipe<?>[]} payload.
 *
 * <p>This class references PacketEvents types directly, so it is only ever
 * instantiated by the gateway after PacketEvents is confirmed present. When
 * PacketEvents is absent the class is never loaded, avoiding {@code NoClassDefFoundError}.
 */
@SuppressWarnings("deprecation") // 1.21.1 recipe protocol uses PacketEvents' pre-1.21.2 (obsolete) recipe types by design
public final class PacketRecipeChannel implements RecipeSyncChannel {

    private final JavaPlugin plugin;

    public PacketRecipeChannel(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "packetevents";
    }

    @Override
    public boolean isAvailable() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void sync(Player player, Set<String> visibleRecipeIds) {
        if (player == null || visibleRecipeIds == null || visibleRecipeIds.isEmpty()) {
            return;
        }
        List<com.github.retrooper.packetevents.protocol.recipe.Recipe<?>> recipes = new ArrayList<>();
        for (String recipeId : visibleRecipeIds) {
            NamespacedKey key = NamespacedKey.fromString(recipeId);
            if (key == null) {
                continue;
            }
            Recipe bukkitRecipe = Bukkit.getRecipe(key);
            if (bukkitRecipe == null) {
                continue;
            }
            com.github.retrooper.packetevents.protocol.recipe.Recipe<?> converted = convert(recipeId, bukkitRecipe);
            if (converted != null) {
                recipes.add(converted);
            }
        }
        if (recipes.isEmpty()) {
            return;
        }
        try {
            WrapperPlayServerDeclareRecipes packet = new WrapperPlayServerDeclareRecipes(
                    recipes.toArray(new com.github.retrooper.packetevents.protocol.recipe.Recipe<?>[0]));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "[Codex] PacketEvents recipe sync failed for " + player.getName() + ": " + throwable.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private com.github.retrooper.packetevents.protocol.recipe.Recipe<?> convert(String recipeId, Recipe recipe) {
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                return buildShaped(recipeId, shaped);
            }
            if (recipe instanceof ShapelessRecipe shapeless) {
                return buildShapeless(recipeId, shapeless);
            }
            if (recipe instanceof CookingRecipe<?> cooking) {
                return buildCooking(recipeId, cooking);
            }
            if (recipe instanceof StonecuttingRecipe stonecutting) {
                return buildStonecutting(recipeId, stonecutting);
            }
        } catch (Throwable ignored) {
            // Unsupported/edge recipe shapes are skipped; the vanilla book channel still covers them.
        }
        return null;
    }

    private com.github.retrooper.packetevents.protocol.recipe.Recipe<ShapedRecipeData> buildShaped(
            String recipeId, ShapedRecipe shaped) {
        String[] shape = shaped.getShape();
        int height = shape.length;
        int width = height == 0 ? 0 : shape[0].length();
        Ingredient[] ingredients = new Ingredient[Math.max(0, width * height)];
        int index = 0;
        for (String row : shape) {
            for (int column = 0; column < width; column++) {
                char symbol = column < row.length() ? row.charAt(column) : ' ';
                org.bukkit.inventory.ItemStack bukkitItem = symbol == ' '
                        ? null : shaped.getIngredientMap().get(symbol);
                ingredients[index++] = toIngredient(bukkitItem);
            }
        }
        ShapedRecipeData data = new ShapedRecipeData(width, height, shaped.getGroup(),
                ingredients, toPacketItem(shaped.getResult()));
        return new com.github.retrooper.packetevents.protocol.recipe.Recipe<>(
                com.github.retrooper.packetevents.protocol.recipe.RecipeType.CRAFTING_SHAPED, recipeId, data);
    }

    private com.github.retrooper.packetevents.protocol.recipe.Recipe<ShapelessRecipeData> buildShapeless(
            String recipeId, ShapelessRecipe shapeless) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack ingredient : shapeless.getIngredientList()) {
            ingredients.add(toIngredient(ingredient));
        }
        ShapelessRecipeData data = new ShapelessRecipeData(shapeless.getGroup(), CraftingCategory.MISC,
                ingredients.toArray(new Ingredient[0]), toPacketItem(shapeless.getResult()));
        return new com.github.retrooper.packetevents.protocol.recipe.Recipe<>(
                com.github.retrooper.packetevents.protocol.recipe.RecipeType.CRAFTING_SHAPELESS, recipeId, data);
    }

    private com.github.retrooper.packetevents.protocol.recipe.Recipe<CookedRecipeData> buildCooking(
            String recipeId, CookingRecipe<?> cooking) {
        CookedRecipeData data = new CookedRecipeData(cooking.getGroup(), CookingCategory.MISC,
                toIngredient(cooking.getInput()), toPacketItem(cooking.getResult()),
                cooking.getExperience(), cooking.getCookingTime());
        return new com.github.retrooper.packetevents.protocol.recipe.Recipe<>(cookingType(cooking), recipeId, data);
    }

    private com.github.retrooper.packetevents.protocol.recipe.Recipe<StoneCuttingRecipeData> buildStonecutting(
            String recipeId, StonecuttingRecipe stonecutting) {
        StoneCuttingRecipeData data = new StoneCuttingRecipeData(stonecutting.getGroup(),
                toIngredient(stonecutting.getInput()), toPacketItem(stonecutting.getResult()));
        return new com.github.retrooper.packetevents.protocol.recipe.Recipe<>(
                com.github.retrooper.packetevents.protocol.recipe.RecipeType.STONECUTTING, recipeId, data);
    }

    private com.github.retrooper.packetevents.protocol.recipe.RecipeType cookingType(CookingRecipe<?> cooking) {
        if (cooking instanceof FurnaceRecipe) {
            return com.github.retrooper.packetevents.protocol.recipe.RecipeType.SMELTING;
        }
        if (cooking instanceof BlastingRecipe) {
            return com.github.retrooper.packetevents.protocol.recipe.RecipeType.BLASTING;
        }
        if (cooking instanceof SmokingRecipe) {
            return com.github.retrooper.packetevents.protocol.recipe.RecipeType.SMOKING;
        }
        if (cooking instanceof CampfireRecipe) {
            return com.github.retrooper.packetevents.protocol.recipe.RecipeType.CAMPFIRE_COOKING;
        }
        return com.github.retrooper.packetevents.protocol.recipe.RecipeType.SMELTING;
    }

    private Ingredient toIngredient(org.bukkit.inventory.ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType().isAir()) {
            return new Ingredient(ItemStack.EMPTY);
        }
        return new Ingredient(toPacketItem(bukkitItem));
    }

    private ItemStack toPacketItem(org.bukkit.inventory.ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType().isAir()) {
            return ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(bukkitItem);
    }
}
