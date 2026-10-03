package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

class EmakiItemStartupResourceTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void everyGuiTemplateIsParseableAndFillsTheChest() throws IOException {
        Path guiDirectory = RESOURCES.resolve("gui");
        List<Path> templates;
        try (var stream = Files.list(guiDirectory)) {
            templates = stream.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        }
        assertTrue(templates.size() >= 3, "expected at least the browser and two editor templates");
        for (Path template : templates) {
            Map<String, Object> root = YamlFiles.load(Files.readString(template, StandardCharsets.UTF_8)).asMap();
            assertTrue(root.get("id") != null, template + " must declare an id");
            assertTrue(root.get("slots") instanceof Map<?, ?>, template + " must declare slots");
            List<Integer> placed = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) root.get("slots")).entrySet()) {
                Object slots = ((Map<?, ?>) entry.getValue()).get("slots");
                assertTrue(slots instanceof List<?>, template + "#" + entry.getKey() + " must declare slot indices");
                for (Object index : (List<?>) slots) {
                    placed.add(Integer.parseInt(String.valueOf(index)));
                }
            }
            assertEquals(placed.size(), new HashSet<>(placed).size(), template + " has duplicate slot indices");
            for (int index : placed) {
                assertTrue(index >= 0 && index <= 53, template + " places a slot outside a 6-row chest: " + index);
            }
        }
    }

    @Test
    void everySubmenuTemplateOffersBothBackButtons() throws IOException {
        Map<String, Object> root = YamlFiles.load(Files.readString(
                RESOURCES.resolve("gui").resolve("item_editor_page_gui.yml"), StandardCharsets.UTF_8)).asMap();
        Map<?, ?> slots = (Map<?, ?>) root.get("slots");
        Set<String> types = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : slots.entrySet()) {
            types.add(String.valueOf(((Map<?, ?>) entry.getValue()).get("type")));
        }
        assertTrue(types.contains("back_parent"), "submenu must offer back-to-parent");
        assertTrue(types.contains("back_home"), "submenu must offer back-to-home");
    }

    @Test
    void languageKeySetsAreSymmetric() throws IOException {
        Set<String> chinese = flatten(YamlFiles.load(Files.readString(
                RESOURCES.resolve("lang").resolve("zh_CN.yml"), StandardCharsets.UTF_8)).asMap(), "");
        Set<String> english = flatten(YamlFiles.load(Files.readString(
                RESOURCES.resolve("lang").resolve("en_US.yml"), StandardCharsets.UTF_8)).asMap(), "");
        assertEquals(List.of(), new ArrayList<>(difference(chinese, english)), "zh_CN has keys missing in en_US");
        assertEquals(List.of(), new ArrayList<>(difference(english, chinese)), "en_US has keys missing in zh_CN");
    }

    @Test
    void resourceVersionsMatchModulePom() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        String expected = pom.replaceAll("(?s).*?<artifactId>emaki-item</artifactId>\\s*<version>([^<]+)</version>.*", "$1");
        for (String resource : List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml")) {
            Object version = YamlFiles.load(Files.readString(RESOURCES.resolve(resource), StandardCharsets.UTF_8))
                    .asMap().get("version");
            assertEquals(expected, String.valueOf(version), resource + " version must match the module pom");
        }
    }

    @Test
    void shippedItemAndSetResourcesParse() throws IOException {
        List<Path> files = new ArrayList<>();
        collectYaml(RESOURCES.resolve("items"), files);
        collectYaml(RESOURCES.resolve("sets"), files);
        assertTrue(files.size() >= 3, "expected shipped item and set resources");
        for (Path file : files) {
            Map<String, Object> root = YamlFiles.load(Files.readString(file, StandardCharsets.UTF_8)).asMap();
            assertTrue(root.get("id") != null || root.get("display_name") != null
                            || root.get("pieces") != null || root.get("icon") != null,
                    file + " does not look like a recognised EmakiItem resource");
        }
    }

    private static void collectYaml(Path directory, List<Path> sink) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.filter(path -> path.toString().endsWith(".yml")).forEach(sink::add);
        }
    }

    private static Set<String> flatten(Map<?, ?> root, String prefix) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            keys.add(key);
            if (entry.getValue() instanceof Map<?, ?> nested) {
                keys.addAll(flatten(nested, key));
            }
        }
        return keys;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
