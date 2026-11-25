package emaki.jiuwu.craft.corelib.api.data;

import java.nio.charset.StandardCharsets;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * Decoding helpers for the payload bytes carried by {@link DomainSnapshot}.
 *
 * <p>The database backends store a YAML document per player domain. Every domain repository that
 * reads one needs the same empty-payload rule, so the rule lives here instead of being repeated in
 * each plugin's store class.
 */
public final class DomainPayloads {

    private DomainPayloads() {
    }

    /**
     * Decodes the YAML payload of one snapshot.
     *
     * @param snapshot stored row snapshot; {@code null} and an empty payload both decode to an
     *                 empty section
     * @return the decoded section, never {@code null}
     */
    public static YamlSection decodeYaml(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return new MapYamlSection();
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }
}
