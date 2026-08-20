package emaki.jiuwu.craft.strengthen.enhancement.target;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayer;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryLayerCodec;

public final class EquipmentTargetProvider implements EnhancementTargetProvider {

    private static final String PROVIDER_ID = "equipment";

    private final EmakiStrengthenPlugin plugin;
    private final MasteryLayerCodec masteryCodec;

    public EquipmentTargetProvider(EmakiStrengthenPlugin plugin) {
        this(plugin, null);
    }

    public EquipmentTargetProvider(EmakiStrengthenPlugin plugin, @Nullable MasteryLayerCodec masteryCodec) {
        this.plugin = plugin;
        this.masteryCodec = masteryCodec;
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
    public @NotNull String readInstanceId(@Nullable ItemStack itemStack) {
        if (masteryCodec == null) {
            return "";
        }
        MasteryLayer layer = masteryCodec.read(itemStack);
        return layer == null ? "" : layer.instanceId();
    }

    @Override
    public @NotNull EmakiResult<ItemMasteryView> masterySnapshot(@Nullable ItemStack itemStack) {
        if (masteryCodec == null) {
            return EmakiResult.unavailable();
        }
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        MasteryLayer layer = masteryCodec.read(itemStack);
        if (layer == null) {
            return EmakiResult.notFound("strengthen.mastery.absent");
        }
        return EmakiResult.success(layer.toView());
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
