package emaki.jiuwu.craft.forge.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class EditorPersistenceService {

    record OperationResult(boolean success, String message) {

    }

    private final EmakiForgePlugin plugin;
    private final EditorStateManager stateManager;
    private final EditorReloadService reloadService;
    private final SimpleDateFormat backupFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

    EditorPersistenceService(EmakiForgePlugin plugin,
            EditorStateManager stateManager,
            EditorReloadService reloadService) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.reloadService = reloadService;
    }

    EditorSession openExisting(Player player, EditableResourceType type, String resourceId) {
        if (player == null || type == null || resourceId == null || resourceId.isBlank()) {
            return null;
        }
        if (!stateManager.tryLock(type, resourceId, player.getUniqueId())) {
            return null;
        }
        Map<String, Object> data = readDocumentData(type, resourceId);
        if (data == null) {
            stateManager.releaseLock(type, resourceId, player.getUniqueId());
            return null;
        }
        return new EditorSession(player, type, resourceId, data, EditorSession.Mode.DOCUMENT);
    }

    EditorSession createNew(Player player, EditableResourceType type, String requestedId) {
        if (player == null || type == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>(type.defaultData());
        if (requestedId != null && !requestedId.isBlank()) {
            data.put("id", requestedId.trim());
        }
        return new EditorSession(player, type, null, data, EditorSession.Mode.DOCUMENT);
    }

    OperationResult save(EditorSession session) {
        if (session == null || session.resourceType() == null || session.rootData() == null) {
            return new OperationResult(false, "<red>没有可保存的资源草稿。</red>");
        }
        String currentId = session.currentId();
        if (currentId == null || currentId.isBlank()) {
            return new OperationResult(false, "<red>资源 ID 不能为空。</red>");
        }
        if (stateManager.isLockedByOther(session.resourceType(), currentId, session.playerId())) {
            return new OperationResult(false, "<red>该资源正被其他编辑器会话占用。</red>");
        }
        File targetFile = resourceFile(session.resourceType(), currentId);
        YamlSection configuration = toConfiguration(session.rootData());
        if (!validate(session.resourceType(), targetFile, configuration)) {
            return new OperationResult(false, "<red>资源校验失败，请检查当前字段。</red>");
        }
        try {
            if (targetFile.exists()) {
                backupFile(session.resourceType(), targetFile);
            }
            YamlFiles.save(targetFile, session.rootData());
            if (session.originalId() != null
                    && !session.originalId().isBlank()
                    && !session.originalId().equalsIgnoreCase(currentId)) {
                File oldFile = resourceFile(session.resourceType(), session.originalId());
                if (oldFile.exists()) {
                    backupFile(session.resourceType(), oldFile);
                    Files.deleteIfExists(oldFile.toPath());
                }
                stateManager.releaseLock(session.resourceType(), session.originalId(), session.playerId());
            }
            stateManager.tryLock(session.resourceType(), currentId, session.playerId());
            session.setOriginalId(currentId);
            session.setDirty(false);
            reloadService.reloadResources();
            return new OperationResult(true, "<green>资源已保存并热重载。</green>");
        } catch (Exception exception) {
            return new OperationResult(false, "<red>保存失败: " + exception.getMessage() + "</red>");
        }
    }

    OperationResult delete(EditorSession session) {
        if (session == null || session.resourceType() == null) {
            return new OperationResult(false, "<red>当前没有打开资源。</red>");
        }
        String resourceId = session.originalId() == null || session.originalId().isBlank()
                ? session.currentId()
                : session.originalId();
        if (resourceId == null || resourceId.isBlank()) {
            return new OperationResult(false, "<red>无法确定要删除的资源 ID。</red>");
        }
        File file = resourceFile(session.resourceType(), resourceId);
        if (!file.exists()) {
            return new OperationResult(false, "<red>目标文件不存在。</red>");
        }
        try {
            backupFile(session.resourceType(), file);
            Files.deleteIfExists(file.toPath());
            stateManager.releaseLock(session.resourceType(), resourceId, session.playerId());
            reloadService.reloadResources();
            return new OperationResult(true, "<green>资源已删除并热重载。</green>");
        } catch (Exception exception) {
            return new OperationResult(false, "<red>删除失败: " + exception.getMessage() + "</red>");
        }
    }

    OperationResult deleteDirect(Player player, EditableResourceType type, String resourceId) {
        if (player == null || type == null || resourceId == null || resourceId.isBlank()) {
            return new OperationResult(false, "<red>删除参数无效。</red>");
        }
        if (stateManager.isLockedByOther(type, resourceId, player.getUniqueId())) {
            return new OperationResult(false, "<red>目标资源正被其他编辑器会话占用。</red>");
        }
        File file = resourceFile(type, resourceId);
        if (!file.exists()) {
            return new OperationResult(false, "<red>未找到对应资源文件。</red>");
        }
        try {
            backupFile(type, file);
            Files.deleteIfExists(file.toPath());
            reloadService.reloadResources();
            return new OperationResult(true, "<green>资源已删除并热重载。</green>");
        } catch (Exception exception) {
            return new OperationResult(false, "<red>删除失败: " + exception.getMessage() + "</red>");
        }
    }

    private Map<String, Object> readDocumentData(EditableResourceType type, String resourceId) {
        YamlDirectoryLoader.LoadedYamlEntry<?> entry = loaderEntry(type, resourceId);
        if (entry == null || entry.configuration() == null) {
            return null;
        }
        Object plain = ConfigNodes.toPlainData(entry.configuration());
        if (!(plain instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> documentEntry : map.entrySet()) {
            if (documentEntry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(documentEntry.getKey()), EditorSession.copyValue(documentEntry.getValue()));
        }
        return result;
    }

    private YamlDirectoryLoader.LoadedYamlEntry<?> loaderEntry(EditableResourceType type, String resourceId) {
        return switch (type) {
            case BLUEPRINT -> plugin.blueprintLoader().entry(resourceId);
            case MATERIAL -> plugin.materialLoader().entry(resourceId);
            case RECIPE -> plugin.recipeLoader().entry(resourceId);
        };
    }

    private File resourceFile(EditableResourceType type, String resourceId) {
        return plugin.dataPath(type.directoryName(), type.fileName(resourceId)).toFile();
    }

    private boolean validate(EditableResourceType type, File file, YamlSection configuration) {
        return switch (type) {
            case BLUEPRINT -> {
                Blueprint value = plugin.blueprintLoader().parseDocument(file, configuration);
                yield value != null;
            }
            case MATERIAL -> {
                ForgeMaterial value = plugin.materialLoader().parseDocument(file, configuration);
                yield value != null;
            }
            case RECIPE -> {
                Recipe value = plugin.recipeLoader().parseDocument(file, configuration);
                yield value != null;
            }
        };
    }

    private YamlSection toConfiguration(Map<String, Object> data) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                normalized.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return new MapYamlSection(normalized);
    }

    private void backupFile(EditableResourceType type, File sourceFile) throws IOException {
        if (type == null || sourceFile == null || !sourceFile.exists()) {
            return;
        }
        File backupDirectory = plugin.dataPath("backups", type.id()).toFile();
        YamlFiles.ensureDirectory(backupDirectory.toPath());
        File backupFile = new File(backupDirectory, backupFormat.format(new Date()) + "-" + sourceFile.getName());
        Files.copy(sourceFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
