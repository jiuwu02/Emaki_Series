package emaki.jiuwu.craft.attribute.api.model;

/**
 * Immutable public view of one configured player resource.
 *
 * @param id normalized resource id
 * @param displayName configured display name
 * @param defaultMax default maximum
 * @param minMax minimum permitted maximum
 * @param maxMax maximum permitted maximum
 * @param syncToBukkit whether the value is mirrored to a Bukkit-native resource
 * @param fullOnInit whether newly initialized state starts full
 * @param regenPerSecond passive regeneration per second
 */
public record ResourceDefinitionView(String id,
        String displayName,
        double defaultMax,
        double minMax,
        double maxMax,
        boolean syncToBukkit,
        boolean fullOnInit,
        double regenPerSecond) {

    public ResourceDefinitionView {
        id = id == null ? "" : id;
        displayName = displayName == null ? id : displayName;
    }
}
