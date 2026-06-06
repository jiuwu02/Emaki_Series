package emaki.jiuwu.craft.level.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiLevel.
 *
 * <p>Third-party plugins should call these static methods directly. EmakiLevel
 * installs the backing bridge during its enable lifecycle and removes it on
 * disable.
 */
public final class EmakiLevelApi {

    private static volatile Bridge bridge;

    private EmakiLevelApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiLevel's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiLevel
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiLevelApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiLevelApi.bridge == bridge) {
            EmakiLevelApi.bridge = null;
        }
    }

    /** {@return whether EmakiLevel has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /**
     * Looks up a level type by id.
     *
     * @param typeId the level type id
     * @return the matching type view, or {@link Optional#empty()} when absent or unavailable
     */
    public static @NotNull Optional<LevelTypeView> type(@NotNull String typeId) {
        Bridge resolved = bridge;
        return resolved == null ? Optional.empty() : resolved.type(typeId);
    }

    /**
     * Returns every loaded level type.
     *
     * @return a collection of type views; never {@code null}
     */
    public static @NotNull Collection<LevelTypeView> types() {
        Bridge resolved = bridge;
        return resolved == null ? java.util.List.of() : resolved.types();
    }

    /**
     * Loads a player's level data snapshot.
     *
     * @param uuid the player UUID
     * @return a future completed with the player's level view; never {@code null}
     */
    public static @NotNull CompletableFuture<PlayerLevelView> getPlayerData(@NotNull UUID uuid) {
        Bridge resolved = bridge;
        if (resolved == null) {
            return CompletableFuture.completedFuture(new PlayerLevelView(uuid, "", Map.of()));
        }
        return resolved.getPlayerData(uuid);
    }

    /**
     * Reads a player's current level in a type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @return the current level, or {@code 0} when absent or unavailable
     */
    public static int getLevel(@NotNull UUID uuid, @NotNull String typeId) {
        Bridge resolved = bridge;
        return resolved == null ? 0 : resolved.getLevel(uuid, typeId);
    }

    /**
     * Reads a player's current exp in a type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @return the current exp, or {@code 0} when absent or unavailable
     */
    public static double getExp(@NotNull UUID uuid, @NotNull String typeId) {
        Bridge resolved = bridge;
        return resolved == null ? 0D : resolved.getExp(uuid, typeId);
    }

    /**
     * Reads a player's accumulated total exp in a type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @return the total exp, or {@code 0} when absent or unavailable
     */
    public static double getTotalExp(@NotNull UUID uuid, @NotNull String typeId) {
        Bridge resolved = bridge;
        return resolved == null ? 0D : resolved.getTotalExp(uuid, typeId);
    }

    /**
     * Computes the exp required to reach a target level.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param targetLevel the target level
     * @return the required exp, or {@code 0} when absent or unavailable
     */
    public static double getRequiredExp(@NotNull UUID uuid, @NotNull String typeId, int targetLevel) {
        Bridge resolved = bridge;
        return resolved == null ? 0D : resolved.getRequiredExp(uuid, typeId, targetLevel);
    }

    /**
     * Adds exp to a player's level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param amount the exp amount to add
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult addExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.ADD_EXP, typeId) : resolved.addExp(uuid, typeId, amount, reason);
    }

    /**
     * Removes exp from a player's level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param amount the exp amount to remove
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult removeExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.REMOVE_EXP, typeId) : resolved.removeExp(uuid, typeId, amount, reason);
    }

    /**
     * Sets a player's current exp in a level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param amount the new exp amount
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult setExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.SET_EXP, typeId) : resolved.setExp(uuid, typeId, amount, reason);
    }

    /**
     * Adds levels to a player's level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param amount the number of levels to add
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult addLevel(@NotNull UUID uuid, @NotNull String typeId, int amount, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.ADD_LEVEL, typeId) : resolved.addLevel(uuid, typeId, amount, reason);
    }

    /**
     * Removes levels from a player's level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param amount the number of levels to remove
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult removeLevel(@NotNull UUID uuid, @NotNull String typeId, int amount, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.REMOVE_LEVEL, typeId) : resolved.removeLevel(uuid, typeId, amount, reason);
    }

    /**
     * Sets a player's level in a level type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param level the new level
     * @param reason the operation reason; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult setLevel(@NotNull UUID uuid, @NotNull String typeId, int level, @Nullable String reason) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.SET_LEVEL, typeId) : resolved.setLevel(uuid, typeId, level, reason);
    }

    /**
     * Performs a level-up operation using the configured rules for a type.
     *
     * @param uuid the player UUID
     * @param typeId the level type id
     * @param cause the level-up cause; may be {@code null}
     * @return the operation result; never {@code null}
     */
    public static @NotNull LevelOperationResult levelUp(@NotNull UUID uuid, @NotNull String typeId, @Nullable LevelUpCause cause) {
        Bridge resolved = bridge;
        return resolved == null ? unavailable(LevelOperationType.LEVEL_UP, typeId) : resolved.levelUp(uuid, typeId, cause);
    }

    private static LevelOperationResult unavailable(LevelOperationType operationType, String typeId) {
        return LevelOperationResult.failure("api_unavailable", operationType, typeId);
    }

    /** Internal bridge installed by EmakiLevel. */
    public interface Bridge {
        /**
         * Looks up a level type by id.
         *
         * @param typeId the level type id
         * @return the matching type view, or {@link Optional#empty()} when absent
         */
        @NotNull
        Optional<LevelTypeView> type(@NotNull String typeId);

        /**
         * Returns every loaded level type.
         *
         * @return a collection of type views; never {@code null}
         */
        @NotNull
        Collection<LevelTypeView> types();

        /**
         * Loads a player's level data snapshot.
         *
         * @param uuid the player UUID
         * @return a future completed with the player's level view; never {@code null}
         */
        @NotNull
        CompletableFuture<PlayerLevelView> getPlayerData(@NotNull UUID uuid);

        /**
         * Reads a player's current level in a type.
         *
         * @param uuid the player UUID
         * @param typeId the level type id
         * @return the current level
         */
        int getLevel(@NotNull UUID uuid, @NotNull String typeId);

        /**
         * Reads a player's current exp in a type.
         *
         * @param uuid the player UUID
         * @param typeId the level type id
         * @return the current exp
         */
        double getExp(@NotNull UUID uuid, @NotNull String typeId);

        /**
         * Reads a player's accumulated total exp in a type.
         *
         * @param uuid the player UUID
         * @param typeId the level type id
         * @return the total exp
         */
        double getTotalExp(@NotNull UUID uuid, @NotNull String typeId);

        /**
         * Computes the exp required to reach a target level.
         *
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param targetLevel the target level
         * @return the required exp
         */
        double getRequiredExp(@NotNull UUID uuid, @NotNull String typeId, int targetLevel);

        /**
         * Adds exp to a player's level type.
         *
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param amount the exp amount to add
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult addExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason);

        /**
         * Removes exp from a player's level type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param amount the exp amount to remove
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult removeExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason);

        /**
         * Sets a player's current exp in a level type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param amount the new exp amount
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult setExp(@NotNull UUID uuid, @NotNull String typeId, double amount, @Nullable String reason);

        /**
         * Adds levels to a player's level type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param amount the number of levels to add
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult addLevel(@NotNull UUID uuid, @NotNull String typeId, int amount, @Nullable String reason);

        /**
         * Removes levels from a player's level type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param amount the number of levels to remove
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult removeLevel(@NotNull UUID uuid, @NotNull String typeId, int amount, @Nullable String reason);

        /**
         * Sets a player's level in a level type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param level the new level
         * @param reason the operation reason; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult setLevel(@NotNull UUID uuid, @NotNull String typeId, int level, @Nullable String reason);

        /**
         * Performs a level-up operation using the configured rules for a type.
         * @param uuid the player UUID
         * @param typeId the level type id
         * @param cause the level-up cause; may be {@code null}
         * @return the operation result; never {@code null}
         */
        @NotNull
        LevelOperationResult levelUp(@NotNull UUID uuid, @NotNull String typeId, @Nullable LevelUpCause cause);
    }
}
