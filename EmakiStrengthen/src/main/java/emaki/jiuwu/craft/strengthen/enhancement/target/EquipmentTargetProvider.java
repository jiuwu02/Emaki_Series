package emaki.jiuwu.craft.strengthen.enhancement.target;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

public final class EquipmentTargetProvider implements EnhancementTargetProvider {

    private static final String PROVIDER_ID = "equipment";

    private final EmakiStrengthenPlugin plugin;

    public EquipmentTargetProvider(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean canHandle(@Nullable ItemStack itemStack) {
        return itemStack != null && !itemStack.getType().isAir();
    }

    @Override
    public int readLevel(@Nullable ItemStack itemStack) {
        StrengthenState state = readState(itemStack);
        return state == null ? 0 : state.currentStar();
    }

    @Override
    public int readTemper(@Nullable ItemStack itemStack) {
        StrengthenState state = readState(itemStack);
        return state == null ? 0 : state.temperLevel();
    }

    @Override
    public @NotNull String readRecipeId(@Nullable ItemStack itemStack) {
        StrengthenState state = readState(itemStack);
        return state == null ? "" : state.recipeId();
    }

    @Override
    public void writeLevel(@Nullable ItemStack itemStack, int level) {
        applyState(itemStack, level, readTemper(itemStack), readRecipeId(itemStack));
    }

    @Override
    public void writeTemper(@Nullable ItemStack itemStack, int temper) {
        applyState(itemStack, readLevel(itemStack), temper, readRecipeId(itemStack));
    }

    @Override
    public void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId) {
        applyState(itemStack, readLevel(itemStack), readTemper(itemStack), recipeId);
    }

    @Override
    public void clearEnhancement(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin.attemptService() == null) {
            return;
        }
        ItemStack cleared = plugin.attemptService().clearStrengthenLayer(itemStack);
        if (cleared != null && cleared.hasItemMeta()) {
            itemStack.setItemMeta(cleared.getItemMeta());
        }
    }

    private void applyState(ItemStack itemStack, int level, int temper, String recipeId) {
        if (itemStack == null || itemStack.getType().isAir() || plugin.attemptService() == null) {
            return;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(itemStack, level, temper, recipeId);
        if (rebuilt != null && rebuilt.hasItemMeta()) {
            itemStack.setItemMeta(rebuilt.getItemMeta());
        }
    }

    private StrengthenState readState(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin.attemptService() == null) {
            return null;
        }
        return plugin.attemptService().readState(itemStack);
    }
}
