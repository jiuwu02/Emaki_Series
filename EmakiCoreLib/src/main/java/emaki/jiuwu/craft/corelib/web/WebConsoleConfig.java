package emaki.jiuwu.craft.corelib.web;

import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record WebConsoleConfig(
        boolean enabled,
        String host,
        int port,
        boolean publicAccessWarning,
        Auth auth,
        Security security,
        ConfigBrowser configBrowser
) {

    public static WebConsoleConfig defaults() {
        return new WebConsoleConfig(
                false,
                "127.0.0.1",
                38765,
                true,
                new Auth("admin", "change-me", 60),
                new Security(false, 256, List.of()),
                new ConfigBrowser(512, List.of(".yml", ".yaml", ".json", ".txt"))
        );
    }

    public static WebConsoleConfig fromConfig(YamlSection section) {
        WebConsoleConfig defaults = defaults();
        if (section == null) {
            return defaults;
        }
        YamlSection authSection = section.getSection("auth");
        YamlSection securitySection = section.getSection("security");
        YamlSection browserSection = section.getSection("config_browser");
        return new WebConsoleConfig(
                section.getBoolean("enabled", defaults.enabled()),
                safeString(section.getString("host", defaults.host()), defaults.host()),
                clampPort(section.getInt("port", defaults.port())),
                section.getBoolean("public_access_warning", defaults.publicAccessWarning()),
                Auth.fromConfig(authSection, defaults.auth()),
                Security.fromConfig(securitySection, defaults.security()),
                ConfigBrowser.fromConfig(browserSection, defaults.configBrowser())
        );
    }

    public boolean hasUnsafeDefaultPassword() {
        return auth == null || Texts.isBlank(auth.password()) || "change-me".equals(auth.password());
    }

    private static String safeString(String value, String fallback) {
        return Texts.isBlank(value) ? fallback : value.trim();
    }

    private static int clampPort(int port) {
        if (port < 1 || port > 65535) {
            return 38765;
        }
        return port;
    }

    public record Auth(String username, String password, int sessionTimeoutMinutes) {

        static Auth fromConfig(YamlSection section, Auth defaults) {
            if (section == null) {
                return defaults;
            }
            return new Auth(
                    safeString(section.getString("username", defaults.username()), defaults.username()),
                    safeString(section.getString("password", defaults.password()), defaults.password()),
                    Math.max(1, section.getInt("session_timeout_minutes", defaults.sessionTimeoutMinutes()))
            );
        }
    }

    public record Security(boolean allowConfigWrite, int maxRequestBodyKb, List<String> allowedModules) {

        static Security fromConfig(YamlSection section, Security defaults) {
            if (section == null) {
                return defaults;
            }
            List<String> modules = section.getStringList("allowed_modules");
            if (modules == null || modules.isEmpty()) {
                modules = defaults.allowedModules();
            }
            return new Security(
                    section.getBoolean("allow_config_write", defaults.allowConfigWrite()),
                    Math.max(1, section.getInt("max_request_body_kb", defaults.maxRequestBodyKb())),
                    List.copyOf(modules)
            );
        }
    }

    public record ConfigBrowser(int maxFileSizeKb, List<String> allowedExtensions) {

        static ConfigBrowser fromConfig(YamlSection section, ConfigBrowser defaults) {
            if (section == null) {
                return defaults;
            }
            List<String> extensions = section.getStringList("allowed_extensions");
            if (extensions == null || extensions.isEmpty()) {
                extensions = defaults.allowedExtensions();
            }
            return new ConfigBrowser(
                    Math.max(1, section.getInt("max_file_size_kb", defaults.maxFileSizeKb())),
                    List.copyOf(extensions.stream()
                            .map(String::trim)
                            .filter(value -> !value.isEmpty())
                            .map(value -> value.startsWith(".") ? value.toLowerCase(java.util.Locale.ROOT) : "." + value.toLowerCase(java.util.Locale.ROOT))
                            .toList())
            );
        }
    }
}
