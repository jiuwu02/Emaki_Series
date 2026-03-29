package emaki.jiuwu.craft.corelib.assembly;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmakiNamespaceRegistryTest {

    @Test
    void orderNamespacesUsesRegisteredOrderAndDeduplicates() {
        EmakiNamespaceRegistry registry = new EmakiNamespaceRegistry();
        registry.register(new EmakiNamespaceDefinition("strengthen", 200, "Strengthen"));
        registry.register(new EmakiNamespaceDefinition("forge", 100, "Forge"));
        registry.register(new EmakiNamespaceDefinition("socket", 300, "Socket"));

        List<String> ordered = registry.orderNamespaces(List.of("socket", "forge", "strengthen", "forge"));

        assertEquals(List.of("forge", "strengthen", "socket"), ordered);
    }

    @Test
    void unknownNamespacesSortAfterRegisteredNamespaces() {
        EmakiNamespaceRegistry registry = new EmakiNamespaceRegistry();
        registry.register(new EmakiNamespaceDefinition("forge", 100, "Forge"));

        List<String> ordered = registry.orderNamespaces(List.of("mystery", "forge", "strengthen"));

        assertEquals(List.of("forge", "mystery", "strengthen"), ordered);
    }
}
