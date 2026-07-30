package emaki.jiuwu.craft.level.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/** Immutable public leaderboard entry. */
public record LevelTopEntry(@NotNull UUID uuid,
        @NotNull String name,
        @NotNull String typeId,
        int level,
        double exp,
        double totalExp,
        long updatedAt) {

    public LevelTopEntry {
        if (uuid == null) {
            throw new NullPointerException("uuid");
        }
        name = name == null ? "" : name;
        typeId = typeId == null ? "" : typeId;
    }
}
