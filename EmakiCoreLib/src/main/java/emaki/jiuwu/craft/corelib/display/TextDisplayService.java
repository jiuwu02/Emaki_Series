package emaki.jiuwu.craft.corelib.display;

public interface TextDisplayService {

    void upsert(TextDisplaySpec spec);

    void remove(DisplayKey key);

    void removeGroup(String namespace, String group);

    void removeGroupPrefix(String namespace, String groupPrefix);

    void removeNamespace(String namespace);

    void shutdown();

    String backendName();
}
