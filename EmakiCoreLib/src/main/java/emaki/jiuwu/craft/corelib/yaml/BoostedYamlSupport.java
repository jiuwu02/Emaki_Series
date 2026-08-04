package emaki.jiuwu.craft.corelib.yaml;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 4 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class BoostedYamlSupport {

    private BoostedYamlSupport() {
    }

    public static MapYamlSection load(InputStream inputStream) {
        return emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport.load(inputStream);
    }

    public static MapYamlSection load(String payload) {
        return emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport.load(payload);
    }

    public static MapYamlSection load(Reader reader) {
        return emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport.load(reader);
    }

    public static String dump(Map<String, ?> values) {
        return emaki.jiuwu.craft.corelib.api.yaml.BoostedYamlSupport.dump(values);
    }
}
