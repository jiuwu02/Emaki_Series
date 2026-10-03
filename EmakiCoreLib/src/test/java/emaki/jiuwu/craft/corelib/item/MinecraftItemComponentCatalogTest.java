package emaki.jiuwu.craft.corelib.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MinecraftItemComponentCatalogTest {

    private static final String PROFILE = "minecraft:profile";
    private static final String NOTE_BLOCK_SOUND = "minecraft:note_block_sound";
    private static final String CUSTOM_NAME = "minecraft:custom_name";

    private static final Set<String> MATERIALS_NEWER_THAN_PAPER_API_BASELINE = Set.of(
            "minecraft:zombie_nautilus_spawn_egg");

    private final MinecraftItemComponentCatalog catalog = new MinecraftItemComponentCatalog();

    @Test
    void resourceCatalogIsLoadedInsteadOfDegradedFallback() {
        assertTrue(catalog.entries().size() >= 90,
                "component catalog should load from item-components.yml, fallback only has generic entries");
        assertNotNull(catalog.entry(PROFILE));
    }

    @Test
    void resourceCatalogHasNoDuplicateIds() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String componentId : catalog.entries().keySet()) {
            if (!seen.add(componentId)) {
                duplicates.add(componentId);
            }
        }
        assertEquals(List.of(), duplicates);
    }

    @Test
    void profileIsMaterialScopedToPlayerHeads() {
        MinecraftItemComponentCatalog.Entry entry = catalog.entry(PROFILE);
        assertNotNull(entry);
        assertTrue(entry.materialScoped());
        assertEquals(MinecraftItemComponentCatalog.Scope.MATERIAL, entry.scope());
        assertTrue(entry.appliesTo("minecraft:player_head"));
        assertTrue(entry.appliesTo("minecraft:player_wall_head"));
        assertTrue(entry.appliesTo("player_head"));
        assertFalse(entry.appliesTo("minecraft:diamond_sword"));
    }

    @Test
    void noteBlockSoundCoversEverySkull() {
        MinecraftItemComponentCatalog.Entry entry = catalog.entry(NOTE_BLOCK_SOUND);
        assertNotNull(entry);
        assertTrue(entry.appliesTo("minecraft:player_head"));
        assertTrue(entry.appliesTo("minecraft:dragon_head"));
        assertTrue(entry.appliesTo("minecraft:piglin_wall_head"));
        assertEquals(14, entry.appliesTo().size());
    }

    @Test
    void specializedForFiltersByMaterial() {
        List<String> headComponents = ids(catalog.specializedFor("minecraft:player_head"));
        assertTrue(headComponents.contains(PROFILE));
        assertTrue(headComponents.contains(NOTE_BLOCK_SOUND));

        assertEquals(List.of(), ids(catalog.specializedFor("minecraft:diamond_sword")));
        assertEquals(List.of(), ids(catalog.specializedFor(null)));
    }

    @Test
    void universalComponentsApplyToAnyMaterial() {
        MinecraftItemComponentCatalog.Entry entry = catalog.entry(CUSTOM_NAME);
        assertNotNull(entry);
        assertFalse(entry.materialScoped());
        assertEquals(MinecraftItemComponentCatalog.Scope.UNIVERSAL, entry.scope());
        assertTrue(entry.appliesTo("minecraft:diamond_sword"));
        assertTrue(entry.appliesTo("minecraft:player_head"));
        assertTrue(catalog.universalEntries().stream().noneMatch(candidate -> PROFILE.equals(candidate.componentId())));
    }

    @Test
    void isApplicableNormalizesBareMaterialAndComponentIds() {
        assertTrue(catalog.isApplicable(PROFILE, "player_head"));
        assertTrue(catalog.isApplicable(PROFILE, "minecraft:player_head"));
        assertFalse(catalog.isApplicable(PROFILE, "stone"));
        assertFalse(catalog.isApplicable("minecraft:missing_component", "stone"));
    }

    @Test
    void materialScopedEntriesAlwaysDeclareMaterials() {
        List<String> missing = new ArrayList<>();
        for (MinecraftItemComponentCatalog.Entry entry : catalog.entries().values()) {
            boolean scoped = entry.materialScoped();
            boolean declared = !entry.appliesTo().isEmpty();
            if (scoped != declared) {
                missing.add(entry.componentId());
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void everyDeclaredMaterialIsEitherKnownToBaselineOrExplicitlyExempted() {
        List<String> unknown = new ArrayList<>();
        Set<String> usedExemptions = new LinkedHashSet<>();
        for (MinecraftItemComponentCatalog.Entry entry : catalog.materialScopedEntries()) {
            for (String materialId : entry.appliesTo()) {
                if (!"minecraft".equals(namespaceOf(materialId))) {
                    continue;
                }
                String key = materialId.substring(materialId.indexOf(':') + 1).toUpperCase(Locale.ROOT);
                if (Material.getMaterial(key) != null) {
                    continue;
                }
                if (MATERIALS_NEWER_THAN_PAPER_API_BASELINE.contains(materialId)) {
                    usedExemptions.add(materialId);
                    continue;
                }
                unknown.add(entry.componentId() + " -> " + materialId);
            }
        }
        assertEquals(List.of(), unknown);
        assertEquals(MATERIALS_NEWER_THAN_PAPER_API_BASELINE, usedExemptions);
    }

    private static List<String> ids(List<MinecraftItemComponentCatalog.Entry> entries) {
        return entries.stream().map(MinecraftItemComponentCatalog.Entry::componentId).toList();
    }

    private static String namespaceOf(String materialId) {
        int separator = materialId.indexOf(':');
        return separator < 0 ? "minecraft" : materialId.substring(0, separator);
    }
}
