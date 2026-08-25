package emaki.jiuwu.craft.accessory.apiimpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.api.AccessoryCatalog;
import emaki.jiuwu.craft.accessory.api.model.AccessoryPartView;
import emaki.jiuwu.craft.accessory.api.model.EquippedAccessoryView;
import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryPageRegistry;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class DefaultAccessoryCatalog implements AccessoryCatalog {

    private final EmakiAccessoryPlugin plugin;

    DefaultAccessoryCatalog(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<AccessoryPartView> parts() {
        List<AccessoryPart> parts = plugin.partLoader() == null ? List.of() : plugin.partLoader().parts();
        return parts.stream().map(DefaultAccessoryCatalog::toView).toList();
    }

    @Override
    public @NotNull Optional<AccessoryPartView> part(@Nullable String partId) {
        String normalized = Texts.normalizeId(partId);
        if (Texts.isBlank(normalized) || plugin.partLoader() == null) {
            return Optional.empty();
        }
        return plugin.partLoader().parts().stream()
                .filter(part -> part.partId().equals(normalized))
                .findFirst()
                .map(DefaultAccessoryCatalog::toView);
    }

    @Override
    public @NotNull List<String> slotInstanceIds() {
        return plugin.partRegistry().slotInstanceIds();
    }

    @Override
    public @NotNull List<String> pageIds() {
        return plugin.pageRegistry().pageIds();
    }

    @Override
    public @NotNull String enabledPage(@Nullable UUID playerId) {
        PlayerAccessories accessories = session(playerId);
        if (accessories == null || plugin.contributionService() == null) {
            return "";
        }
        return plugin.contributionService().effectivePage(accessories);
    }

    @Override
    public @NotNull Map<String, EquippedAccessoryView> equipped(@Nullable UUID playerId) {
        return equippedOnPage(playerId, enabledPage(playerId));
    }

    @Override
    public @NotNull Map<String, EquippedAccessoryView> equippedOnPage(@Nullable UUID playerId,
            @Nullable String pageId) {
        PlayerAccessories accessories = session(playerId);
        String page = Texts.normalizeId(pageId);
        if (accessories == null || Texts.isBlank(page)) {
            return Map.of();
        }
        AccessoryPartRegistry registry = plugin.partRegistry();
        AccessoryPageRegistry pages = plugin.pageRegistry();
        Map<String, EquippedAccessoryView> equipped = new LinkedHashMap<>();
        accessories.items(page).forEach((slotInstanceId, item) -> {
            if (item == null || item.getType().isAir()) {
                return;
            }
            AccessorySlot slot = registry.slot(slotInstanceId);
            equipped.put(slotInstanceId, new EquippedAccessoryView(
                    page,
                    slotInstanceId,
                    slot == null ? "" : slot.partId(),
                    item.clone(),
                    !pages.declaresSlot(page, slotInstanceId)));
        });
        return Map.copyOf(equipped);
    }

    private PlayerAccessories session(UUID playerId) {
        if (playerId == null || plugin.accessoryStore() == null) {
            return null;
        }
        return plugin.accessoryStore().cached(playerId);
    }

    @Override
    public int equippedSetPieces(@Nullable UUID playerId, @Nullable String setId) {
        if (playerId == null || plugin.setService() == null) {
            return 0;
        }
        return plugin.setService().equippedPieces(playerId, setId);
    }

    private static AccessoryPartView toView(AccessoryPart part) {
        return new AccessoryPartView(part.partId(), part.count(), part.displayName(),
                part.slotInstanceIds());
    }
}
