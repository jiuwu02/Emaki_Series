package emaki.jiuwu.craft.corelib.editor;

import java.io.File;
import java.util.function.BiFunction;

import org.bukkit.Material;

public interface EditorBrowserRoot {

    String id();

    String directory();

    default String fileName() {
        return null;
    }

    String labelKey();

    default Material icon() {
        return null;
    }

    default boolean supportsCreate() {
        return fileName() == null;
    }

    default boolean supportsDelete() {
        return fileName() == null;
    }

    BiFunction<File, String, String> fileId();
}
