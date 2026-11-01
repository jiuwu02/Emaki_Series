package emaki.jiuwu.craft.dungeon.flow;

import java.util.UUID;

import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;

public interface PlayerStateHook {

    void onPlayerDeath(DungeonInstance instance, UUID playerId);
}
