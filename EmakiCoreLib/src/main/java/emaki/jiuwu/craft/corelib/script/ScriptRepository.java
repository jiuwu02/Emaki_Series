package emaki.jiuwu.craft.corelib.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptRepository {

    private final Path root;
    private final ScriptConfig.Security security;

    public ScriptRepository(Path root, ScriptConfig.Security security) {
        this.root = root == null ? Path.of("scripts").toAbsolutePath().normalize() : root.toAbsolutePath().normalize();
        this.security = security == null ? ScriptConfig.Security.defaults() : security;
    }

    public Path root() {
        return root;
    }

    public void ensureDirectories(List<String> directories) throws IOException {
        Files.createDirectories(root);
        if (directories == null) {
            return;
        }
        for (String directory : directories) {
            Path resolved = resolveSafeDirectory(directory);
            Files.createDirectories(resolved);
        }
    }

    public void releaseDefaultScripts(Plugin plugin) {
        Path examplesDir = root.resolve("examples");
        try {
            Files.createDirectories(examplesDir);
        } catch (IOException ignored) {
            return;
        }
        // 只在 examples 目录为空时释放
        try (Stream<Path> stream = Files.list(examplesDir)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        } catch (IOException ignored) {
            return;
        }
        // 释放默认示例脚本
        String[] examples = {
            "attribute_buff.js",
            "cooking_reward.js",
            "forge_success.js",
            "hello.js",
            "item_right_click.js",
            "skills_upgrade_success.js",
            "strengthen_success.js"
        };
        for (String name : examples) {
            String resourcePath = "scripts/examples/" + name;
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) continue;
                Path target = examplesDir.resolve(name);
                if (!Files.exists(target)) {
                    Files.copy(input, target);
                }
            } catch (IOException ignored) {
                // 单个文件释放失败不影响其他
            }
        }
    }

    public Optional<ScriptSource> find(String scriptPath) {
        try {
            Path resolved = resolveScript(scriptPath);
            if (!Files.isRegularFile(resolved)) {
                return Optional.empty();
            }
            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            long lastModified = Files.getLastModifiedTime(resolved).toMillis();
            String logical = normalizeLogicalPath(root.relativize(resolved));
            return Optional.of(new ScriptSource(logical, resolved, content, lastModified, sha256(content)));
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    public List<String> scan() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> scripts = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".js"))
                    .map(path -> normalizeLogicalPath(root.relativize(path.toAbsolutePath().normalize())))
                    .sorted()
                    .forEach(scripts::add);
        }
        return List.copyOf(scripts);
    }

    public Path resolveScript(String scriptPath) {
        if (Texts.isBlank(scriptPath)) {
            throw new IllegalArgumentException("Script path cannot be blank.");
        }
        String normalizedRaw = Texts.trim(scriptPath).replace('\\', '/');
        for (String denied : security.deniedPathFragments()) {
            if (Texts.isNotBlank(denied) && normalizedRaw.contains(denied)) {
                throw new IllegalArgumentException("Script path contains denied fragment: " + denied);
            }
        }
        if (normalizedRaw.startsWith("/") || normalizedRaw.startsWith("~")) {
            throw new IllegalArgumentException("Script path must be relative: " + scriptPath);
        }
        Path resolved = root.resolve(normalizedRaw).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Script path escapes script root: " + scriptPath);
        }
        return resolved;
    }

    private Path resolveSafeDirectory(String directory) {
        if (Texts.isBlank(directory)) {
            return root;
        }
        Path resolved = root.resolve(Texts.trim(directory).replace('\\', '/')).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Script directory escapes script root: " + directory);
        }
        return resolved;
    }

    private String normalizeLogicalPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }
}
