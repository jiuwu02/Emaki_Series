package emaki.jiuwu.craft.kits.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.config.KitIds;

public final class KitsEditorService {

    private final EmakiKitsPlugin plugin;
    private final GuiService gui;
    private final GuiTemplateLoader templates;
    private final ChatInputService chatInput;
    private final KitsEditorModule module;
    private final EditorService service;

    public KitsEditorService(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
        EmakiCoreLibPlugin coreLib = EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class);
        ExecutionDispatcher dispatcher = EmakiCoreLibApi.requireService(ExecutionDispatcher.class);
        this.templates = new GuiTemplateLoader(plugin);
        this.templates.load();
        this.gui = new GuiService(plugin, dispatcher, coreLib.asyncTaskScheduler(),
                coreLib.performanceMonitor(), coreLib.guiBackend());
        this.chatInput = new ChatInputService(plugin, dispatcher);
        this.module = new KitsEditorModule(plugin);
        this.service = new EditorService(plugin, gui, templates, plugin.messageService(),
                chatInput, module);
        module.bind(service);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
        plugin.getServer().getPluginManager().registerEvents(chatInput, plugin);
    }

    public void openBrowser(Player player) {
        service.openBrowser(player);
    }

    public void open(Player player, String kitId) {
        String id = KitIds.normalize(kitId);
        Path file = resolve(id);
        if (file == null) {
            plugin.messageService().send(player, "editor.open_failed", Map.of("id", kitId));
            return;
        }
        EditorSession session = service.open(player, file, id,
                module.validator(module.kitsRoot(), file), null);
        if (session == null) {
            plugin.messageService().send(player, "editor.open_failed", Map.of("id", kitId));
        }
    }

    public void flushAll() {
        service.flushAll();
    }

    public void closeAll() {
        service.closeAll();
        gui.closeAll();
        chatInput.close();
    }

    public EditorService editorService() {
        return service;
    }

    public KitsEditorModule module() {
        return module;
    }

    private Path resolve(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Path directory = plugin.getDataFolder().toPath().resolve(KitsEditorModule.DIRECTORY);
        Path direct = directory.resolve(id + ".yml");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> children = Files.list(directory)) {
            for (Path file : children.filter(Files::isRegularFile).toList()) {
                try {
                    YamlSection yaml = YamlFiles.load(file.toFile());
                    if (yaml != null && id.equals(KitIds.normalize(yaml.getString("id", "")))) {
                        return file;
                    }
                } catch (RuntimeException ignored) {
                }
            }
        } catch (IOException failure) {
            return null;
        }
        return null;
    }
}
