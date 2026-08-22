package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.mobs.api.MobExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultMobExtensions implements MobExtensions {

    private final Map<String, CustomSpawner> spawners = new ConcurrentHashMap<>();

    @Override
    public void registerCustomSpawner(@NotNull String id, @NotNull CustomSpawner spawner) {
        spawners.put(id, spawner);
        spawner.onReload();
    }

    public void notifyReload() {
        spawners.values().forEach(CustomSpawner::onReload);
    }
}
