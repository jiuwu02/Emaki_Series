package emaki.jiuwu.craft.item.service;

import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;

public final class EmakiItemUpdateService {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemIdResolver idResolver;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGatewayAdapter attributeGateway;

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway) {
        this.itemLoader = itemLoader;
        this.idResolver = idResolver;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
    }

    public ItemStack updateIfNeeded(ItemStack original, String trigger) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        String id = identifier.identify(original);
        if (id.isBlank()) {
            return original;
        }
        EmakiItemAlias alias = idResolver == null ? null : idResolver.aliasFor(id);
        if (alias != null) {
            return migrateAlias(original, id, alias);
        }
        EmakiItemDefinition definition = idResolver == null ? itemLoader.get(id) : idResolver.resolveDefinition(id);
        if (definition == null) {
            return original;
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        if (!updateConfig.triggerEnabled(trigger)) {
            return original;
        }
        if (identifier.updateVersion(original) >= definition.updatePolicy().version()) {
            return original;
        }
        return rebuild(original, definition, updateConfig, definition.id());
    }

    public ItemStack forceUpdate(ItemStack original) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        String id = identifier.identify(original);
        EmakiItemAlias alias = idResolver == null ? null : idResolver.aliasFor(id);
        if (alias != null) {
            return migrateAlias(original, id, alias);
        }
        EmakiItemDefinition definition = id.isBlank() ? null : (idResolver == null ? itemLoader.get(id) : idResolver.resolveDefinition(id));
        if (definition == null) {
            return original;
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        return updateConfig.enabled() ? rebuild(original, definition, updateConfig, definition.id()) : original;
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

    private ItemStack migrateAlias(ItemStack original, String currentId, EmakiItemAlias alias) {
        if (alias == null) {
            return original;
        }
        EmakiItemDefinition definition = itemLoader.get(alias.targetId());
        if (definition == null) {
            return original;
        }
        boolean migratePdc = alias.migratePdc();
        boolean rewriteDisplay = alias.rewriteDisplay();
        if (!migratePdc && !rewriteDisplay) {
            return original;
        }
        ItemUpdateConfig updateConfig = definition.updatePolicy().resolve();
        String identityId = migratePdc ? definition.id() : currentId;
        if (rewriteDisplay) {
            return rebuild(original, definition, updateConfig, identityId);
        }
        ItemStack migrated = original.clone();
        writeIdentity(migrated, definition, identityId);
        return migrated;
    }

    private ItemStack rebuild(ItemStack original, EmakiItemDefinition definition, ItemUpdateConfig updateConfig, String identityId) {
        int amount = updateConfig.preserveAmount() ? original.getAmount() : 1;
        int oldDamage = readDamage(original);
        ItemStack rebuilt = itemFactory.rebuildBase(definition, amount);
        if (rebuilt == null) {
            return original;
        }
        if (!definition.id().equals(identityId)) {
            writeIdentity(rebuilt, definition, identityId);
        }
        if (updateConfig.preserveDamage()) {
            applyDamage(rebuilt, oldDamage);
        }
        if (updateConfig.preserveUnknownAttributeSources()) {
            attributeGateway.copyPayloads(original, rebuilt, Set.of("emakiitem", EmakiItemPdcWriter.SET_ATTRIBUTE_SOURCE_ID));
        }
        return rebuilt;
    }

    private void writeIdentity(ItemStack itemStack, EmakiItemDefinition definition, String identityId) {
        if (itemStack == null || definition == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Integer updateVersion = definition.updatePolicy().updateEnabled() ? definition.updatePolicy().version() : null;
        identifier.writeIdentity(itemMeta, identityId, definition.definitionSignature(), updateVersion);
        itemStack.setItemMeta(itemMeta);
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
