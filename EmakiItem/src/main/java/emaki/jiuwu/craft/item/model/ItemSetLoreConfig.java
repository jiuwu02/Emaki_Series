package emaki.jiuwu.craft.item.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record ItemSetLoreConfig(String header,
        String equippedFormat,
        String missingFormat,
        String activeThresholdFormat,
        String inactiveThresholdFormat,
        String separator) {

    public ItemSetLoreConfig {
        header = Texts.isBlank(header) ? "<dark_gray>—— {set_name} <gray>({active}/{total})</gray> ——</dark_gray>" : header;
        equippedFormat = Texts.isBlank(equippedFormat) ? "<green>✔ {piece}</green>" : equippedFormat;
        missingFormat = Texts.isBlank(missingFormat) ? "<gray>✘ {piece}</gray>" : missingFormat;
        activeThresholdFormat = Texts.isBlank(activeThresholdFormat) ? "<green>{line}</green>" : activeThresholdFormat;
        inactiveThresholdFormat = Texts.isBlank(inactiveThresholdFormat) ? "<dark_gray>{line}</dark_gray>" : inactiveThresholdFormat;
        separator = separator == null ? "" : separator;
    }

    public static ItemSetLoreConfig defaults() {
        return new ItemSetLoreConfig("", "", "", "", "", "");
    }
}
