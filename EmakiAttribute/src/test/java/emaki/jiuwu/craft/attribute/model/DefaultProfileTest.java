package emaki.jiuwu.craft.attribute.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class DefaultProfileTest {

    @Test
    void keyedResourcesUseNodeNamesAsFallbackIds() {
        DefaultProfile profile = DefaultProfile.fromMap(Map.of(
                "resources", Map.of(
                        "health", Map.of(
                                "display_name", "生命值",
                                "default_max", 20D,
                                "min_max", 1D,
                                "max_max", 2048D,
                                "sync_to_bukkit", true,
                                "full_on_init", true
                        ),
                        "mana", Map.of(
                                "display_name", "法力值",
                                "default_max", 100D,
                                "min_max", 0D,
                                "max_max", 99999D,
                                "sync_to_bukkit", false,
                                "full_on_init", true
                        )
                )
        ));

        assertTrue(profile.resources().containsKey("health"));
        assertTrue(profile.resources().containsKey("mana"));

        ResourceDefinition health = profile.resources().get("health");
        assertEquals("health", health.id());
        assertEquals("生命值", health.displayName());
        assertEquals(20D, health.defaultMax());
        assertTrue(health.syncToBukkit());

        ResourceDefinition mana = profile.resources().get("mana");
        assertEquals("mana", mana.id());
        assertEquals("法力值", mana.displayName());
        assertEquals(100D, mana.defaultMax());
        assertFalse(mana.syncToBukkit());
    }

    @Test
    void explicitResourceIdOverridesNodeName() {
        DefaultProfile profile = DefaultProfile.fromMap(Map.of(
                "resources", Map.of(
                        "health", Map.of(
                                "id", "custom_health",
                                "default_max", 40D
                        )
                )
        ));

        assertFalse(profile.resources().containsKey("health"));
        assertTrue(profile.resources().containsKey("custom_health"));
        assertEquals("custom_health", profile.resources().get("custom_health").id());
        assertEquals(40D, profile.resources().get("custom_health").defaultMax());
    }
}
