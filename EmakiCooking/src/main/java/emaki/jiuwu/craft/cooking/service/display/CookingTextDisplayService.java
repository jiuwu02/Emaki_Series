package emaki.jiuwu.craft.cooking.service.display;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;

/**
 * 工位文本展示的薄适配层。
 *
 * <p>实体的创建、刷新与回收全部由 CoreLib 的 {@link TextDisplayService} 负责；
 * 这里只把工位身份翻译成 {@code DisplayKey}，让各 RuntimeService 继续按工位语义调用。
 */
public final class CookingTextDisplayService {

    private final TextDisplayService delegate;

    public CookingTextDisplayService(TextDisplayService delegate) {
        this.delegate = delegate;
    }

    public void upsert(CookingTextDisplaySpec spec) {
        if (spec == null) {
            return;
        }
        delegate.upsert(spec.toCoreSpec());
    }

    public void remove(StationType stationType, StationCoordinates coordinates, String displayKey) {
        if (stationType == null || coordinates == null || displayKey == null) {
            return;
        }
        delegate.remove(CookingDisplayKeys.of(stationType, coordinates, displayKey));
    }

    public void removeStation(StationType stationType, StationCoordinates coordinates) {
        if (stationType == null || coordinates == null) {
            return;
        }
        delegate.removeGroup(CookingDisplayKeys.NAMESPACE, CookingDisplayKeys.group(stationType, coordinates));
    }

    public void removeStationType(StationType stationType) {
        if (stationType == null) {
            return;
        }
        delegate.removeGroupPrefix(CookingDisplayKeys.NAMESPACE, CookingDisplayKeys.typePrefix(stationType));
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public String backendName() {
        return delegate.backendName();
    }
}
