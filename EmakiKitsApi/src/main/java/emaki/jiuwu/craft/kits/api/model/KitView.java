package emaki.jiuwu.craft.kits.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Immutable, thread-safe snapshot of one loaded kit definition.
 *
 * <p>The view carries no Bukkit, world or player references and never exposes reward payloads,
 * economy state or stored idempotency keys. It is a projection of the configuration as currently
 * loaded by the runtime, so a reload can change the answer of a later {@code kit(id)} call.
 *
 * @param id              normalized kit id, matching the top-level key in {@code kits.yml}
 * @param type            the kit category
 * @param enabled         whether the kit passed configuration validation and is active
 * @param displayName     configured display name used in player-facing messages
 * @param permission      permission node required to claim this kit
 * @param timeLimited     whether a {@code limit} window (range or cron) is active
 * @param choiceMinSelect minimum number of options for a {@link KitTypeView#CHOICE} kit
 * @param choiceMaxSelect maximum number of options for a {@link KitTypeView#CHOICE} kit
 * @param purchaseEnabled whether the kit must be purchased before it can be claimed
 * @param rewardEntryIds  opaque ids of the configured reward entries, for diagnostics only
 */
public record KitView(@NotNull String id,
                      @NotNull KitTypeView type,
                      boolean enabled,
                      @NotNull String displayName,
                      @NotNull String permission,
                      boolean timeLimited,
                      int choiceMinSelect,
                      int choiceMaxSelect,
                      boolean purchaseEnabled,
                      @NotNull List<String> rewardEntryIds) {

    /**
     * Creates a view with defensive copies.
     *
     * @throws NullPointerException when {@code id}, {@code type}, {@code displayName},
     *                              {@code permission} or {@code rewardEntryIds} is {@code null}
     */
    public KitView {
        if (id == null || type == null || displayName == null || permission == null) {
            throw new NullPointerException("kit view identity fields must not be null");
        }
        rewardEntryIds = rewardEntryIds == null ? List.of() : List.copyOf(rewardEntryIds);
    }
}
