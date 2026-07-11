import { booleanField, defineConfigSchema, defineSchemaAst, enumField, objectField, textField } from 'emaki-web-console';
import { copy } from '../webModuleCopy';

export const codexConfigSchema = defineSchemaAst({
  id: 'emakicodex-config',
  moduleId: 'EmakiCodex',
  fields: [
    textField({ path: 'version', label: copy('版本', 'Version'), comment: copy('由资源同步维护的配置版本。', 'Configuration version maintained by resource sync.') }),
    textField({ path: 'language', label: copy('语言', 'Language'), comment: copy('使用的语言文件 ID。', 'Language bundle id.') }),
    booleanField({ path: 'release_default_data', label: copy('释放默认数据', 'Release default data'), comment: copy('是否生成示例成就页。', 'Whether to generate the example advancement page.') }),
    booleanField({ path: 'op_bypass', label: copy('OP 绕过', 'OP bypass'), comment: copy('OP 是否绕过命令权限。', 'Whether operators bypass command permissions.') }),
    objectField({ path: 'advancement', label: copy('原版成就', 'Vanilla advancements'), comment: copy('动态成就注册、发包坐标与事件触发设置。', 'Dynamic registration, packet coordinates, and event trigger settings.'), defaultValue: {} }),
    booleanField({ path: 'advancement.enabled', label: copy('启用成就', 'Enable advancements'), comment: copy('是否启用动态原版成就。', 'Whether dynamic vanilla advancements are enabled.') }),
    enumField({ path: 'advancement.platform', label: copy('注册平台', 'Registration platform'), comment: copy('当前使用 unsafe 动态注册平台。', 'Currently uses the unsafe dynamic registration platform.'), options: ['unsafe'], defaultValue: 'unsafe' }),
    booleanField({ path: 'advancement.announce-default', label: copy('默认广播', 'Default announce'), comment: copy('节点未单独配置时是否全服广播。', 'Default global announcement when a node does not override it.') }),
    booleanField({ path: 'advancement.remove-on-disable', label: copy('禁用时移除', 'Remove on disable'), comment: copy('禁用或重载时移除动态成就。', 'Remove dynamic advancements on disable or reload.') }),
    booleanField({ path: 'advancement.packet-coordinates', label: copy('发包坐标', 'Packet coordinates'), comment: copy('安装 PacketEvents 时使用节点 x/y 坐标。', 'Use node x/y coordinates when PacketEvents is installed.') }),
    booleanField({ path: 'advancement.triggers-enabled', label: copy('事件触发器', 'Event triggers'), comment: copy('是否启用 triggers.entries 自动授予。', 'Whether triggers.entries can automatically grant advancements.') }),
    booleanField({ path: 'debug', label: 'Debug', comment: copy('是否启用 Codex 调试输出。', 'Whether Codex debug output is enabled.') })
  ]
});

export const codexConfigManifestSchema = defineConfigSchema(codexConfigSchema);
