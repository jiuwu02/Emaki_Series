package emaki.jiuwu.craft.storage;

import java.util.Map;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.gui.StorageAmountFormatter;
import emaki.jiuwu.craft.storage.gui.StorageGuiService;
import emaki.jiuwu.craft.storage.gui.StorageLayoutResolver;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.service.PlayerStorageStore;
import emaki.jiuwu.craft.storage.service.StorageCapacityService;
import emaki.jiuwu.craft.storage.service.StorageOverflowService;
import emaki.jiuwu.craft.storage.service.StorageSearchService;
import emaki.jiuwu.craft.storage.service.StorageSortService;
import emaki.jiuwu.craft.storage.service.StorageTextIndexer;
import emaki.jiuwu.craft.storage.service.StorageTransactionService;
import emaki.jiuwu.craft.storage.service.StorageUnlockService;

/**
 * Everything the plugin assembles at enable time.
 *
 * <p>Exposed through {@link RuntimeComponents} so the plugin's service registry stays the single
 * lookup point, matching how the other Emaki runtime modules are wired.
 */
record StorageRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        ExecutionDispatcher executionDispatcher,
        ThreadOwnership threadOwnership,
        GuiService guiService,
        GuiTemplateLoader guiTemplateLoader,
        ChatInputService chatInputService,
        StorageTextIndexer textIndexer,
        PlayerStorageStore dataStore,
        StorageOperationLog operationLog,
        StorageCapacityService capacityService,
        StorageTransactionService transactionService,
        StorageSearchService searchService,
        StorageSortService sortService,
        StorageOverflowService overflowService,
        StorageUnlockService unlockService,
        StorageLayoutResolver layoutResolver,
        StorageAmountFormatter amountFormatter,
        StorageGuiService storageGuiService) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(ExecutionDispatcher.class, executionDispatcher),
                RuntimeComponents.component(ThreadOwnership.class, threadOwnership),
                RuntimeComponents.component(GuiService.class, guiService),
                RuntimeComponents.component(GuiTemplateLoader.class, guiTemplateLoader),
                RuntimeComponents.component(ChatInputService.class, chatInputService),
                RuntimeComponents.component(StorageTextIndexer.class, textIndexer),
                RuntimeComponents.component(PlayerStorageStore.class, dataStore),
                RuntimeComponents.component(StorageOperationLog.class, operationLog),
                RuntimeComponents.component(StorageCapacityService.class, capacityService),
                RuntimeComponents.component(StorageTransactionService.class, transactionService),
                RuntimeComponents.component(StorageSearchService.class, searchService),
                RuntimeComponents.component(StorageSortService.class, sortService),
                RuntimeComponents.component(StorageOverflowService.class, overflowService),
                RuntimeComponents.component(StorageUnlockService.class, unlockService),
                RuntimeComponents.component(StorageLayoutResolver.class, layoutResolver),
                RuntimeComponents.component(StorageAmountFormatter.class, amountFormatter),
                RuntimeComponents.component(StorageGuiService.class, storageGuiService)
        );
    }
}
