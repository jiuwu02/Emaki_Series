package emaki.jiuwu.craft.accessory.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.accessory.api.model.AccessoryPartView;
import emaki.jiuwu.craft.accessory.api.model.EquippedAccessoryView;

/**
 * Stable no-op layer returned while no EmakiAccessory bridge is installed.
 *
 * <p>Exists so {@link EmakiAccessoryApi} never returns {@code null} and callers never have to tell
 * "plugin missing" apart from "empty answer" by catching an exception.
 */
final class UnavailableAccessory {

    static final AccessoryCatalog CATALOG = new UnavailableCatalog();

    private UnavailableAccessory() {
    }

    private static final class UnavailableCatalog implements AccessoryCatalog {

        @Override
        public @NotNull List<AccessoryPartView> parts() {
            return List.of();
        }

        @Override
        public @NotNull Optional<AccessoryPartView> part(@Nullable String partId) {
            return Optional.empty();
        }

        @Override
        public @NotNull List<String> slotInstanceIds() {
            return List.of();
        }

        @Override
        public @NotNull List<String> pageIds() {
            return List.of();
        }

        @Override
        public @NotNull String enabledPage(@Nullable UUID playerId) {
            return "";
        }

        @Override
        public @NotNull Map<String, EquippedAccessoryView> equipped(@Nullable UUID playerId) {
            return Map.of();
        }

        @Override
        public @NotNull Map<String, EquippedAccessoryView> equippedOnPage(@Nullable UUID playerId,
                @Nullable String pageId) {
            return Map.of();
        }

        @Override
        public int equippedSetPieces(@Nullable UUID playerId, @Nullable String setId) {
            return 0;
        }
    }
}
