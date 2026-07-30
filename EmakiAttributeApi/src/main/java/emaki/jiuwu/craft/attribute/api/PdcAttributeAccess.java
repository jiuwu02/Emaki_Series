package emaki.jiuwu.craft.attribute.api;

import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** Reads and writes source-partitioned EmakiAttribute payloads in item PDC. */
@ApiStatus.NonExtendable
public interface PdcAttributeAccess {

    @NotNull
    EmakiResult<Unit> registerSource(@Nullable String sourceId);

    @NotNull
    EmakiResult<Unit> unregisterSource(@Nullable String sourceId);

    boolean isRegisteredSource(@Nullable String sourceId);

    @NotNull
    Set<String> registeredSources();

    /** <strong>Thread:</strong> the owner thread of whatever holds the item. */
    @NotNull
    EmakiResult<Unit> write(@Nullable ItemStack itemStack, @Nullable PdcAttributePayload payload);

    /** Convenience overload that creates a payload with the current schema and timestamp. */
    default @NotNull EmakiResult<Unit> write(@Nullable ItemStack itemStack,
            @Nullable String sourceId,
            @Nullable Map<String, Double> attributes,
            @Nullable Map<String, String> meta) {
        return write(itemStack, PdcAttributePayload.of(sourceId, attributes, meta));
    }

    /** <strong>Thread:</strong> the owner thread of whatever holds the item. */
    @NotNull
    EmakiResult<PdcAttributePayload> read(@Nullable ItemStack itemStack, @Nullable String sourceId);

    /** <strong>Thread:</strong> the owner thread of whatever holds the item. */
    @NotNull
    Map<String, PdcAttributePayload> readAll(@Nullable ItemStack itemStack);

    /** <strong>Thread:</strong> the owner thread of whatever holds the item. */
    @NotNull
    EmakiResult<Unit> clear(@Nullable ItemStack itemStack, @Nullable String sourceId);

    /** <strong>Thread:</strong> the owner thread of whatever holds the item. */
    @NotNull
    EmakiResult<Unit> clearAll(@Nullable ItemStack itemStack);

    /** <strong>Thread:</strong> the owner threads of both item holders. */
    @NotNull
    EmakiResult<Unit> copy(@Nullable ItemStack fromItem,
            @Nullable ItemStack toItem,
            @Nullable Set<String> excludedSourceIds);
}
