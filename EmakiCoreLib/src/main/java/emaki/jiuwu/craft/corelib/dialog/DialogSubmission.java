package emaki.jiuwu.craft.corelib.dialog;

import emaki.jiuwu.craft.corelib.text.Texts;

import io.papermc.paper.dialog.DialogResponseView;

/**
 * 玩家提交对话框后的取值视图。
 *
 * <p>按 input 的 {@code key} 读取，键不存在或类型不符时返回给定的兜底值，
 * 因此调用方不必自己处理 {@code null}。
 */
public final class DialogSubmission {

    private final DialogResponseView response;
    private final String buttonId;

    DialogSubmission(DialogResponseView response, String buttonId) {
        this.response = response;
        this.buttonId = buttonId == null ? "" : buttonId;
    }

    /** {@return 触发提交的按钮标识；未指定时为空串} */
    public String buttonId() {
        return buttonId;
    }

    /**
     * 读取文本输入。
     *
     * @param key 输入项的 key
     * @return 输入值；不存在时返回空串
     */
    public String text(String key) {
        return text(key, "");
    }

    /**
     * 读取文本输入。
     *
     * @param key      输入项的 key
     * @param fallback 缺失时的兜底值
     * @return 输入值或兜底值
     */
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

    /**
     * 读取布尔输入。
     *
     * @param key      输入项的 key
     * @param fallback 缺失时的兜底值
     * @return 输入值或兜底值
     */
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

    /**
     * 读取数值输入。
     *
     * @param key      输入项的 key
     * @param fallback 缺失时的兜底值
     * @return 输入值或兜底值
     */
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
