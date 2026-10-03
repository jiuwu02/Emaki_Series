package emaki.jiuwu.craft.corelib.item;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ProfileComponentSupport {

    public static final String PROFILE_COMPONENT_ID = "minecraft:profile";
    public static final String TEXTURES_PROPERTY = "textures";

    private static final String SKIN_TEXTURE_KEY = "SKIN";

    private ProfileComponentSupport() {
    }

    public static Map<String, Object> profileWithPlayerName(String playerName) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", Texts.toStringSafe(playerName).trim());
        return profile;
    }

    public static Map<String, Object> profileWithTextureValue(String textureValue) {
        return profileWithTextureValue(textureValue, null);
    }

    public static Map<String, Object> profileWithTextureValue(String textureValue, String signature) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", TEXTURES_PROPERTY);
        property.put("value", textureValue == null ? "" : textureValue);
        if (!Texts.isBlank(signature)) {
            property.put("signature", signature);
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("properties", new ArrayList<>(List.of(property)));
        return profile;
    }

    public static Map<String, Object> profileWithTextureUrl(String textureUrl) {
        return profileWithTextureValue(encodeTextureUrl(textureUrl), null);
    }

    public static Map<String, Object> withTextureValue(Map<String, Object> profile, String textureValue) {
        return withTextureValue(profile, textureValue, null);
    }

    public static Map<String, Object> withTextureValue(Map<String, Object> profile,
            String textureValue,
            String signature) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (profile != null) {
            for (Map.Entry<String, Object> entry : profile.entrySet()) {
                if (!"properties".equals(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        List<Object> properties = new ArrayList<>();
        for (Map<String, Object> existing : readProperties(profile)) {
            if (!TEXTURES_PROPERTY.equals(existing.get("name"))) {
                properties.add(existing);
            }
        }
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", TEXTURES_PROPERTY);
        property.put("value", textureValue == null ? "" : textureValue);
        if (!Texts.isBlank(signature)) {
            property.put("signature", signature);
        }
        properties.add(property);
        result.put("properties", properties);
        return result;
    }

    public static String textureValueOf(Object profile) {
        for (Map<String, Object> property : readProperties(profile)) {
            if (TEXTURES_PROPERTY.equals(property.get("name"))) {
                Object value = property.get("value");
                return value == null ? null : Texts.toStringSafe(value);
            }
        }
        return null;
    }

    public static String textureSignatureOf(Object profile) {
        for (Map<String, Object> property : readProperties(profile)) {
            if (TEXTURES_PROPERTY.equals(property.get("name"))) {
                Object signature = property.get("signature");
                return signature == null ? null : Texts.toStringSafe(signature);
            }
        }
        return null;
    }

    public static String textureUrlOf(Object profile) {
        return decodeTextureUrl(textureValueOf(profile));
    }

    public static String encodeTextureUrl(String textureUrl) {
        if (Texts.isBlank(textureUrl)) {
            return "";
        }
        JsonObject skin = new JsonObject();
        JsonObject url = new JsonObject();
        url.addProperty("url", textureUrl.trim());
        JsonObject textures = new JsonObject();
        textures.add(SKIN_TEXTURE_KEY, url);
        skin.add(TEXTURES_PROPERTY, textures);
        return Base64.getEncoder().encodeToString(skin.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeTextureUrl(String textureValue) {
        if (Texts.isBlank(textureValue)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(textureValue.trim());
            JsonObject root = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject(TEXTURES_PROPERTY);
            if (textures == null) {
                return null;
            }
            JsonObject skin = textures.getAsJsonObject(SKIN_TEXTURE_KEY);
            if (skin == null) {
                return null;
            }
            String url = skin.get("url") == null ? null : skin.get("url").getAsString();
            return Texts.isBlank(url) ? null : url;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static boolean isProfileShape(Object value) {
        return value instanceof Map<?, ?> map
                && (map.containsKey("name") || map.containsKey("id") || map.containsKey("properties"));
    }

    private static List<Map<String, Object>> readProperties(Object profile) {
        if (!(profile instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object raw = map.get("properties");
        if (!(raw instanceof List<?> configured)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(configured.size());
        for (Object element : configured) {
            if (element instanceof Map<?, ?> property) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : property.entrySet()) {
                    copy.put(Texts.toStringSafe(entry.getKey()), entry.getValue());
                }
                result.add(copy);
            }
        }
        return result;
    }
}
