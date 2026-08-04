package emaki.jiuwu.craft.item.service;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

/**
 * Exposes EmakiItem definitions as an item source.
 *
 * <p>This class now owns all three halves of that claim: the {@linkplain #KIND kind}, the shorthand
 * prefixes and the resolution behaviour. Previously the resolver lived here while CoreLib hard-coded
 * the {@code EMAKIITEM} enum constant, the {@code emakiitem-} prefix short circuit and the identifier
 * normalisation &mdash; CoreLib recognised an item source it did not implement. Whoever supplies the
 * provider declares the kind and the prefixes; that rule is what this class exists to satisfy.
 */
public final class EmakiItemSourceResolver implements ItemSourceProvider {

    /**
     * EmakiItem's own item source kind.
     *
     * <p>Its namespace is {@code emakiitem} rather than {@code emaki}, because the {@code emaki}
     * namespace belongs to the eight kinds CoreLib implements itself.
     *
     * <p>The id repeats the namespace on purpose. Existing server config compares a bare source-type
     * name against {@code kind().id()} &mdash; EmakiStrengthen's {@code match_rule.source_types} is the
     * known case &mdash; and that name used to be the old enum constant lower-cased, i.e.
     * {@code emakiitem}. An id of {@code item} would have read better but would silently stop matching
     * every rule that lists {@code emakiitem}.
     */
    public static final ItemSourceKind KIND = ItemSourceKind.of("emakiitem:emakiitem");

    private final EmakiItemApi.Bridge api;

    public EmakiItemSourceResolver(EmakiItemApi.Bridge api) {
        this.api = api;
    }

    @Override
    public ItemSourceKind kind() {
        return KIND;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("emakiitem-", "ei-");
    }

    @Override
    public String canonicalShorthandPrefix() {
        return "emakiitem-";
    }

    @Override
    public String normalizeIdentifier(String identifier) {
        // Definition ids are plain lowercase tokens; ItemSourceRef already trims, lower-cases and
        // replaces spaces, so nothing further is needed here.
        return identifier;
    }

    @Override
    public String providerPluginName() {
        return "EmakiItem";
    }

    @Override
    public boolean supports(ItemSourceRef ref) {
        return ref != null && KIND.equals(ref.kind());
    }

    @Override
    public ItemSourceProbeResult probe(ItemSourceRef ref) {
        String providerId = KIND.key();
        if (!supports(ref) || Texts.isBlank(ref.identifier())) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, ref, providerId,
                    "The EmakiItem source kind and identifier are required.");
        }
        try {
            if (!api.status().usable()) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_NOT_READY, ref, providerId,
                        "EmakiItem is installed but its runtime is not ready.");
            }
            return api.catalog().exists(ref.identifier())
                    ? ItemSourceProbeResult.ready(ref, providerId)
                    : ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                            "EmakiItem does not contain the requested item definition.");
        } catch (LinkageError exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref, providerId, detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref, providerId, detail(exception));
        }
    }

    @Override
    public ItemSourceRef identify(ItemStack itemStack) {
        if (!readyForAccess()) {
            return null;
        }
        String definitionId = api.catalog().identify(itemStack).orElse(null);
        return Texts.isBlank(definitionId) ? null : ItemSourceRef.orNull(KIND, definitionId);
    }

    @Override
    public ItemStack create(ItemSourceRef ref, int amount) {
        if (!supports(ref) || !readyForAccess()) {
            return null;
        }
        return api.operations().create(ref.identifier(), amount).orElse(null);
    }

    @Override
    public String displayName(ItemSourceRef ref) {
        if (!supports(ref) || !readyForAccess()) {
            return null;
        }
        return api.catalog().displayName(ref.identifier()).orElse(null);
    }

    private boolean readyForAccess() {
        try {
            return api.status().usable();
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private String detail(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable == null ? "Unknown EmakiItem resolution failure" : throwable.getClass().getSimpleName()
                : message;
    }
}
