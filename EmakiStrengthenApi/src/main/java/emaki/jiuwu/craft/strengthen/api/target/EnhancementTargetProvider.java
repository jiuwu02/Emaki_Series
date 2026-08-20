package emaki.jiuwu.craft.strengthen.api.target;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;

/**
 * Reads and writes the enhancement layer of one target type.
 *
 * <p>This is the extension point of the enhancement framework. EmakiStrengthen ships the
 * {@code equipment} provider; other plugins register their own target types through
 * {@link emaki.jiuwu.craft.strengthen.api.StrengthenOperations#registerEnhancementTarget}
 * so the framework never references a target plugin's internal classes.
 *
 * <p>Every method must tolerate a {@code null} or air stack rather than throwing. Read methods
 * return a neutral value in that case; write methods do nothing.
 *
 * <p><strong>Thread:</strong> called on the owner thread of whatever holds the stack. A detached
 * item copy may be processed on the caller's current thread.
 */
public interface EnhancementTargetProvider {

    /** {@return the unique provider id used by a recipe's {@code target.provider} field} */
    @NotNull
    String id();

    /**
     * {@return whether this provider owns the given stack}
     *
     * @param itemStack the candidate stack; {@code null} or air must yield {@code false}
     */
    boolean canHandle(@Nullable ItemStack itemStack);

    /**
     * {@return the current enhancement level, or {@code 0} when the stack carries none}
     *
     * @param itemStack the stack to read
     */
    int readLevel(@Nullable ItemStack itemStack);

    /**
     * {@return the current temper level, or {@code 0} when the stack carries none}
     *
     * @param itemStack the stack to read
     */
    int readTemper(@Nullable ItemStack itemStack);

    /**
     * {@return the recipe id recorded on the stack, or an empty string when absent}
     *
     * @param itemStack the stack to read
     */
    @NotNull
    String readRecipeId(@Nullable ItemStack itemStack);

    /**
     * Writes the enhancement level into the stack in place.
     *
     * @param itemStack the stack to modify
     * @param level     the level to record
     */
    void writeLevel(@Nullable ItemStack itemStack, int level);

    /**
     * Writes the temper level into the stack in place.
     *
     * @param itemStack the stack to modify
     * @param temper    the temper level to record
     */
    void writeTemper(@Nullable ItemStack itemStack, int temper);

    /**
     * Writes the owning recipe id into the stack in place.
     *
     * @param itemStack the stack to modify
     * @param recipeId  the recipe id to record; {@code null} clears it
     */
    void writeRecipeId(@Nullable ItemStack itemStack, @Nullable String recipeId);

    /**
     * Removes this provider's entire enhancement layer from the stack in place.
     *
     * @param itemStack the stack to modify
     */
    void clearEnhancement(@Nullable ItemStack itemStack);

    /**
     * Context-aware variant of {@link #canHandle(ItemStack)}.
     *
     * <p>The default implementation preserves compatibility with providers compiled against the
     * original item-only contract. Providers that need the acting player should override this method
     * and the corresponding context-aware read/write methods.
     *
     * @param player    the acting player, when the operation is player-scoped
     * @param itemStack the candidate stack
     * @return whether this provider owns the given stack
     */
    default boolean canHandle(@Nullable Player player, @Nullable ItemStack itemStack) {
        return canHandle(itemStack);
    }

    /** {@return the context-aware enhancement level, defaulting to {@link #readLevel(ItemStack)}} */
    default int readLevel(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readLevel(itemStack);
    }

    /** {@return the context-aware temper level, defaulting to {@link #readTemper(ItemStack)}} */
    default int readTemper(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readTemper(itemStack);
    }

    /** {@return the context-aware recipe id, defaulting to {@link #readRecipeId(ItemStack)}} */
    default @NotNull String readRecipeId(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readRecipeId(itemStack);
    }

    /**
     * Reads this provider's item mastery snapshot when the provider exposes one.
     *
     * <p>The default preserves compatibility for existing providers and reports unavailability rather than
     * inventing mastery state from enhancement level, temper, or recipe metadata.
     *
     * @param itemStack the item to inspect
     * @return the mastery snapshot or {@link EmakiResult#unavailable()} when unsupported
     */
    default @NotNull EmakiResult<ItemMasteryView> masterySnapshot(@Nullable ItemStack itemStack) {
        return EmakiResult.unavailable();
    }

    /** Context-aware variant of {@link #masterySnapshot(ItemStack)}. */
    default @NotNull EmakiResult<ItemMasteryView> masterySnapshot(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        return masterySnapshot(itemStack);
    }

    /** Context-aware variant of {@link #writeLevel(ItemStack, int)}. */
    default void writeLevel(@Nullable Player player, @Nullable ItemStack itemStack, int level) {
        writeLevel(itemStack, level);
    }

    /** Context-aware variant of {@link #writeTemper(ItemStack, int)}. */
    default void writeTemper(@Nullable Player player, @Nullable ItemStack itemStack, int temper) {
        writeTemper(itemStack, temper);
    }

    /** Context-aware variant of {@link #writeRecipeId(ItemStack, String)}. */
    default void writeRecipeId(@Nullable Player player,
            @Nullable ItemStack itemStack,
            @Nullable String recipeId) {
        writeRecipeId(itemStack, recipeId);
    }

    /** Context-aware variant of {@link #clearEnhancement(ItemStack)}. */
    default void clearEnhancement(@Nullable Player player, @Nullable ItemStack itemStack) {
        clearEnhancement(itemStack);
    }

    /**
     * Reads a stable provider-owned instance identifier when one is available.
     *
     * <p>The default is empty so providers compiled against the original contract remain valid.
     */
    default @NotNull String readInstanceId(@Nullable ItemStack itemStack) {
        return "";
    }

    /** Context-aware variant of {@link #readInstanceId(ItemStack)}. */
    default @NotNull String readInstanceId(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readInstanceId(itemStack);
    }

    /**
     * Reads a provider-owned version or revision marker when one is available.
     *
     * <p>The default is empty so providers compiled against the original contract remain valid.
     */
    default @NotNull String readVersion(@Nullable ItemStack itemStack) {
        return "";
    }

    /** Context-aware variant of {@link #readVersion(ItemStack)}. */
    default @NotNull String readVersion(@Nullable Player player, @Nullable ItemStack itemStack) {
        return readVersion(itemStack);
    }

    /**
     * Rebuilds the presentation layer — lore, attribute payload, display metadata — of a stack whose
     * enhancement level was just written.
     *
     * <p>The enhancement runtime calls this <strong>inside the transaction, on the prepared clone,
     * before any cost is charged</strong>. A provider that reports failure therefore aborts the whole
     * attempt while the player still owns their currency and materials; it never produces a
     * "charged but unchanged" item. Implementations must not assume the stack is the one held by a
     * player, and must not write to any inventory.
     *
     * <p>The default returns {@link EmakiResult#ok()} because a provider whose {@code writeLevel}
     * already produces its own final presentation has nothing left to do. Override this only when the
     * level write leaves lore or attributes stale, and return a classified failure — rather than a
     * silent success — when a required bridge is missing, so the runtime can surface a specific
     * reason key instead of a generic write-back error.
     *
     * @param player    the acting player, when the operation is player-scoped
     * @param itemStack the prepared stack to refresh in place
     * @return {@link Unit} when the presentation is current, or a classified failure carrying a
     *         reason key describing why the refresh could not complete
     */
    default @NotNull EmakiResult<Unit> refreshPresentation(@Nullable Player player,
            @Nullable ItemStack itemStack) {
        return EmakiResult.ok();
    }
}
