package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.api.world.WorldMountSupport;

public final class WorldMountBridge {

    private static final String WORLD_MOUNT_IMPL = "emaki.jiuwu.craft.nms.WorldMountSupportImpl";
    private static final String BRIDGE_ARTIFACT_PATH = "emaki/jiuwu/craft/emakinms";

    private static volatile WorldMountSupport support;

    private WorldMountBridge() {
    }

    public static WorldMountSupport load(Path dataDirectory) {
        WorldMountSupport mounted = support;
        if (mounted != null) {
            return mounted;
        }
        synchronized (WorldMountBridge.class) {
            if (support == null) {
                support = create(dataDirectory);
            }
            return support;
        }
    }

    private static WorldMountSupport create(Path dataDirectory) {
        Path bridgeJar = findBridgeJar(dataDirectory);
        if (bridgeJar == null) {
            throw new IllegalStateException("副本世界桥运行库未就绪: " + dataDirectory.resolve("libraries") + "/" + BRIDGE_ARTIFACT_PATH + " 下没有 emakinms jar");
        }
        try {
            URLClassLoader loader = new URLClassLoader(new URL[] { bridgeJar.toUri().toURL() }, WorldMountSupport.class.getClassLoader());
            Object instance = Class.forName(WORLD_MOUNT_IMPL, true, loader).getDeclaredConstructor().newInstance();
            return (WorldMountSupport) instance;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException | IOException exception) {
            throw new IllegalStateException("副本世界桥装载失败: 无法从 " + bridgeJar.getFileName() + " 实例化 " + WORLD_MOUNT_IMPL, exception);
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
                    .sorted(Comparator.comparingLong(WorldMountBridge::lastModified))
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
