package emaki.jiuwu.craft.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapHooks;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.runtime.AbstractLifecycleCoordinator;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.UnlockCostConfig;
import emaki.jiuwu.craft.storage.gui.StorageAmountFormatter;
import emaki.jiuwu.craft.storage.gui.StorageGuiService;
import emaki.jiuwu.craft.storage.gui.StorageLayoutResolver;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.SortMode;
import emaki.jiuwu.craft.storage.service.PlayerStorageStore;
import emaki.jiuwu.craft.storage.service.StorageCapacityService;
import emaki.jiuwu.craft.storage.service.StorageOverflowService;
import emaki.jiuwu.craft.storage.service.StorageSearchService;
import emaki.jiuwu.craft.storage.service.StorageSortService;
import emaki.jiuwu.craft.storage.service.StorageTextIndexer;
import emaki.jiuwu.craft.storage.service.StorageTransactionService;
import emaki.jiuwu.craft.storage.service.StorageUnlockService;
import emaki.jiuwu.craft.storage.config.AutoPickupConfig;
import emaki.jiuwu.craft.storage.config.InputModeConfig;

final class StorageLifecycleCoordinator
        extends AbstractLifecycleCoordinator<EmakiStoragePlugin, StorageRuntimeComponents> {

    private static final String DEFAULT_PREFIX =
            "<gray>[ <gradient:#4DA6FF:#FFD166>EmakiStorage</gradient> ]</gray>";
    private static final List<String> VERSIONED_FILES =
            List.of("config.yml", "lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> STATIC_FILES = List.of("gui/storage_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("unlock_costs.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data", "logs", "corrupt");

    @Override
    public StorageRuntimeComponents initialize(EmakiStoragePlugin plugin) {
        EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);

        YamlConfigLoader<AppConfig> appConfigLoader = new YamlConfigLoader<>(
                plugin, "config.yml", AppConfig::defaults,
                section -> parseAppConfig(section, plugin));
        appConfigLoader.load();

        LanguageLoader languageLoader = new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
        MessageService messageService = new MessageService(plugin, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();

        BootstrapService bootstrapService = new BootstrapService(
                plugin, messageService, VERSIONED_FILES, STATIC_FILES,
                DEFAULT_DATA_FILES, EXTRA_DIRECTORIES,
                new BootstrapHooks() {
                    @Override
                    public boolean shouldInstallDefaultData() {
                        AppConfig current = appConfigLoader.current();
                        return current == null || current.releaseDefaultData();
                    }
                });

        AppConfig config = appConfigLoader.current();
        var executionDispatcher = coreLib.executionDispatcher();
        var threadOwnership = coreLib.threadOwnership();

        GuiTemplateLoader guiTemplateLoader = new GuiTemplateLoader(plugin);
        GuiService guiService = new GuiService(plugin, executionDispatcher,
                coreLib.asyncTaskScheduler(), coreLib.performanceMonitor(), coreLib.guiBackend());
        ChatInputService chatInputService = new ChatInputService(plugin, executionDispatcher);

        StorageTextIndexer textIndexer = new StorageTextIndexer(
                coreLib.itemSourceService(),
                coreLib.vanillaTranslationService());
        var fileScope = coreLib.asyncFileScope(plugin);
        StorageOperationLog operationLog = new StorageOperationLog(
                plugin.dataPath("logs"), plugin.getLogger(), fileScope, config.logging());
        PlayerStorageStore dataStore = new PlayerStorageStore(plugin.getLogger(), fileScope,
                plugin.dataPath("data"), plugin.dataPath("corrupt"), textIndexer);
        dataStore.configure(config.behavior().defaultSort(), config.capacity().warnEntryCount(),
                config.autoPickup().defaultEnabled());

        StorageCapacityService capacityService = new StorageCapacityService(config);
        StorageTransactionService transactionService = new StorageTransactionService(
                coreLib.itemSourceService(), capacityService, textIndexer, operationLog, config,
                plugin::debugLogger);
        StorageSearchService searchService = new StorageSearchService();
        StorageSortService sortService = new StorageSortService();
        StorageOverflowService overflowService =
                new StorageOverflowService(operationLog, textIndexer, capacityService, config);
        UnlockCostConfig costConfig = loadCostConfig(plugin, config, false);
        StorageUnlockService unlockService = new StorageUnlockService(coreLib.economyManager(),
                coreLib.itemSourceService(), capacityService, operationLog, config, costConfig);

        StorageLayoutResolver layoutResolver = new StorageLayoutResolver(plugin.getLogger());
        StorageAmountFormatter amountFormatter = new StorageAmountFormatter(config.display());
        StorageGuiService storageGuiService = new StorageGuiService(guiService, capacityService,
                searchService, overflowService, amountFormatter, messageService, config);

        return new StorageRuntimeComponents(appConfigLoader, languageLoader, messageService,
                bootstrapService, executionDispatcher, threadOwnership, guiService, guiTemplateLoader,
                chatInputService, textIndexer, dataStore, operationLog, capacityService,
                transactionService, searchService, sortService, overflowService, unlockService,
                layoutResolver, amountFormatter, storageGuiService);
    }

    public int reload(EmakiStoragePlugin plugin) {

        ConfigCommitGate.Result gate = ConfigCommitGate.commit(
                plugin.messageService(),
                "storage",
                plugin.appConfigLoader()::current,
                () -> {
                    plugin.languageLoader().load();
                    AppConfig candidate = plugin.appConfigLoader().load();
                    plugin.guiTemplateLoader().load();
                    return candidate;
                },
                plugin.appConfigLoader()::overrideCurrent);
        if (gate.rejected()) {

            return plugin.guiTemplateLoader().all().size();
        }
        plugin.languageLoader().setLanguage(plugin.appConfig().language());

        AppConfig config = plugin.appConfig();
        UnlockCostConfig costConfig = loadCostConfig(plugin, config, true);

        plugin.capacityService().reconfigure(config);
        plugin.transactionService().reconfigure(config);
        plugin.overflowService().reconfigure(config);
        plugin.unlockService().reconfigure(config, costConfig);
        plugin.amountFormatter().reconfigure(config.display());
        plugin.operationLog().reconfigure(config.logging());
        plugin.dataStore().configure(config.behavior().defaultSort(), config.capacity().warnEntryCount(),
                config.autoPickup().defaultEnabled());

        int templates = plugin.guiTemplateLoader().all().size();
        StorageLayoutResolver.Layout layout =
                plugin.layoutResolver().resolve(plugin.guiTemplateLoader(), config.gui().storageRows());
        plugin.storageGuiService().reconfigure(config, layout);
        for (String issue : plugin.guiTemplateLoader().issues()) {
            plugin.getLogger().warning("[storage] " + issue);
        }
        plugin.operationLog().purgeExpired();
        return templates;
    }

    public void shutdown(EmakiStoragePlugin plugin) {
        if (plugin.stageRegistrar() != null) {
            plugin.stageRegistrar().unregister();
        }
    }

    private AppConfig parseAppConfig(YamlSection configuration, EmakiStoragePlugin plugin) {
        if (configuration == null || configuration.getKeys(false).isEmpty()) {
            return AppConfig.defaults();
        }
        Consumer<String> issues =
                issue -> plugin.getLogger().warning("[config] " + issue);
        return new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("version", AppConfig.CURRENT_VERSION),
                bool(configuration, "release_default_data", true),
                bool(configuration, "op_bypass", false),
                parseGui(configuration.getSection("gui")),
                parseCapacity(configuration.getSection("capacity")),
                parseUnlock(configuration.getSection("unlock")),
                parseDisplay(configuration.getSection("display")),
                parseBehavior(configuration.getSection("behavior"), issues),
                AutoPickupConfig.fromConfig(
                        configuration.getSection("auto_pickup")),
                parseSearch(configuration.getSection("search"), issues),
                parsePersistence(configuration.getSection("persistence")),
                parseLogging(configuration.getSection("logging")),
                bool(configuration, "debug", false));
    }

    private AppConfig.GuiConfig parseGui(YamlSection section) {
        AppConfig.GuiConfig defaults = AppConfig.GuiConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig.GuiConfig(
                integer(section, "storage_rows", defaults.storageRows()),
                AppConfig.DepositFeedback.fromId(section.getString("deposit_feedback", null),
                        defaults.depositFeedback()),
                bool(section, "require_empty_cursor_for_withdraw",
                        defaults.requireEmptyCursorForWithdraw()));
    }

    private AppConfig.CapacityConfig parseCapacity(YamlSection section) {
        AppConfig.CapacityConfig defaults = AppConfig.CapacityConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig.CapacityConfig(
                Math.max(0, integer(section, "base_slots", defaults.baseSlots())),
                Math.max(0, integer(section, "max_slots", defaults.maxSlots())),
                Math.max(0, integer(section, "warn_entry_count", defaults.warnEntryCount())),
                Math.max(0L, longValue(section, "default_stack_limit", defaults.defaultStackLimit())));
    }

    private AppConfig.UnlockConfig parseUnlock(YamlSection section) {
        AppConfig.UnlockConfig defaults = AppConfig.UnlockConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig.UnlockConfig(
                AppConfig.OverflowPolicy.fromId(section.getString("overflow_policy", null),
                        defaults.overflowPolicy()),
                bool(section, "purchase_enabled", defaults.purchaseEnabled()),
                section.getString("cost_file", defaults.costFile()));
    }

    private AppConfig.DisplayConfig parseDisplay(YamlSection section) {
        AppConfig.DisplayConfig defaults = AppConfig.DisplayConfig.defaults();
        if (section == null) {
            return defaults;
        }
        List<String> units = section.getStringList("compact_units");
        return new AppConfig.DisplayConfig(
                AppConfig.AmountMode.fromId(section.getString("amount_mode", null), defaults.amountMode()),
                integer(section, "percent_scale", defaults.percentScale()),
                units == null || units.isEmpty() ? defaults.compactUnits() : units,
                Math.max(0, integer(section, "compact_decimals", defaults.compactDecimals())),
                bool(section, "show_exact_amount", defaults.showExactAmount()),
                AppConfig.LorePosition.fromId(section.getString("lore_position", null),
                        defaults.lorePosition()));
    }

    private AppConfig.BehaviorConfig parseBehavior(YamlSection section,
            Consumer<String> issues) {
        AppConfig.BehaviorConfig defaults = AppConfig.BehaviorConfig.defaults();
        if (section == null) {
            return defaults;
        }
        YamlSection amounts = section.getSection("withdraw_amounts");
        AppConfig.WithdrawAmounts withdrawAmounts = amounts == null
                ? defaults.withdrawAmounts()
                : new AppConfig.WithdrawAmounts(
                        Math.max(0L, longValue(amounts, "left", defaults.withdrawAmounts().left())),
                        Math.max(0L, longValue(amounts, "right", defaults.withdrawAmounts().right())),
                        Math.max(0L, longValue(amounts, "shift_left", defaults.withdrawAmounts().shiftLeft())),
                        Math.max(0L, longValue(amounts, "shift_right", defaults.withdrawAmounts().shiftRight())));

        YamlSection filter = section.getSection("deposit_filter");
        YamlSection filterMatcher = filter == null ? null : filter.getSection("matcher");
        AppConfig.DepositFilter depositFilter = filter == null
                ? defaults.depositFilter()
                : new AppConfig.DepositFilter(
                        AppConfig.FilterMode.fromId(filter.getString("mode", null),
                                defaults.depositFilter().mode()),
                        filter.getStringList("entries") == null
                                ? List.of() : filter.getStringList("entries"),
                        filterMatcher == null ? null : Matcher.fromConfig(filterMatcher));

        YamlSection withdrawPrompt = section.getSection("withdraw_prompt");
        boolean withdrawPromptEnabled = withdrawPrompt == null
                ? defaults.withdrawPromptEnabled()
                : bool(withdrawPrompt, "enabled", defaults.withdrawPromptEnabled());

        return new AppConfig.BehaviorConfig(
                AppConfig.WithdrawOverflow.fromId(section.getString("overflow_on_withdraw", null),
                        defaults.overflowOnWithdraw()),
                withdrawAmounts,
                withdrawPromptEnabled,
                InputModeConfig.fromConfig(withdrawPrompt,
                        "emakistorage/withdraw_amount", AppConfig.WITHDRAW_INPUT_KEY, issues),
                depositFilter,
                bool(section, "allow_unique_items", defaults.allowUniqueItems()),
                bool(section, "multi_slot_stacking", defaults.multiSlotStacking()),
                (int) longValue(section, "batch_max_ops", defaults.batchMaxOps()),
                SortMode.fromId(section.getString("default_sort", null), defaults.defaultSort()),
                bool(section, "player_sort_enabled", defaults.playerSortEnabled()));
    }

    private AppConfig.SearchConfig parseSearch(YamlSection section,
            Consumer<String> issues) {
        AppConfig.SearchConfig defaults = AppConfig.SearchConfig.defaults();
        if (section == null) {
            return defaults;
        }
        YamlSection operators = section.getSection("operators");
        SearchQuery.Operators parsedOperators = operators == null
                ? defaults.operators()
                : new SearchQuery.Operators(
                        operators.getString("name", defaults.operators().name()),
                        operators.getString("lore", defaults.operators().lore()),
                        operators.getString("id", defaults.operators().id()),
                        operators.getString("exclude", defaults.operators().exclude()));
        YamlSection input = section.getSection("input");
        long timeout = input == null
                ? defaults.inputTimeoutSeconds()
                : longValue(input, "timeout", defaults.inputTimeoutSeconds());
        List<String> cancelKeywords = input == null ? null : input.getStringList("cancel_keywords");
        return new AppConfig.SearchConfig(
                bool(section, "enabled", defaults.enabled()),
                parsedOperators,
                InputModeConfig.fromConfig(input,
                        "emakistorage/search", AppConfig.SEARCH_INPUT_KEY, issues),
                Math.max(0L, timeout),
                cancelKeywords == null || cancelKeywords.isEmpty()
                        ? defaults.cancelKeywords() : cancelKeywords);
    }

    private AppConfig.PersistenceConfig parsePersistence(YamlSection section) {
        AppConfig.PersistenceConfig defaults = AppConfig.PersistenceConfig.defaults();
        if (section == null) {
            return defaults;
        }
        return new AppConfig.PersistenceConfig(
                Math.max(0L, longValue(section, "autosave_interval", defaults.autosaveIntervalSeconds())),
                Math.max(1L, longValue(section, "drain_timeout", defaults.drainTimeoutSeconds())));
    }

    private AppConfig.LoggingConfig parseLogging(YamlSection section) {
        AppConfig.LoggingConfig defaults = AppConfig.LoggingConfig.defaults();
        if (section == null) {
            return defaults;
        }
        List<String> sources = section.getStringList("sources");
        return new AppConfig.LoggingConfig(
                bool(section, "enabled", defaults.enabled()),
                Math.max(0, integer(section, "retention_days", defaults.retentionDays())),
                sources == null ? List.of() : sources);
    }

    private UnlockCostConfig loadCostConfig(EmakiStoragePlugin plugin, AppConfig config,
            boolean reportMissingFile) {
        String fileName = config.unlock().costFile();
        YamlSection section = YamlFiles.load(plugin.dataPath(fileName).toFile());
        if (section == null || section.isEmpty()) {
            if (reportMissingFile) {
                plugin.getLogger().warning("[storage] " + fileName
                        + " is missing or empty; paid expansion is disabled until it is provided.");
            }
            return UnlockCostConfig.empty();
        }
        List<UnlockCostConfig.Tier> tiers = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("tiers")) {
            UnlockCostConfig.Tier tier = parseTier(plugin, raw);
            if (tier != null) {
                tiers.add(tier);
            }
        }
        UnlockCostConfig.Fallback fallback = parseFallback(plugin, section.getSection("fallback"));
        YamlSection batchSection = section.getSection("batch");
        UnlockCostConfig.Batch batch = UnlockCostConfig.Batch.defaults();
        if (batchSection != null) {
            List<Integer> options = new ArrayList<>();
            for (Object option : batchSection.getList("options", List.of())) {
                if (option instanceof Number number && number.intValue() > 0) {
                    options.add(number.intValue());
                }
            }
            batch = new UnlockCostConfig.Batch(
                    bool(batchSection, "enabled", true),
                    options.isEmpty() ? UnlockCostConfig.Batch.defaults().options() : options);
        }
        return new UnlockCostConfig(tiers, fallback, batch);
    }

    private UnlockCostConfig.Tier parseTier(EmakiStoragePlugin plugin, Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        String range = ConfigNodes.string(raw, "count_range", null);
        int[] bounds = parseRange(range);
        if (bounds == null) {
            plugin.getLogger().warning("[storage] Skipping unlock tier with invalid count_range: " + range);
            return null;
        }
        UnlockCostConfig.CurrencyCost currency = parseCurrency(ConfigNodes.get(raw, "currency"));
        UnlockCostConfig.ItemCost item = parseItem(ConfigNodes.get(raw, "item"));
        if (currency == null && item == null) {
            plugin.getLogger().warning("[storage] Skipping unlock tier " + range + " with no price.");
            return null;
        }
        return new UnlockCostConfig.Tier(bounds[0], bounds[1], currency, item);
    }

    private UnlockCostConfig.Fallback parseFallback(EmakiStoragePlugin plugin, YamlSection section) {
        if (section == null) {
            return null;
        }
        UnlockCostConfig.CurrencyCost currency = parseCurrency(section.get("currency"));
        UnlockCostConfig.ItemCost item = parseItem(section.get("item"));
        if (currency == null && item == null) {
            return null;
        }
        Double maxAmount = section.getDouble("max_amount", null);
        if (maxAmount == null || maxAmount <= 0.0D) {

            plugin.getLogger().warning("[storage] unlock_costs.yml fallback requires a positive"
                    + " max_amount guard rail; paid expansion beyond the defined tiers is disabled.");
            return null;
        }
        return new UnlockCostConfig.Fallback(currency, item, maxAmount);
    }

    private UnlockCostConfig.CurrencyCost parseCurrency(Object raw) {
        if (raw == null) {
            return null;
        }
        UnlockCostConfig.CurrencyType type = UnlockCostConfig.CurrencyType.fromId(
                ConfigNodes.string(raw, "type", "vault"), UnlockCostConfig.CurrencyType.VAULT);
        String currencyId = ConfigNodes.string(raw, "currency", "");
        Object amount = ConfigNodes.get(raw, "amount");
        if (amount instanceof Number number) {
            return new UnlockCostConfig.CurrencyCost(type, currencyId, number.doubleValue(), null);
        }
        if (amount instanceof String expression && !expression.isBlank()) {
            return new UnlockCostConfig.CurrencyCost(type, currencyId, 0.0D,
                    expression.replace("%count%", "count"));
        }
        return null;
    }

    private UnlockCostConfig.ItemCost parseItem(Object raw) {
        if (raw == null) {
            return null;
        }
        String source = ConfigNodes.string(raw, "source", null);
        if (source == null || source.isBlank()) {
            return null;
        }
        Object amount = ConfigNodes.get(raw, "amount");
        int count = amount instanceof Number number ? number.intValue() : 1;
        return count <= 0 ? null : new UnlockCostConfig.ItemCost(source.trim(), count);
    }

    private int[] parseRange(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String normalized = range.trim().toLowerCase(Locale.ROOT);
        int dash = normalized.indexOf('-');
        try {
            if (dash < 0) {
                int single = Integer.parseInt(normalized);
                return single <= 0 ? null : new int[] { single, single };
            }
            int min = Integer.parseInt(normalized.substring(0, dash).trim());
            int max = Integer.parseInt(normalized.substring(dash + 1).trim());
            if (min <= 0 || max < min) {
                return null;
            }
            return new int[] { min, max };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean bool(YamlSection section, String path, boolean fallback) {
        Boolean value = section.getBoolean(path, fallback);
        return value == null ? fallback : value;
    }

    private int integer(YamlSection section, String path, int fallback) {
        Integer value = section.getInt(path, fallback);
        return value == null ? fallback : value;
    }

    private long longValue(YamlSection section, String path, long fallback) {
        Object raw = section.get(path);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

}
