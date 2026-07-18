package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.ItemUpdateConfig;

public final class EmakiItemUpdateService {

    private static final String DISPLAY_OPERATION_NAMESPACE = "emakiitem:item_display";

    private final EmakiItemLoader itemLoader;
    private final EmakiItemIdResolver idResolver;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final PdcAttributeGatewayAdapter attributeGateway;
    private final EmakiItemAssemblyService assemblyService;
    private final ItemOperationLedger operationLedger = new ItemOperationLedger();
    private final PdcService pdcService = new PdcService("emaki");
    private final PdcPartition itemPartition = pdcService.partition("item");

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway) {
        this(itemLoader, idResolver, itemFactory, identifier, attributeGateway, null);
    }

    public EmakiItemUpdateService(EmakiItemLoader itemLoader,
            EmakiItemIdResolver idResolver,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            PdcAttributeGatewayAdapter attributeGateway,
            EmakiItemAssemblyService assemblyService) {
        this.itemLoader = itemLoader;
        this.idResolver = idResolver;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.attributeGateway = attributeGateway;
        this.assemblyService = assemblyService;
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
        EmakiItemDefinition definition = id.isBlank()
                ? null
                : idResolver == null ? itemLoader.get(id) : idResolver.resolveDefinition(id);
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

    private ItemStack rebuild(ItemStack original,
            EmakiItemDefinition definition,
            ItemUpdateConfig updateConfig,
            String identityId) {
        int amount = updateConfig.preserveAmount() ? original.getAmount() : 1;
        int oldDamage = readDamage(original);
        EmakiItemFactory.PreparedBuild prepared = itemFactory.prepareBuild(definition);
        if (prepared == null) {
            return original;
        }
        ItemStack rebuilt = mergeAssemblyAndLedger(original, prepared, amount);
        if (rebuilt == null) {
            return original;
        }
        rebuilt = itemFactory.finishBuild(rebuilt, definition, prepared.variables());
        rebuilt.setAmount(Math.max(1, Math.min(amount, rebuilt.getMaxStackSize())));
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

    private ItemStack mergeAssemblyAndLedger(ItemStack original,
            EmakiItemFactory.PreparedBuild prepared,
            int amount) {
        ItemStack rebuiltBase = prepared.itemStack().clone();
        boolean hasAssemblyData = hasAssemblyData(original);
        boolean canReplayAssembly = assemblyService != null && assemblyService.isEmakiItem(original);
        boolean hasLedger = operationLedger.hasOperations(original);
        if (!canReplayAssembly) {
            if (hasAssemblyData || hasLedger) {
                copyPersistentData(original, rebuiltBase, false);
            }
            return rebuiltBase;
        }

        ItemStack assemblyState = original.clone();
        operationLedger.revertAll(assemblyState, DISPLAY_OPERATION_NAMESPACE);
        writeAssemblyBasePresentation(assemblyState, rebuiltBase);
        ItemSource source = ItemSourceUtil.parse(prepared.itemDefinition().source());
        ItemStack assembled = assemblyService.preview(new EmakiItemAssemblyRequest(source, amount, assemblyState, List.of()));
        if (assembled == null) {
            copyPersistentData(original, rebuiltBase, false);
            return rebuiltBase;
        }

        ConfiguredItemDefinition nonPresentation = withoutPresentationPatches(prepared.itemDefinition());
        ItemBuildResult patched = EmakiCoreLibApi.applyConfiguredItem(assembled, nonPresentation);
        ItemStack merged = patched.success() && patched.itemStack() != null ? patched.itemStack() : assembled;
        copyPersistentData(original, merged, false);
        return merged;
    }

    private ConfiguredItemDefinition withoutPresentationPatches(ConfiguredItemDefinition definition) {
        Map<String, ItemComponentPatch> patches = new LinkedHashMap<>();
        definition.components().forEach((componentId, patch) -> {
            if (!"minecraft:custom_name".equals(componentId) && !"minecraft:lore".equals(componentId)) {
                patches.put(componentId, patch);
            }
        });
        return new ConfiguredItemDefinition(null, 1, patches);
    }

    private boolean hasAssemblyData(ItemStack itemStack) {
        return pdcService.has(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER)
                && pdcService.has(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
    }

    private void writeAssemblyBasePresentation(ItemStack assemblyState, ItemStack rebuiltBase) {
        ItemMeta itemMeta = rebuiltBase == null ? null : rebuiltBase.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (ItemTextBridge.hasCustomName(itemMeta)) {
            pdcService.set(assemblyState, itemPartition, "base_custom_name", PersistentDataType.STRING,
                    MiniMessages.serialize(ItemTextBridge.customName(itemMeta)));
        } else {
            pdcService.remove(assemblyState, itemPartition, "base_custom_name");
        }
        List<String> lore = ItemTextBridge.loreLines(itemMeta);
        if (lore == null || lore.isEmpty()) {
            pdcService.remove(assemblyState, itemPartition, "base_lore");
        } else {
            pdcService.set(assemblyState, itemPartition, "base_lore", PersistentDataType.STRING,
                    YamlFiles.dump(Map.of("lore", lore)));
        }
    }

    private void copyPersistentData(ItemStack from, ItemStack to, boolean replace) {
        if (from == null || to == null) {
            return;
        }
        ItemMeta fromMeta = from.getItemMeta();
        ItemMeta toMeta = to.getItemMeta();
        if (fromMeta == null || toMeta == null) {
            return;
        }
        fromMeta.getPersistentDataContainer().copyTo(toMeta.getPersistentDataContainer(), replace);
        to.setItemMeta(toMeta);
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
