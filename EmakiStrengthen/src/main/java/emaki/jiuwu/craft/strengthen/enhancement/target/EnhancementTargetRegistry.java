package emaki.jiuwu.craft.strengthen.enhancement.target;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

public final class EnhancementTargetRegistry {

    private final ConcurrentMap<String, EnhancementTargetProvider> providers = new ConcurrentHashMap<>();

    public void register(@NotNull EnhancementTargetProvider provider) {
        providers.put(provider.id(), provider);
    }

    public void unregister(@NotNull String id) {
        providers.remove(id);
    }

    @Nullable
    public EnhancementTargetProvider get(@NotNull String id) {
        return providers.get(id);
    }

    @Nullable
    public EnhancementTargetProvider resolve(@Nullable ItemStack itemStack) {
        return resolve(null, itemStack);
    }

    @Nullable
    public EnhancementTargetProvider resolve(@Nullable Player player, @Nullable ItemStack itemStack) {
        for (EnhancementTargetProvider provider : providers.values()) {
            if (provider.canHandle(player, itemStack)) {
                return provider;
            }
        }
        return null;
    }

    @NotNull
    public Collection<EnhancementTargetProvider> all() {
        return List.copyOf(providers.values());
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }
}
