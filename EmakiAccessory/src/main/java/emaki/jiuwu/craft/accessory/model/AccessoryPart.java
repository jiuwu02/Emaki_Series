package emaki.jiuwu.craft.accessory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record AccessoryPart(String partId, int count, String displayName, String icon) {

    public static final Pattern PART_ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private static final Pattern INDEX_SUFFIX_PATTERN = Pattern.compile(".*_\\d+$");

    public AccessoryPart {
        partId = Texts.normalizeId(partId);
        displayName = Texts.toStringSafe(displayName);
        icon = Texts.toStringSafe(icon);
        count = Math.max(1, count);
    }

    public static boolean isLegalPartId(String candidate) {
        String normalized = Texts.normalizeId(candidate);
        return Texts.isNotBlank(normalized)
                && PART_ID_PATTERN.matcher(normalized).matches()
                && !INDEX_SUFFIX_PATTERN.matcher(normalized).matches();
    }

    public static String slotInstanceId(String partId, int index) {
        return Texts.normalizeId(partId) + "_" + index;
    }

    public static String partIdOf(String slotInstanceId) {
        String normalized = Texts.normalizeId(slotInstanceId);
        int separator = normalized.lastIndexOf('_');
        if (separator <= 0 || separator == normalized.length() - 1) {
            return normalized;
        }
        String suffix = normalized.substring(separator + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return normalized;
            }
        }
        return normalized.substring(0, separator);
    }

    public List<String> slotInstanceIds() {
        List<String> ids = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            ids.add(slotInstanceId(partId, index));
        }
        return List.copyOf(ids);
    }
}
