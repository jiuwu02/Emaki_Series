package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.api.command.CommandMapGuard;
import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;

public final class NmsBridge {

    private static final String WORLD_MOUNT_IMPL = "emaki.jiuwu.craft.nms.WorldMountSupportImpl";
    private static final String COMMAND_MAP_GUARD_IMPL = "emaki.jiuwu.craft.nms.CommandMapGuardImpl";
    private static final String BRIDGE_ARTIFACT_PATH = "emaki/jiuwu/craft/emakinms";

    private static volatile URLClassLoader loader;
    private static volatile WorldMountSupport worldMountSupport;
    private static volatile CommandMapGuard commandMapGuard;

    private NmsBridge() {
    }

    public static WorldMountSupport worldMountSupport(Path dataDirectory) {
        WorldMountSupport mounted = worldMountSupport;
        if (mounted != null) {
            return mounted;
        }
        synchronized (NmsBridge.class) {
            if (worldMountSupport == null) {
                worldMountSupport = create(loader(dataDirectory), WORLD_MOUNT_IMPL, WorldMountSupport.class);
            }
            return worldMountSupport;
        }
    }

    public static CommandMapGuard commandMapGuard(Path dataDirectory) {
        CommandMapGuard guard = commandMapGuard;
        if (guard != null) {
            return guard;
        }
        synchronized (NmsBridge.class) {
            if (commandMapGuard == null) {
                commandMapGuard = create(loader(dataDirectory), COMMAND_MAP_GUARD_IMPL, CommandMapGuard.class);
            }
            return commandMapGuard;
        }
    }

    private static URLClassLoader loader(Path dataDirectory) {
        URLClassLoader resolved = loader;
        if (resolved != null) {
            return resolved;
        }
        synchronized (NmsBridge.class) {
            if (loader == null) {
                loader = createLoader(dataDirectory);
            }
            return loader;
        }
    }

    private static URLClassLoader createLoader(Path dataDirectory) {
        Path bridgeJar = findBridgeJar(dataDirectory);
        if (bridgeJar == null) {
            throw new IllegalStateException("EmakiNms 运行库未就绪: " + dataDirectory.resolve("libraries") + "/" + BRIDGE_ARTIFACT_PATH + " 下没有 emakinms jar");
        }
        try {
            return new URLClassLoader(new URL[] { bridgeJar.toUri().toURL() }, WorldMountSupport.class.getClassLoader());
        } catch (IOException exception) {
            throw new IllegalStateException("EmakiNms 运行库装载失败: 无法打开 " + bridgeJar.getFileName(), exception);
        }
    }

    private static <T> T create(URLClassLoader loader, String implName, Class<T> apiType) {
        try {
            Object instance = Class.forName(implName, true, loader).getDeclaredConstructor().newInstance();
            return apiType.cast(instance);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            throw new IllegalStateException("EmakiNms 运行库装载失败: 无法从 " + implName + " 实例化实现", exception);
        }
    }

    private static Path findBridgeJar(Path dataDirectory) {
        Path root = dataDirectory.resolve("libraries").resolve(BRIDGE_ARTIFACT_PATH);
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> versions = Files.list(root)) {
            List<Path> jars = versions
                    .filter(Files::isDirectory)
                    .map(version -> version.resolve("emakinms-" + version.getFileName() + ".jar"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(NmsBridge::lastModified))
                    .toList();
            return jars.isEmpty() ? null : jars.get(jars.size() - 1);
        } catch (IOException exception) {
            return null;
        }
    }

    private static long lastModified(Path jar) {
        try {
            return Files.getLastModifiedTime(jar).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }
}
