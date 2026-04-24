package emaki.jiuwu.craft.attribute.loader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.DefaultProfile;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class DefaultProfileRegistry {

    private final EmakiAttributePlugin plugin;
    private final Object stateLock = new Object();
    private final Map<String, DefaultProfile> items = new LinkedHashMap<>();
    private final List<String> issues = new ArrayList<>();
    private final List<DefaultProfile> mergedProfiles = new ArrayList<>();
    private boolean loaded;

    public DefaultProfileRegistry(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public int load() {
        synchronized (stateLock) {
            items.clear();
            issues.clear();
            mergedProfiles.clear();
            loaded = false;
            DefaultProfile profile = plugin == null || plugin.configModel() == null
                    ? null
                    : plugin.configModel().defaultProfile();
            if (profile == null) {
                profile = emaki.jiuwu.craft.attribute.config.AttributeConfig.defaults().defaultProfile();
            }
            if (profile == null) {
                loaded = true;
                return 0;
            }
            String id = Texts.normalizeId(Texts.isBlank(profile.id()) ? "default" : profile.id());
            items.put(id, profile);
            mergedProfiles.add(profile);
            mergedProfiles.sort(Comparator.comparingInt(DefaultProfile::priority).reversed());
            loaded = true;
            return items.size();
        }
    }

    public Map<String, DefaultProfile> all() {
        synchronized (stateLock) {
            return Map.copyOf(items);
        }
    }

    public List<String> issues() {
        synchronized (stateLock) {
            return List.copyOf(issues);
        }
    }

    public boolean loaded() {
        synchronized (stateLock) {
            return loaded;
        }
    }

    public DefaultProfile get(String id) {
        synchronized (stateLock) {
            if (Texts.isBlank(id)) {
                return null;
            }
            return items.get(Texts.normalizeId(id));
        }
    }

    public List<DefaultProfile> mergedProfiles() {
        synchronized (stateLock) {
            return List.copyOf(mergedProfiles);
        }
    }
}

