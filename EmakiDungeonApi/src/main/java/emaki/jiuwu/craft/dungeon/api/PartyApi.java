package emaki.jiuwu.craft.dungeon.api;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

public interface PartyApi {

    @NotNull Result<PartyId> create(@NotNull UUID player);

    @NotNull Result<InviteToken> invite(@NotNull UUID player, @NotNull UUID target);

    @NotNull Result<Void> kick(@NotNull UUID member);

    @NotNull Result<Void> confirmReady(@NotNull UUID player);
}
