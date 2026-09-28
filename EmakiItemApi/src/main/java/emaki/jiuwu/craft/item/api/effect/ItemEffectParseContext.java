package emaki.jiuwu.craft.item.api.effect;

import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Parse-time view of one custom {@code effects} entry.
 *
 * <p>Supplied by EmakiItem; third-party plugins must not implement it. The raw effect map is a defensive copy
 * of plain data and must not be mutated.
 */
@ApiStatus.NonExtendable
public interface ItemEffectParseContext {

    /** {@return the id of the item definition declaring this effect} */
    @NotNull String definitionId();

    /**
     * {@return the raw effect entry, including its {@code type} field; never {@code null}, and unmodifiable}
     */
    @NotNull Map<String, Object> effect();
}
