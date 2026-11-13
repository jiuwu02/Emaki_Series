package emaki.jiuwu.craft.kits.config;

import emaki.jiuwu.craft.kits.model.KitDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AppConfig(String version,
                        String language,
                        String timeZone,
                        StorageSettings storage,
                        EconomySettings economy,
                        InviteSettings invite,
                        CdkSettings cdk,
                        NestedSettings nested,
                        JoinSettings join,
                        Map<String, KitDefinition> kits,
                        List<ConfigIssue> issues) {

    public record StorageSettings(String backend, int saveDelayTicks, boolean atomicWrites) {}
    public record EconomySettings(String provider, String currencyId) {}
    public record InviteSettings(boolean enabled, boolean requireNewPlayer,
                                 boolean allowMutual, int maxBindsPerInvitee,
                                 List<String> newPlayerMarkers) {
        public InviteSettings {
            newPlayerMarkers = newPlayerMarkers == null ? List.of() : List.copyOf(newPlayerMarkers);
        }
    }
    public record CdkSettings(boolean caseSensitive, int maxCodesPerKit) {}
    public record NestedSettings(int maxDepth, boolean countPassToNested) {}
    public record JoinSettings(long missedWindowSeconds, String inviteCodeType) {}
    public record ConfigIssue(String path, String reasonKey) {}

    public static final String CURRENT_VERSION = "1.0.0";

    public static AppConfig defaults() {
        Map<String, KitDefinition> empty = new LinkedHashMap<>();
        return new AppConfig(CURRENT_VERSION, "zh_CN", "Asia/Shanghai",
                new StorageSettings("yaml", 20, true),
                new EconomySettings("auto", ""),
                new InviteSettings(true, true, false, 1, List.of("first_join")),
                new CdkSettings(false, 10000),
                new NestedSettings(4, false),
                new JoinSettings(7200L, "uuid"),
                empty, List.of());
    }

    public AppConfig withKits(Map<String, KitDefinition> parsed, List<ConfigIssue> recorded) {
        return new AppConfig(version, language, timeZone, storage, economy, invite, cdk, nested, join,
                parsed == null ? Map.of() : Map.copyOf(parsed),
                recorded == null ? List.of() : List.copyOf(recorded));
    }

    public KitDefinition kit(String id) {
        return kits.get(id);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}
