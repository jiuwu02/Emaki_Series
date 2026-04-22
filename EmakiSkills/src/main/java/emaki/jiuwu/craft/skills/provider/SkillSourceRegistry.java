package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SkillSourceRegistry {

    private final List<SkillSourceProvider> providers = new ArrayList<>();

    public void register(SkillSourceProvider provider) {
        if (provider == null) {
            return;
        }
        synchronized (providers) {
            providers.removeIf(existing -> existing.id().equals(provider.id()));
            providers.add(provider);
            providers.sort(Comparator.comparingInt(SkillSourceProvider::priority));
        }
    }

    public void unregister(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        synchronized (providers) {
            providers.removeIf(existing -> existing.id().equals(providerId));
        }
    }

    public List<SkillSourceProvider> all() {
        synchronized (providers) {
            return List.copyOf(providers);
        }
    }

    public void clear() {
        synchronized (providers) {
            providers.clear();
        }
    }
}
