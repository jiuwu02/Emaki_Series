package emaki.jiuwu.craft.dungeon.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static public API facade for EmakiDungeon.
 *
 * <p>EmakiDungeon exposes dungeon entry, party, instance, script, and editor
 * facades, all five delivered by the installed bridge. Accessors never return
 * {@code null}: while EmakiDungeon is absent the facade reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}.
 *
 * <p>Resolve accessors at the point of use rather than caching them in a field,
 * because the backing bridge is replaced across a reload.
 */
public final class EmakiDungeonApi {

    private static volatile Bridge bridge;

    private EmakiDungeonApi() {
    }

    /** Installs the backing bridge. For EmakiDungeon lifecycle use only. */
    @ApiStatus.Internal
    public static void install(@NotNull Bridge b) {
        bridge = b;
    }

    /** Removes the backing bridge when it is still the active bridge. */
    @ApiStatus.Internal
    public static void uninstall(@Nullable Bridge b) {
        if (bridge == b) {
            bridge = null;
        }
    }

    /**
     * {@return availability and identity metadata; never {@code null}, and
     * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()} when no bridge is installed}
     */
    public static @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
        Bridge resolved = bridge;
        if (resolved == null) {
            return emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled();
        }
        var s = resolved.status();
        return s == null ? emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled() : s;
    }

    /** {@return the dungeon entry facade; never {@code null}} */
    public static @NotNull DungeonApi dungeonApi() {
        Bridge resolved = bridge;
        return resolved == null ? NOT_INSTALLED.dungeonApi() : resolved.dungeonApi();
    }

    /** {@return the party facade; never {@code null}} */
    public static @NotNull PartyApi partyApi() {
        Bridge resolved = bridge;
        return resolved == null ? NOT_INSTALLED.partyApi() : resolved.partyApi();
    }

    /** {@return the instance facade; never {@code null}} */
    public static @NotNull InstanceApi instanceApi() {
        Bridge resolved = bridge;
        return resolved == null ? NOT_INSTALLED.instanceApi() : resolved.instanceApi();
    }

    /** {@return the script facade; never {@code null}} */
    public static @NotNull ScriptApi scriptApi() {
        Bridge resolved = bridge;
        return resolved == null ? NOT_INSTALLED.scriptApi() : resolved.scriptApi();
    }

    /** {@return the editor facade; never {@code null}} */
    public static @NotNull EditorApi editorApi() {
        Bridge resolved = bridge;
        return resolved == null ? NOT_INSTALLED.editorApi() : resolved.editorApi();
    }

    private static final Bridge NOT_INSTALLED = new NotInstalledBridge();

    private static final class NotInstalledBridge implements Bridge {

        @Override
        public @NotNull emaki.jiuwu.craft.corelib.api.contract.ApiStatus status() {
            return emaki.jiuwu.craft.corelib.api.contract.ApiStatus.notInstalled();
        }

        @Override
        public @NotNull DungeonApi dungeonApi() {
            return new DungeonApi() {
                @Override
                public @NotNull Result<Object> enter(@NotNull Player player,
                        @NotNull String dungeonId) {
                    return notInstalled();
                }

                @Override
                public @NotNull List<InstanceView> listInstances() {
                    return List.of();
                }

                @Override
                public @NotNull Optional<InstanceView> getInstance(
                        @NotNull UUID instanceId) {
                    return Optional.empty();
                }
            };
        }

        @Override
        public @NotNull PartyApi partyApi() {
            return new PartyApi() {
                @Override
                public @NotNull Result<PartyId> create(@NotNull UUID player) {
                    return notInstalled();
                }

                @Override
                public @NotNull Result<InviteToken> invite(@NotNull UUID player,
                        @NotNull UUID target) {
                    return notInstalled();
                }

                @Override
                public @NotNull Result<Void> kick(@NotNull UUID member) {
                    return notInstalled();
                }

                @Override
                public @NotNull Result<Void> confirmReady(@NotNull UUID player) {
                    return notInstalled();
                }
            };
        }

        @Override
        public @NotNull InstanceApi instanceApi() {
            return instanceId -> notInstalled();
        }

        @Override
        public @NotNull ScriptApi scriptApi() {
            return new ScriptApi() {
                @Override
                public @NotNull Result<ScriptId> loadFlowScript(@NotNull String dungeonId,
                        @NotNull String source) {
                    return notInstalled();
                }

                @Override
                public @NotNull Result<Void> registerTrigger(@NotNull String triggerId,
                        @NotNull String event, @NotNull ScriptTriggerHandler handler) {
                    return notInstalled();
                }
            };
        }

        @Override
        public @NotNull EditorApi editorApi() {
            return (player, dungeonId) -> notInstalled();
        }

        private <T> Result<T> notInstalled() {
            return Result.err(ErrorCodes.NOT_INSTALLED, "api.not_installed");
        }
    }

    /** Bridge contract implemented by EmakiDungeon runtime. Third-party plugins must not implement it. */
    @ApiStatus.NonExtendable
    public interface Bridge {

        /** {@return availability and identity metadata; must never {@code null}} */
        @NotNull
        emaki.jiuwu.craft.corelib.api.contract.ApiStatus status();

        @NotNull
        DungeonApi dungeonApi();

        @NotNull
        PartyApi partyApi();

        @NotNull
        InstanceApi instanceApi();

        @NotNull
        ScriptApi scriptApi();

        @NotNull
        EditorApi editorApi();
    }
}
