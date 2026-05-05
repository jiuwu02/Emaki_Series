package emaki.jiuwu.craft.item.service;

import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;

public final class EmakiItemUpdateService {

    private final EmakiItemLoader itemLoader;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final EmakiItemPdcWriter pdcWriter;
    private final PdcAttributeGatewayAdapter attributeGateway;
    private final java.util.function.Supplier<AppConfig> configSupplier;

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            PdcAttributeGatewayAdapter attributeGateway,
            java.util.function.Supplier<AppConfig> configSupplier) {
        this.itemLoader = itemLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.attributeGateway = attributeGateway;
        this.configSupplier = configSupplier;
    }

    public ItemStack updateIfNeeded(ItemStack original, String trigger) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        AppConfig config = configSupplier.get();
        if (config != null && !config.itemUpdate().triggerEnabled(trigger)) {
            return original;
        }
        String id = identifier.identify(original);
        if (id.isBlank()) {
            return original;
        }
        EmakiItemDefinition definition = itemLoader.get(id);
        if (definition == null) {
            return original;
        }
        Integer schemaVersion = identifier.schemaVersion(original);
        String storedSignature = identifier.definitionSignature(original);
        if (schemaVersion != null
                && schemaVersion == EmakiItemIdentifier.SCHEMA_VERSION
                && definition.definitionSignature().equals(storedSignature)) {
            return original;
        }
        return rebuild(original, definition, config == null ? AppConfig.defaults() : config);
    }

    public ItemStack forceUpdate(ItemStack original) {
        if (original == null || original.getType().isAir()) {
            return original;
        }
        String id = identifier.identify(original);
        EmakiItemDefinition definition = id.isBlank() ? null : itemLoader.get(id);
        return definition == null ? original : rebuild(original, definition, configSupplier.get() == null ? AppConfig.defaults() : configSupplier.get());
    }

    private ItemStack rebuild(ItemStack original, EmakiItemDefinition definition, AppConfig config) {
        int amount = config.itemUpdate().preserveAmount() ? original.getAmount() : 1;
        int oldDamage = readDamage(original);
        ItemStack rebuilt = itemFactory.rebuildBase(definition, amount);
        if (rebuilt == null) {
            return original;
        }
        if (config.itemUpdate().preserveDamage()) {
            applyDamage(rebuilt, oldDamage);
        }
        if (config.itemUpdate().preserveUnknownAttributeSources()) {
            attributeGateway.copyPayloads(original, rebuilt, Set.of("emakiitem", EmakiItemPdcWriter.SET_ATTRIBUTE_SOURCE_ID));
        }
        return rebuilt;
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
