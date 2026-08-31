package emaki.jiuwu.craft.strengthen.enhancement.pity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PityStateStore {

    @Nullable PityState load(@NotNull String scope, @NotNull String group, @NotNull String key);

    void save(@NotNull String scope, @NotNull String group, @NotNull String key, @NotNull PityState state);

    void remove(@NotNull String scope, @NotNull String group, @NotNull String key);

    boolean exists(@NotNull String scope, @NotNull String group, @NotNull String key);
}
