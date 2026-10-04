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

public final class EmakiItemSourceResolver implements ItemSourceProvider {

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
                    "EmakiItem 来源类型与标识符为必填项。");
        }
        try {
            if (!api.status().usable()) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_NOT_READY, ref, providerId,
                        "EmakiItem 已安装但运行时尚未就绪。");
            }
            return api.catalog().exists(ref.identifier())
                    ? ItemSourceProbeResult.ready(ref, providerId)
                    : ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                            "EmakiItem 不包含所请求的物品定义。");
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
                ? throwable == null ? "未知的 EmakiItem 解析失败" : throwable.getClass().getSimpleName()
                : message;
    }
}
