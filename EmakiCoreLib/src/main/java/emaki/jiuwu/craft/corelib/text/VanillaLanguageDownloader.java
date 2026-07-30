package emaki.jiuwu.craft.corelib.text;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Downloads a vanilla language file from Mojang's official asset index.
 *
 * <p>Vanilla item and block names only exist as translation keys on the server,
 * so a server-side feature that needs the localized name has to obtain the
 * client's language table itself. The file is fetched once and cached on disk;
 * later starts reuse the cache and perform no network access at all.
 *
 * <p>Resolution chain, all official Mojang endpoints:
 * <ol>
 *   <li>{@code version_manifest_v2.json} locates the version entry;</li>
 *   <li>the version JSON exposes {@code assetIndex.url};</li>
 *   <li>the asset index maps {@code minecraft/lang/<locale>.json} to a hash;</li>
 *   <li>the object is downloaded from {@code resources.download.minecraft.net}.</li>
 * </ol>
 *
 * <p>Every failure is non-fatal: the caller keeps working without a translation
 * table rather than blocking startup. All methods here perform blocking IO and
 * must never be called from a server thread.
 */
public final class VanillaLanguageDownloader {

    private static final String VERSION_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCE_BASE_URL =
            "https://resources.download.minecraft.net/";
    private static final String USER_AGENT = "EmakiCoreLib-LanguageDownloader";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final Logger logger;
    private final Path cacheDirectory;

    public VanillaLanguageDownloader(Logger logger, Path cacheDirectory) {
        this.logger = logger;
        this.cacheDirectory = cacheDirectory;
    }

    /**
     * Loads the language table, preferring the on-disk cache over the network.
     *
     * @param minecraftVersion the running server's Minecraft version
     * @param locale the vanilla locale id, such as {@code zh_cn}
     * @return the translation table, or an empty map when it cannot be obtained
     */
    public Map<String, String> load(String minecraftVersion, String locale) {
        String safeLocale = locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
        if (safeLocale.isEmpty()) {
            return Map.of();
        }
        Path cacheFile = cacheDirectory.resolve(safeLocale + "-" + sanitize(minecraftVersion) + ".json");
        Map<String, String> cached = readCache(cacheFile);
        if (!cached.isEmpty()) {
            return cached;
        }
        String body = downloadLanguageFile(minecraftVersion, safeLocale);
        if (body == null) {
            return Map.of();
        }
        Map<String, String> parsed = parseLanguageJson(body);
        if (parsed.isEmpty()) {
            return Map.of();
        }
        writeCache(cacheFile, body);
        return parsed;
    }

    private Map<String, String> readCache(Path cacheFile) {
        if (!Files.isRegularFile(cacheFile)) {
            return Map.of();
        }
        try {
            String body = Files.readString(cacheFile, StandardCharsets.UTF_8);
            Map<String, String> parsed = parseLanguageJson(body);
            if (parsed.isEmpty()) {
                Files.deleteIfExists(cacheFile);
            }
            return parsed;
        } catch (IOException | RuntimeException exception) {
            logger.fine("Vanilla language cache unreadable, will re-download: " + exception.getMessage());
            return Map.of();
        }
    }

    private void writeCache(Path cacheFile, String body) {
        Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(temporary, body, StandardCharsets.UTF_8);
            Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.fine("Failed to cache vanilla language file: " + exception.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException _) {
                // Nothing further can be done about a leftover temporary file.
            }
        }
    }

    /**
     * Walks Mojang's manifest chain and returns the raw language JSON.
     *
     * @return the file body, or {@code null} when any step fails
     */
    private String downloadLanguageFile(String minecraftVersion, String locale) {
        String versionUrl = resolveVersionUrl(minecraftVersion);
        if (versionUrl == null) {
            return null;
        }
        String assetIndexUrl = readStringPath(versionUrl, "assetIndex", "url");
        if (assetIndexUrl == null) {
            logger.fine("Version metadata has no assetIndex.url");
            return null;
        }
        String hash = resolveObjectHash(assetIndexUrl, "minecraft/lang/" + locale + ".json");
        if (hash == null) {
            logger.fine("Asset index has no entry for locale " + locale);
            return null;
        }
        return fetch(RESOURCE_BASE_URL + hash.substring(0, 2) + "/" + hash);
    }

    /**
     * Locates the version metadata URL for the running server.
     *
     * <p>An exact id match is preferred. Because a server may report a version
     * Mojang's manifest does not list verbatim, the search falls back to the
     * newest release sharing the same {@code major.minor} prefix, then to the
     * manifest's latest release. Language files barely differ inside one minor
     * series, so an approximate match is still useful.
     */
    private String resolveVersionUrl(String minecraftVersion) {
        String manifest = fetch(VERSION_MANIFEST_URL);
        if (manifest == null) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(manifest).getAsJsonObject();
            var versions = root.getAsJsonArray("versions");
            if (versions == null) {
                return null;
            }
            String requested = minecraftVersion == null ? "" : minecraftVersion.trim();
            String prefix = minorPrefix(requested);
            String prefixMatch = null;
            for (JsonElement element : versions) {
                JsonObject entry = element.getAsJsonObject();
                String id = optionalString(entry, "id");
                String url = optionalString(entry, "url");
                if (id == null || url == null) {
                    continue;
                }
                if (id.equals(requested)) {
                    return url;
                }
                boolean release = "release".equals(optionalString(entry, "type"));
                // Entries are ordered newest first, so the first prefix hit wins.
                if (prefixMatch == null && release && !prefix.isEmpty() && id.startsWith(prefix)) {
                    prefixMatch = url;
                }
            }
            if (prefixMatch != null) {
                return prefixMatch;
            }
            JsonObject latest = root.getAsJsonObject("latest");
            String latestRelease = latest == null ? null : optionalString(latest, "release");
            if (latestRelease == null) {
                return null;
            }
            for (JsonElement element : versions) {
                JsonObject entry = element.getAsJsonObject();
                if (latestRelease.equals(optionalString(entry, "id"))) {
                    return optionalString(entry, "url");
                }
            }
            return null;
        } catch (RuntimeException exception) {
            logger.fine("Failed to read version manifest: " + exception.getMessage());
            return null;
        }
    }

    private String resolveObjectHash(String assetIndexUrl, String objectPath) {
        String index = fetch(assetIndexUrl);
        if (index == null) {
            return null;
        }
        try {
            JsonObject objects = JsonParser.parseString(index).getAsJsonObject().getAsJsonObject("objects");
            if (objects == null) {
                return null;
            }
            JsonObject entry = objects.getAsJsonObject(objectPath);
            String hash = entry == null ? null : optionalString(entry, "hash");
            return hash != null && hash.length() >= 2 ? hash : null;
        } catch (RuntimeException exception) {
            logger.fine("Failed to read asset index: " + exception.getMessage());
            return null;
        }
    }

    private String readStringPath(String url, String objectName, String field) {
        String body = fetch(url);
        if (body == null) {
            return null;
        }
        try {
            JsonObject nested = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject(objectName);
            return nested == null ? null : optionalString(nested, field);
        } catch (RuntimeException exception) {
            logger.fine("Failed to read " + objectName + "." + field + ": " + exception.getMessage());
            return null;
        }
    }

    /**
     * Parses a flat vanilla language file.
     *
     * <p>Only string values are kept; the file is a single-level
     * {@code {"key": "text"}} object and any other shape is treated as invalid
     * rather than partially accepted.
     */
    private Map<String, String> parseLanguageJson(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            Map<String, String> table = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    table.put(entry.getKey(), value.getAsString());
                }
            }
            return table;
        } catch (RuntimeException exception) {
            logger.fine("Vanilla language file is not a flat JSON object: " + exception.getMessage());
            return Map.of();
        }
    }

    /**
     * Performs one blocking GET and returns the body as UTF-8 text.
     *
     * <p>Responses are capped at {@value #MAX_RESPONSE_BYTES} bytes so a wrong or
     * hostile endpoint cannot exhaust heap.
     *
     * @return the response body, or {@code null} on any failure
     */
    private String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            URL target = URI.create(url).toURL();
            connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.fine("Language download returned HTTP " + responseCode + " for " + url);
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] bytes = inputStream.readNBytes(MAX_RESPONSE_BYTES);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException exception) {
            logger.fine("Language download failed for " + url + ": " + exception.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String minorPrefix(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        int first = version.indexOf('.');
        if (first < 0) {
            return "";
        }
        int second = version.indexOf('.', first + 1);
        return second < 0 ? version + "." : version.substring(0, second + 1);
    }

    private static String optionalString(JsonObject object, String field) {
        JsonElement element = object == null ? null : object.get(field);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static String sanitize(String version) {
        return version == null || version.isBlank()
                ? "unknown"
                : version.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
