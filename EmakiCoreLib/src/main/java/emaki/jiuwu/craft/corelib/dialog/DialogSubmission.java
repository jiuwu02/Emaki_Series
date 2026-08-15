package emaki.jiuwu.craft.corelib.dialog;

import emaki.jiuwu.craft.corelib.api.text.Texts;

import io.papermc.paper.dialog.DialogResponseView;

public final class DialogSubmission {

    private final DialogResponseView response;
    private final String buttonId;

    DialogSubmission(DialogResponseView response, String buttonId) {
        this.response = response;
        this.buttonId = buttonId == null ? "" : buttonId;
    }

    public String buttonId() {
        return buttonId;
    }

    public String text(String key) {
        return text(key, "");
    }

    public String text(String key, String fallback) {
        if (response == null || Texts.isBlank(key)) {
            return fallback;
        }
        try {
            String value = response.getText(key);
            return value == null ? fallback : value;
        } catch (RuntimeException _) {
            return fallback;
        }
    }

    public boolean bool(String key, boolean fallback) {
        if (response == null || Texts.isBlank(key)) {
            return fallback;
        }
        try {
            Boolean value = response.getBoolean(key);
            return value == null ? fallback : value;
        } catch (RuntimeException _) {
            return fallback;
        }
    }

    public float number(String key, float fallback) {
        if (response == null || Texts.isBlank(key)) {
            return fallback;
        }
        try {
            Float value = response.getFloat(key);
            return value == null ? fallback : value;
        } catch (RuntimeException _) {
            return fallback;
        }
    }
}
