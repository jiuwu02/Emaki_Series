package emaki.jiuwu.craft.item.service;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;

public final class EmakiItemUpdateService {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGatewayAdapter attributeGateway;
    private final java.util.function.Supplier<AppConfig> configSupplier;

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway,
            java.util.function.Supplier<AppConfig> configSupplier) {
        this.itemLoader = itemLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.configSupplier = configSupplier;
    }

    public ItemStack updateIfNeeded(ItemStack original, String trigger) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        AppConfig config = configSupplier.get();
        String id = identifier.identify(original);
        if (id.isBlank()) {
            return original;
        }
        EmakiItemDefinition definition = itemLoader.get(id);
        if (definition == null) {
            return original;
        }
        ItemUpdateConfig updateConfig = resolvedUpdateConfig(definition, config);
        if (!updateConfig.triggerEnabled(trigger)) {
            return original;
        }
        if (identifier.updateVersion(original) >= definition.updatePolicy().version()) {
            return original;
        }
        return rebuild(original, definition, updateConfig);
    }

    public ItemStack forceUpdate(ItemStack original) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        String id = identifier.identify(original);
        EmakiItemDefinition definition = id.isBlank() ? null : itemLoader.get(id);
        return definition == null ? original : rebuild(original, definition, resolvedUpdateConfig(definition, configSupplier.get()));
    }

    public int updatePlayerItems(Player player, String trigger) {
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int changed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            ItemStack updated = updateIfNeeded(original, trigger);
            if (updated != original) {
                inventory.setItem(slot, updated);
                changed++;
            }
        }
        return changed;
    }

    private ItemStack rebuild(ItemStack original, EmakiItemDefinition definition, ItemUpdateConfig updateConfig) {
        int amount = updateConfig.preserveAmount() ? original.getAmount() : 1;
        int oldDamage = readDamage(original);
        ItemStack rebuilt = itemFactory.rebuildBase(definition, amount);
        if (rebuilt == null) {
            return original;
        }
        if (updateConfig.preserveDamage()) {
            applyDamage(rebuilt, oldDamage);
        }
        if (updateConfig.preserveUnknownAttributeSources()) {
            attributeGateway.copyPayloads(original, rebuilt, Set.of("emakiitem", EmakiItemPdcWriter.SET_ATTRIBUTE_SOURCE_ID));
        }
        return rebuilt;
    }

    private ItemUpdateConfig resolvedUpdateConfig(EmakiItemDefinition definition, AppConfig config) {
        AppConfig effectiveConfig = config == null ? AppConfig.defaults() : config;
        return definition.updatePolicy().resolve(effectiveConfig.itemUpdate());
    }

    private int readDamage(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta instanceof Damageable damageable ? Math.max(0, damageable.getDamage()) : 0;
    }

    private void applyDamage(ItemStack itemStack, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof Damageable damageable)) {
            return;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : Integer.MAX_VALUE;
        damageable.setDamage(Math.max(0, Math.min(damage, maxDamage)));
        itemStack.setItemMeta(itemMeta);
    }

    public interface PdcAttributeGatewayAdapter {
        void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds);
    }
}
