package emaki.jiuwu.craft.item.apiimpl;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.ItemCatalog;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.service.EmakiItemConditionChecker;
import emaki.jiuwu.craft.item.service.EmakiItemIdentifier;

public final class DefaultItemCatalog implements ItemCatalog {

    private final EmakiItemPlugin plugin;

    public DefaultItemCatalog(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Set<String> definitionIds() {
        if (plugin.itemLoader() == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new TreeSet<>(plugin.itemLoader().all().keySet()));
    }

    @Override
    public @NotNull EmakiResult<ConfiguredItemDefinition> definition(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return EmakiResult.invalidInput("item.definition.id_required");
        }
        if (plugin.idResolver() == null || !plugin.runtimeReady()) {
            return EmakiResult.unavailable();
        }
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        return definition == null
                ? EmakiResult.notFound("item.definition.not_found")
                : EmakiResult.success(definition.itemDefinition());
    }

    @Override
    public @NotNull EmakiResult<String> identify(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("item.identify.item_required");
        }
        EmakiItemIdentifier identifier = plugin.identifier();
        if (identifier == null) {
            return EmakiResult.unavailable();
        }
        String id = identifier.identify(itemStack);
        return Texts.isBlank(id)
                ? EmakiResult.notFound("item.identify.not_managed")
                : EmakiResult.success(Texts.normalizeId(id));
    }

    @Override
    public @NotNull EmakiResult<String> displayName(@Nullable String id) {
        if (Texts.isBlank(id)) {
            return EmakiResult.invalidInput("item.definition.id_required");
        }
        if (plugin.idResolver() == null || plugin.itemFactory() == null || !plugin.runtimeReady()) {
            return EmakiResult.unavailable();
        }
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(id);
        if (definition == null) {
            return EmakiResult.notFound("item.definition.not_found");
        }
        ItemStack built = plugin.itemFactory().rebuildBase(definition, 1);
        if (built == null) {
            return EmakiResult.internalError("item.display_name.build_failed");
        }
        String text = ItemTextBridge.effectiveNameText(built);
        return EmakiResult.success(Texts.isBlank(text)
                ? MiniMessages.serialize(ItemTextBridge.effectiveName(built))
                : text);
    }

    @Override
    public boolean exists(@Nullable String id) {
        return Texts.isNotBlank(id)
                && plugin.idResolver() != null
                && plugin.idResolver().resolveDefinition(id) != null;
    }

    @Override
    public @NotNull EmakiResult<Boolean> conditionPasses(@Nullable Player player,
            @Nullable String itemId,
            @Nullable String trigger,
            @Nullable ItemStack itemStack) {
        if (player == null) {
            return EmakiResult.invalidInput("item.condition.player_required");
        }
        if (Texts.isBlank(itemId)) {
            return EmakiResult.invalidInput("item.definition.id_required");
        }
        if (Texts.isBlank(trigger)) {
            return EmakiResult.invalidInput("item.condition.trigger_required");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin.scheduling() == null
                || plugin.idResolver() == null
                || plugin.conditionChecker() == null
                || !plugin.runtimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.scheduling().ownsEntity(player)) {
            return EmakiResult.wrongThread();
        }
        EmakiItemDefinition definition = plugin.idResolver().resolveDefinition(itemId);
        if (definition == null) {
            return EmakiResult.notFound("item.definition.not_found");
        }
        try {
            EmakiItemConditionChecker checker = plugin.conditionChecker();
            return EmakiResult.success(checker.passes(player, definition, trigger, itemStack));
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("item.condition.internal_error");
        }
    }
}
