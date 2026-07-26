package emaki.jiuwu.craft.corelib.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.integration.PdcAttributeApi;
import emaki.jiuwu.craft.corelib.api.integration.PdcAttributePayloadSnapshot;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Legacy CoreLib gateway to EmakiAttribute's PDC attribute data.
 *
 * <p>This gateway holds no attribute business rules: every operation resolves
 * the deprecated CoreLib {@link PdcAttributeApi} mirror service, which is
 * implemented solely by EmakiAttribute's compatibility adapter and delegates to
 * the canonical {@code emaki.jiuwu.craft.attribute.api.PdcAttributeApi}. It
 * remains lossy because {@link PdcAttributePayloadSnapshot} cannot represent
 * conditions, schema version or update timestamp.
 *
 * @deprecated Use {@code emaki.jiuwu.craft.attribute.api.PdcAttributeApi} with
 *             the full {@code PdcAttributePayload}. Retained for one
 *             synchronized release window.
 */
@Deprecated(forRemoval = true)
public final class PdcAttributeGateway {

    private final Plugin owner;
    private volatile String registeredSourceId;

    public PdcAttributeGateway(Plugin owner) {
        this.owner = owner;
    }

    public boolean available() {
        return resolveApiInstance() != null;
    }

    public void syncRegistration(String sourceId) {
        String nextSourceId = Texts.normalizeId(sourceId);
        String previousSourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(previousSourceId) && !previousSourceId.equals(nextSourceId)) {
            unregisterSource(previousSourceId);
        }
        if (Texts.isNotBlank(nextSourceId)) {
            registerSource(nextSourceId);
        }
        registeredSourceId = Texts.isNotBlank(nextSourceId) ? nextSourceId : null;
    }

    public void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            unregisterSource(sourceId);
        }
        registeredSourceId = null;
    }

    public boolean registerSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return false;
        }
        PdcAttributeApi api = resolveApiInstance();
        return api != null && api.registerSource(normalized);
    }

    public void unregisterSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return;
        }
        PdcAttributeApi api = resolveApiInstance();
        if (api != null) {
            api.unregisterSource(normalized);
        }
    }

    public boolean isRegisteredSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return false;
        }
        PdcAttributeApi api = resolveApiInstance();
        return api != null && api.isRegisteredSource(normalized);
    }

    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized) || attributes == null || attributes.isEmpty()) {
            return false;
        }
        PdcAttributeApi api = resolveApiInstance();
        if (api == null || !ensureRegisteredSource(normalized)) {
            return false;
        }
        Map<String, Double> attributeCopy = new LinkedHashMap<>(attributes);
        Map<String, String> metaCopy = meta == null ? Map.of() : new LinkedHashMap<>(meta);
        return api.write(itemStack, normalized, attributeCopy, metaCopy);
    }

    public boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        PdcAttributeApi api = resolveApiInstance();
        if (api == null) {
            return false;
        }
        Map<String, PdcAttributePayloadSnapshot> payloads = api.readAllSnapshots(itemStack);
        if (payloads == null || !payloads.containsKey(normalized)) {
            return false;
        }
        return api.clear(itemStack, normalized);
    }

    public Map<String, PdcAttributePayloadSnapshot> readAll(ItemStack itemStack) {
        if (itemStack == null) {
            return Map.of();
        }
        PdcAttributeApi api = resolveApiInstance();
        if (api == null) {
            return Map.of();
        }
        Map<String, PdcAttributePayloadSnapshot> payloads = api.readAllSnapshots(itemStack);
        return payloads == null || payloads.isEmpty() ? Map.of() : Map.copyOf(payloads);
    }

    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        if (fromItem == null || toItem == null) {
            return;
        }
        PdcAttributeApi api = resolveApiInstance();
        if (api == null) {
            return;
        }
        api.copy(fromItem, toItem, normalizeIds(excludedSourceIds));
    }

    private PdcAttributeApi resolveApiInstance() {
        // Resolved per call: a reloaded or disabled EmakiAttribute must never be
        // reached through a stale provider instance.
        RegisteredServiceProvider<PdcAttributeApi> provider = Bukkit.getServicesManager()
                .getRegistration(PdcAttributeApi.class);
        if (provider == null) {
            return null;
        }
        Plugin providerPlugin = provider.getPlugin();
        if (providerPlugin == null || !providerPlugin.isEnabled()) {
            return null;
        }
        return provider.getProvider();
    }

    private boolean ensureRegisteredSource(String sourceId) {
        if (Texts.isBlank(sourceId)) {
            return false;
        }
        if (isRegisteredSource(sourceId)) {
            return true;
        }
        return registerSource(sourceId);
    }

    private Set<String> normalizeIds(Set<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String sourceId : sourceIds) {
            String value = Texts.normalizeId(sourceId);
            if (Texts.isNotBlank(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }
}
