package emaki.jiuwu.craft.accessory.model;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record AccessoryPage(String pageId,
        String displayName,
        int order,
        String guiTemplate,
        String permission,
        List<String> parts) {

    public static final String DEFAULT_TEMPLATE = "accessory_gui";

    public AccessoryPage {
        pageId = Texts.normalizeId(pageId);
        displayName = Texts.toStringSafe(displayName);
        guiTemplate = Texts.isBlank(guiTemplate) ? DEFAULT_TEMPLATE : Texts.normalizeId(guiTemplate);
        permission = Texts.trim(permission);
        parts = normalizeParts(parts);
    }

    public boolean open() {
        return Texts.isBlank(permission);
    }

    public String label() {
        return Texts.isBlank(displayName) ? pageId : displayName;
    }

    private static List<String> normalizeParts(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(raw.size());
        for (String candidate : raw) {
            String partId = Texts.normalizeId(candidate);
            if (Texts.isNotBlank(partId) && !normalized.contains(partId)) {
                normalized.add(partId);
            }
        }
        return List.copyOf(normalized);
    }
}
