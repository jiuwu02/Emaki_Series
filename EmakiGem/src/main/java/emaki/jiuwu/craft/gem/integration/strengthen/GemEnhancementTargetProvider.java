package emaki.jiuwu.craft.gem.integration.strengthen;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

public final class GemEnhancementTargetProvider implements EnhancementTargetProvider {

    public static final String PROVIDER_ID = "gem";

    private final EmakiGemPlugin plugin;

    public GemEnhancementTargetProvider(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean canHandle(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        GemDefinition definition = readDefinition(instance);
        return definition != null && definition.stages().enabled();
    }

    @Override
    public int readLevel(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? 0 : instance.level();
    }

    @Override
    public int readTemper(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? 0 : instance.stage();
    }

    @Override
    public @NotNull String readRecipeId(@Nullable ItemStack itemStack) {
        GemItemInstance instance = readInstance(itemStack);
        return instance == null ? "" : instance.gemId();
    }

    @Override
    public void writeLevel(@Nullable ItemStack itemStack, int level) {
        GemItemInstance current = readInstance(itemStack);
        GemDefinition definition = readDefinition(current);
        int targetLevel = Math.max(1, level);
        if (definition == null || targetLevel > definition.stages().maxLevel()
                || (targetLevel > 1 && definition.stage(targetLevel) == null)) {
            return;
        }
        writeInstance(itemStack, withLevel(current, definition, targetLevel));
    }

    @Override
    public void writeTemper(@Nullable ItemStack itemStack, int temper) {
        GemItemInstance current = readInstance(itemStack);
        if (current == null) {
            return;
        }
        writeInstance(itemStack, withStage(current, temper));
    }

    @Override
    public void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId) {
    }

    @Override
    public void clearEnhancement(@Nullable ItemStack itemStack) {
        GemItemInstance current = readInstance(itemStack);
        GemDefinition definition = readDefinition(current);
        if (definition == null) {
            return;
        }
        writeInstance(itemStack, withLevel(current, definition, 1));
    }

    private GemItemInstance readInstance(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin == null
                || plugin.itemMatcher() == null) {
            return null;
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(itemStack);
        if (instance == null || Texts.isBlank(instance.gemId())) {
            return null;
        }
        return readDefinition(instance) == null ? null : instance;
    }

    private GemDefinition readDefinition(GemItemInstance instance) {
        return instance == null || plugin == null || plugin.gemLoader() == null
                ? null
                : plugin.gemLoader().get(instance.gemId());
    }

    private void writeInstance(ItemStack itemStack, GemItemInstance instance) {
        if (plugin == null || plugin.itemFactory() == null || itemStack == null || instance == null) {
            return;
        }
        plugin.itemFactory().applyInstance(itemStack, instance);
    }

    private static GemItemInstance withLevel(GemItemInstance source, GemDefinition definition, int level) {
        Map<String, String> matrices = new LinkedHashMap<>(source.matrices());
        definition.matricesForLevel(level).forEach((key, value) -> {
            if (Texts.isBlank(value)) {
                matrices.remove(key);
            } else {
                matrices.put(key, value);
            }
        });
        return new GemItemInstance(
                source.gemId(),
                level,
                System.currentTimeMillis(),
                source.instanceId(),
                level <= 1 ? 0 : level,
                source.affixes(),
                matrices,
                source.extensions(),
                source.dataVersion()
        );
    }

    private static GemItemInstance withStage(GemItemInstance source, int stage) {
        return new GemItemInstance(
                source.gemId(),
                source.level(),
                System.currentTimeMillis(),
                source.instanceId(),
                stage,
                source.affixes(),
                source.matrices(),
                source.extensions(),
                source.dataVersion()
        );
    }
}
