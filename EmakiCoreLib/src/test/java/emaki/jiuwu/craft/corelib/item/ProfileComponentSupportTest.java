package emaki.jiuwu.craft.corelib.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ProfileComponentSupportTest {

    private static final String TEXTURE_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWJjIn19fQ==";
    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/abc";

    @Test
    void profileWithPlayerNameOnlyCarriesName() {
        Map<String, Object> profile = ProfileComponentSupport.profileWithPlayerName("Notch");
        assertEquals("Notch", profile.get("name"));
        assertFalse(profile.containsKey("properties"));
    }

    @Test
    void profileWithTextureValueBuildsTexturesProperty() {
        Map<String, Object> profile = ProfileComponentSupport.profileWithTextureValue(TEXTURE_VALUE);
        assertEquals(TEXTURE_VALUE, ProfileComponentSupport.textureValueOf(profile));
        assertNull(ProfileComponentSupport.textureSignatureOf(profile));
        assertFalse(profile.containsKey("name"));
    }

    @Test
    void profileWithTextureValueCarriesSignatureWhenProvided() {
        Map<String, Object> profile = ProfileComponentSupport.profileWithTextureValue(TEXTURE_VALUE, "sig");
        assertEquals("sig", ProfileComponentSupport.textureSignatureOf(profile));
    }

    @Test
    void textureUrlRoundTripsThroughEncodedPayload() {
        String encoded = ProfileComponentSupport.encodeTextureUrl(TEXTURE_URL);
        assertEquals(TEXTURE_URL, ProfileComponentSupport.decodeTextureUrl(encoded));

        Map<String, Object> profile = ProfileComponentSupport.profileWithTextureUrl(TEXTURE_URL);
        assertEquals(TEXTURE_URL, ProfileComponentSupport.textureUrlOf(profile));
        assertEquals(encoded, ProfileComponentSupport.textureValueOf(profile));
    }

    @Test
    void withTextureValueReplacesTexturesAndKeepsSiblingProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", "textures");
        properties.put("value", "old");
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("name", "other");
        other.put("value", "kept");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "Notch");
        profile.put("properties", List.of(properties, other));

        Map<String, Object> updated = ProfileComponentSupport.withTextureValue(profile, TEXTURE_VALUE, "sig");

        assertEquals("Notch", updated.get("name"));
        assertEquals(TEXTURE_VALUE, ProfileComponentSupport.textureValueOf(updated));
        assertEquals("sig", ProfileComponentSupport.textureSignatureOf(updated));

        List<?> updatedProperties = (List<?>) updated.get("properties");
        assertEquals(2, updatedProperties.size());
        assertTrue(updatedProperties.stream()
                .anyMatch(candidate -> candidate instanceof Map<?, ?> map && "other".equals(map.get("name"))));
    }

    @Test
    void withTextureValueAddsTexturesPropertyWhenAbsent() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "Notch");

        Map<String, Object> updated = ProfileComponentSupport.withTextureValue(profile, TEXTURE_VALUE);

        assertEquals("Notch", updated.get("name"));
        assertEquals(TEXTURE_VALUE, ProfileComponentSupport.textureValueOf(updated));
    }

    @Test
    void decodeTextureUrlRejectsMalformedInput() {
        assertNull(ProfileComponentSupport.decodeTextureUrl(null));
        assertNull(ProfileComponentSupport.decodeTextureUrl(""));
        assertNull(ProfileComponentSupport.decodeTextureUrl("not base64 !!!"));
        assertNull(ProfileComponentSupport.decodeTextureUrl("aGVsbG8="));
    }

    @Test
    void isProfileShapeAcceptsProfileMapsOnly() {
        assertTrue(ProfileComponentSupport.isProfileShape(Map.of("name", "Notch")));
        assertTrue(ProfileComponentSupport.isProfileShape(Map.of("properties", List.of())));
        assertFalse(ProfileComponentSupport.isProfileShape("Notch"));
        assertFalse(ProfileComponentSupport.isProfileShape(Map.of("value", "x")));
        assertFalse(ProfileComponentSupport.isProfileShape(null));
    }
}
