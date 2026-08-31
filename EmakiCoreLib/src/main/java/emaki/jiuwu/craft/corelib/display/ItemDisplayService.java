package emaki.jiuwu.craft.corelib.display;

import org.bukkit.Location;

public interface ItemDisplayService {

    void upsert(ItemDisplaySpec spec);

    void remove(DisplayKey key);

    void removeGroup(String namespace, String group);

    void removeGroupPrefix(String namespace, String groupPrefix);

    void removeNamespace(String namespace);

    void playTransformAnimation(String namespace,
            String group,
            Location anchor,
            double heightOffset,
            String rotationAxis,
            double rotationDegrees,
            int durationTicks);

    boolean isAnimating(String namespace, String group);

    void shutdown();

    String backendName();
}
