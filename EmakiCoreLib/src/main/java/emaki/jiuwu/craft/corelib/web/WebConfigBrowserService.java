package emaki.jiuwu.craft.corelib.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

public final class WebConfigBrowserService {

    private final JavaPlugin plugin;
    private final WebConsoleConfig config;

    public WebConfigBrowserService(JavaPlugin plugin, WebConsoleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public List<Map<String, Object>> tree(String moduleId) throws IOException {
        Path root = moduleRoot(moduleId);
        List<Map<String, Object>> files = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isAllowedFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> files.add(fileEntry(root, path)));
        }
        return files;
    }

    public Map<String, Object> read(String moduleId, String relativePath) throws IOException {
        Path root = moduleRoot(moduleId);
        Path target = WebPathSecurity.resolveInside(root, relativePath);
        if (target == null || !Files.isRegularFile(target) || !isAllowedFile(target)) {
            throw new IOException("文件不在允许访问范围内");
        }
        long size = Files.size(target);
        long maxBytes = config.configBrowser().maxFileSizeKb() * 1024L;
        if (size > maxBytes) {
            throw new IOException("文件超过 Web Console 读取上限: " + config.configBrowser().maxFileSizeKb() + "KB");
        }
        Map<String, Object> result = fileEntry(root, target);
        result.put("content", Files.readString(target, StandardCharsets.UTF_8));
        return result;
    }

    private Path moduleRoot(String moduleId) throws IOException {
        if (moduleId == null || !config.security().allowedModules().contains(moduleId)) {
            throw new IOException("模块不在允许访问列表中");
        }
        return plugin.getDataFolder().toPath().getParent().resolve(moduleId).toAbsolutePath().normalize();
    }

    private Map<String, Object> fileEntry(Path root, Path path) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", root.relativize(path).toString().replace('\\', '/'));
        entry.put("name", path.getFileName().toString());
        entry.put("type", "file");
        entry.put("editable", false);
        try {
            entry.put("size", Files.size(path));
            entry.put("lastModified", Files.getLastModifiedTime(path).toMillis());
        } catch (IOException exception) {
            entry.put("size", 0L);
            entry.put("lastModified", 0L);
        }
        return entry;
    }

    private boolean isAllowedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return config.configBrowser().allowedExtensions().stream().anyMatch(name::endsWith);
    }
}
