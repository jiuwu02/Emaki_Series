package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiItemAssemblyService {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final ItemSourceService itemSourceService;
    private final AssemblyDataManager dataManager;
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final ItemRenderService itemRenderService;

    public EmakiItemAssemblyService(EmakiNamespaceRegistry namespaceRegistry,
            EmakiItemLayerCodecRegistry codecRegistry,
            ItemSourceService itemSourceService) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
        this.dataManager = new AssemblyDataManager(namespaceRegistry, codecRegistry);
        this.itemRenderService = new ItemRenderService();
    }

    public ItemStack preview(EmakiItemAssemblyRequest request) {
        AssemblyContext context = resolveContext(request);
        if (context == null || context.baseSource() == null) {
            return null;
        }
        ItemStack itemStack = itemSourceService.createItem(context.baseSource(), context.amount());
        if (itemStack == null) {
            return null;
        }
        itemRenderService.renderItem(itemStack, context.layerSnapshots().values());
        dataManager.writeAssemblyData(
                itemStack,
                CURRENT_SCHEMA_VERSION,
                context.baseSource(),
                context.amount(),
                context.activeLayers(),
                context.assemblySignature(),
                context.layerSnapshots().values()
        );
        return itemStack;
    }

    public ItemStack rebuild(ItemStack itemStack) {
        if (!isEmakiItem(itemStack)) {
            return itemStack == null ? null : itemStack.clone();
        }
        return preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of()));
    }

    public ItemStack give(Player player, EmakiItemAssemblyRequest request) {
        ItemStack itemStack = preview(request);
        if (player == null || itemStack == null) {
            return itemStack;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return itemStack;
    }

    public boolean isEmakiItem(ItemStack itemStack) {
        return dataManager.isEmakiItem(itemStack);
    }

    public ItemSource readBaseSource(ItemStack itemStack) {
        return dataManager.readBaseSource(itemStack);
    }

    public int readBaseAmount(ItemStack itemStack) {
        return dataManager.readBaseAmount(itemStack);
    }

    public List<String> readActiveLayers(ItemStack itemStack) {
        return dataManager.readActiveLayers(itemStack);
    }

    public Map<String, EmakiItemLayerSnapshot> readLayerSnapshots(ItemStack itemStack) {
        return dataManager.readLayerSnapshots(itemStack);
    }

    public EmakiItemLayerSnapshot readLayerSnapshot(ItemStack itemStack, String namespaceId) {
        return dataManager.readLayerSnapshot(itemStack, namespaceId);
    }

    private AssemblyContext resolveContext(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, EmakiItemLayerSnapshot> mergedLayers = new LinkedHashMap<>();
        ItemSource baseSource = request.baseSource();
        int amount = request.amount() > 0 ? request.amount() : 1;
        if (request.existingItem() != null && dataManager.isEmakiItem(request.existingItem())) {
            if (baseSource == null) {
                baseSource = dataManager.readBaseSource(request.existingItem());
            }
            if (request.amount() <= 0) {
                amount = dataManager.readBaseAmount(request.existingItem());
            }
            mergedLayers.putAll(dataManager.readLayerSnapshots(request.existingItem()));
        }
        if (request.layerSnapshots() != null) {
            for (EmakiItemLayerSnapshot snapshot : request.layerSnapshots()) {
                if (snapshot == null || Texts.isBlank(snapshot.namespaceId())) {
                    continue;
                }
                mergedLayers.put(normalizeId(snapshot.namespaceId()), snapshot);
            }
        }
        if (baseSource == null && request.existingItem() != null && !request.existingItem().getType().isAir()) {
            baseSource = itemSourceService.identifyItem(request.existingItem());
        }
        if (baseSource == null) {
            return null;
        }
        List<String> activeLayers = namespaceRegistry.orderNamespaces(mergedLayers.keySet());
        Map<String, EmakiItemLayerSnapshot> orderedLayers = new LinkedHashMap<>();
        for (String namespaceId : activeLayers) {
            EmakiItemLayerSnapshot snapshot = mergedLayers.get(namespaceId);
            if (snapshot != null) {
                orderedLayers.put(namespaceId, snapshot);
            }
        }
        String signature = SignatureUtil.stableSignature(List.of(
                ItemSourceUtil.toShorthand(baseSource),
                amount,
                orderedLayers.values().stream().map(EmakiItemLayerSnapshot::toMap).toList()
        ));
        return new AssemblyContext(baseSource, Math.max(1, amount), orderedLayers, activeLayers, signature);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private record AssemblyContext(ItemSource baseSource,
            int amount,
            Map<String, EmakiItemLayerSnapshot> layerSnapshots,
            List<String> activeLayers,
            String assemblySignature) {

    }
}
