package emaki.jiuwu.craft.corelib.dialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.event.ClickEvent;

/**
 * 管理对话框定义并把它们展示给玩家。
 *
 * <p>定义由 {@link DialogLoader} 从 YAML 载入，本服务负责转换为原版对话框对象。
 * 转换结果不缓存：原版对话框依赖注册表上下文，重载后按需重建更安全。
 *
 * <p>展示与关闭必须在目标玩家的所有者线程调用。
 */
public final class DialogService {

    private final JavaPlugin plugin;
    private final DialogLoader loader;
    private final ItemSourceService itemSourceService;
    private volatile boolean enabled;

    public DialogService(JavaPlugin plugin, DialogLoader loader, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.loader = loader;
        this.itemSourceService = itemSourceService;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** {@return 加载的定义数量} */
    public int load() {
        if (!enabled) {
            return 0;
        }
        return loader.load();
    }

    public Collection<String> dialogIds() {
        return enabled ? List.copyOf(loader.all().keySet()) : List.of();
    }

    public boolean contains(String dialogId) {
        return enabled && Texts.isNotBlank(dialogId) && loader.get(Texts.normalizeId(dialogId)) != null;
    }

    public DialogDefinition definition(String dialogId) {
        return Texts.isBlank(dialogId) ? null : loader.get(Texts.normalizeId(dialogId));
    }

    /**
     * 向玩家展示指定对话框。
     *
     * @param player   目标玩家，必须在其所有者线程调用
     * @param dialogId 对话框 id
     * @return 成功展示返回 {@code true}
     */
    public boolean show(Player player, String dialogId) {
        if (!enabled || player == null) {
            return false;
        }
        DialogDefinition definition = definition(dialogId);
        if (definition == null) {
            return false;
        }
        try {
            player.showDialog(build(definition));
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[dialog] Could not show " + definition.id()
                    + " to " + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    /**
     * 关闭玩家当前的对话框。
     *
     * @param player 目标玩家，必须在其所有者线程调用
     * @return 已发出关闭请求返回 {@code true}
     */
    public boolean close(Player player) {
        if (player == null) {
            return false;
        }
        try {
            player.closeDialog();
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[dialog] Could not close dialog for "
                    + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private Dialog build(DialogDefinition definition) {
        return Dialog.create(factory -> factory.empty()
                .base(buildBase(definition))
                .type(buildType(definition)));
    }

    private DialogBase buildBase(DialogDefinition definition) {
        DialogBase.Builder builder = DialogBase.builder(MiniMessages.parse(definition.title()))
                .canCloseWithEscape(definition.canCloseWithEscape())
                .pause(definition.pause())
                .afterAction(switch (definition.afterAction()) {
                    case NONE -> DialogBase.DialogAfterAction.NONE;
                    case WAIT_FOR_RESPONSE -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
                    default -> DialogBase.DialogAfterAction.CLOSE;
                })
                .body(buildBody(definition))
                .inputs(buildInputs(definition));
        if (Texts.isNotBlank(definition.externalTitle())) {
            builder.externalTitle(MiniMessages.parse(definition.externalTitle()));
        }
        return builder.build();
    }

    private List<DialogBody> buildBody(DialogDefinition definition) {
        List<DialogBody> result = new ArrayList<>();
        for (DialogDefinition.Body body : definition.body()) {
            if (Texts.isNotBlank(body.item())) {
                ItemStack item = itemSourceService == null
                        ? null
                        : itemSourceService.createItem(ItemSourceUtil.parseShorthand(body.item()), 1);
                if (item != null) {
                    result.add(DialogBody.item(item).build());
                    continue;
                }
                plugin.getLogger().warning("[dialog] " + definition.id()
                        + ": unknown item source '" + body.item() + "', skipping body entry.");
                continue;
            }
            if (body.width() > 0) {
                result.add(DialogBody.plainMessage(MiniMessages.parse(body.text()), body.width()));
            } else {
                result.add(DialogBody.plainMessage(MiniMessages.parse(body.text())));
            }
        }
        return result;
    }

    private List<DialogInput> buildInputs(DialogDefinition definition) {
        List<DialogInput> result = new ArrayList<>();
        for (DialogDefinition.Input input : definition.inputs()) {
            result.add(switch (input.type()) {
                case BOOLEAN -> buildBooleanInput(input);
                case NUMBER_RANGE -> buildNumberInput(input);
                case SINGLE_OPTION -> buildOptionInput(input);
                default -> buildTextInput(input);
            });
        }
        return result;
    }

    private DialogInput buildTextInput(DialogDefinition.Input input) {
        TextDialogInput.Builder builder = DialogInput.text(input.key(), MiniMessages.parse(input.label()))
                .labelVisible(input.labelVisible());
        if (Texts.isNotBlank(input.initial())) {
            builder.initial(input.initial());
        }
        if (input.maxLength() > 0) {
            builder.maxLength(input.maxLength());
        }
        if (input.width() > 0) {
            builder.width(input.width());
        }
        return builder.build();
    }

    private DialogInput buildBooleanInput(DialogDefinition.Input input) {
        BooleanDialogInput.Builder builder = DialogInput.bool(input.key(), MiniMessages.parse(input.label()))
                .initial(input.initialBoolean());
        if (Texts.isNotBlank(input.onTrue())) {
            builder.onTrue(input.onTrue());
        }
        if (Texts.isNotBlank(input.onFalse())) {
            builder.onFalse(input.onFalse());
        }
        return builder.build();
    }

    private DialogInput buildNumberInput(DialogDefinition.Input input) {
        NumberRangeDialogInput.Builder builder = DialogInput.numberRange(
                input.key(), MiniMessages.parse(input.label()), input.start(), input.end());
        if (input.step() > 0F) {
            builder.step(input.step());
        }
        if (input.width() > 0) {
            builder.width(input.width());
        }
        return builder.build();
    }

    private DialogInput buildOptionInput(DialogDefinition.Input input) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (DialogDefinition.Input.Option option : input.options()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(
                    option.id(), MiniMessages.parse(option.display()), option.initial()));
        }
        SingleOptionDialogInput.Builder builder = DialogInput.singleOption(
                        input.key(), MiniMessages.parse(input.label()), entries)
                .labelVisible(input.labelVisible());
        if (input.width() > 0) {
            builder.width(input.width());
        }
        return builder.build();
    }

    private DialogType buildType(DialogDefinition definition) {
        List<DialogDefinition.Button> buttons = definition.buttons();
        return switch (definition.type()) {
            case CONFIRMATION -> DialogType.confirmation(
                    buildButton(buttons.get(0)), buildButton(buttons.get(1)));
            case MULTI_ACTION -> {
                List<ActionButton> actions = new ArrayList<>();
                for (DialogDefinition.Button button : buttons) {
                    ActionButton built = buildButton(button);
                    if (built != null) {
                        actions.add(built);
                    }
                }
                yield DialogType.multiAction(actions)
                        .exitAction(buildButton(definition.exitButton()))
                        .columns(definition.columns())
                        .build();
            }
            default -> buttons.isEmpty()
                    ? DialogType.notice()
                    : DialogType.notice(buildButton(buttons.get(0)));
        };
    }

    private ActionButton buildButton(DialogDefinition.Button button) {
        if (button == null) {
            return null;
        }
        ActionButton.Builder builder = ActionButton.builder(MiniMessages.parse(button.label()));
        if (Texts.isNotBlank(button.tooltip())) {
            builder.tooltip(MiniMessages.parse(button.tooltip()));
        }
        if (button.width() > 0) {
            builder.width(button.width());
        }
        DialogAction action = buildAction(button.action());
        if (action != null) {
            builder.action(action);
        }
        return builder.build();
    }

    private DialogAction buildAction(DialogDefinition.Action action) {
        if (action == null || Texts.isBlank(action.value())) {
            return null;
        }
        return switch (action.type()) {
            case COMMAND_TEMPLATE -> DialogAction.commandTemplate(action.value());
            case OPEN_URL -> DialogAction.staticAction(ClickEvent.openUrl(action.value()));
            case RUN_COMMAND -> DialogAction.staticAction(ClickEvent.runCommand(action.value()));
            default -> null;
        };
    }
}
