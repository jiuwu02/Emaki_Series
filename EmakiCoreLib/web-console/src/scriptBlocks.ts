import * as Blockly from 'blockly/core';
import { javascriptGenerator, Order } from 'blockly/javascript';
import { parse } from 'acorn';
import { t } from './i18n';
import type { WebScriptBlockCategory, WebScriptBlockDefinition } from './types';

export type ScriptBlockCatalog = {
  categories: WebScriptBlockCategory[];
  blocks: WebScriptBlockDefinition[];
  blocksById: Map<string, WebScriptBlockDefinition>;
  categoriesById: Map<string, WebScriptBlockCategory>;
};

type ParsedBlock = {
  block: WebScriptBlockDefinition;
  typeName: string;
  methodName: string;
  receiver: string;
  moduleId?: string;
  params: string[];
  output: boolean;
};

type ModuleUsage = {
  moduleId: string;
  alias: string;
};

export type ScriptSourceImportResult = {
  importedBlocks: number;
  rawBlocks: number;
  errors: string[];
};

const ENTRY_CATEGORY_ID = 'core.entry';
const LOGIC_CATEGORY_ID = 'core.logic';
const VARIABLE_CATEGORY_ID = 'core.variable';
const TEXT_CATEGORY_ID = 'core.text';
const NUMBER_CATEGORY_ID = 'core.number';
const OBJECT_CATEGORY_ID = 'core.object';
const ARRAY_CATEGORY_ID = 'core.array';
const EXPRESSION_CATEGORY_ID = 'core.expression';
const COMMON_API_CATEGORY_ID = 'core.common-api';
const API_CATEGORY_ID = 'core.api';
const BLOCK_PREFIX = 'emaki_script_';
const generatedBlocks = new Map<string, ParsedBlock>();
let activeModuleUsages: Map<string, ModuleUsage> | null = null;

const BUILTIN_CATEGORIES: WebScriptBlockCategory[] = [
  { id: ENTRY_CATEGORY_ID, label: t('core.script.block.entry.category'), comment: t('core.script.block.entry.categoryComment'), order: -200 },
  { id: LOGIC_CATEGORY_ID, label: t('core.script.block.logic.category'), order: -100 },
  { id: VARIABLE_CATEGORY_ID, label: t('core.script.block.variable.category'), order: -80 },
  { id: TEXT_CATEGORY_ID, label: t('core.script.block.text.category'), order: -60 },
  { id: NUMBER_CATEGORY_ID, label: t('core.script.block.math.category'), order: -50 },
  { id: OBJECT_CATEGORY_ID, label: t('core.script.block.object.category'), order: -40 },
  { id: ARRAY_CATEGORY_ID, label: t('core.script.block.array.category'), order: -35 },
  { id: EXPRESSION_CATEGORY_ID, label: t('core.script.block.expression.category'), comment: t('core.script.block.expression.categoryComment'), order: -30 },
  { id: COMMON_API_CATEGORY_ID, label: t('core.script.block.commonApi.category'), comment: t('core.script.block.commonApi.categoryComment'), order: -20 },
  { id: API_CATEGORY_ID, label: t('core.script.block.api.category'), comment: t('core.script.block.api.categoryComment'), order: 100 }
];

const BUILTIN_BLOCK_DEFINITIONS: WebScriptBlockDefinition[] = [
  { id: 'core.entry.script', categoryId: ENTRY_CATEGORY_ID, scope: 'core', label: t('core.script.block.entry'), comment: t('core.script.block.entry.tooltip'), codeTemplate: 'function main(ctx) {\n}', type: 'control', order: -200 },
  { id: 'core.return.value', categoryId: ENTRY_CATEGORY_ID, scope: 'core', label: t('core.script.block.return'), comment: t('core.script.block.return.tooltip'), codeTemplate: 'return value;', type: 'return', order: -100 },
  { id: 'core.variable.declare', categoryId: VARIABLE_CATEGORY_ID, scope: 'core', label: t('core.script.block.variableDeclare'), comment: t('core.script.block.variableDeclare.tooltip'), codeTemplate: 'const name = value;', type: 'statement', order: -90 },
  { id: 'core.variable.assign', categoryId: VARIABLE_CATEGORY_ID, scope: 'core', label: t('core.script.block.assign'), comment: t('core.script.block.assign.tooltip'), codeTemplate: 'target = value;', type: 'statement', order: -80 },
  { id: 'core.logic.ifElse', categoryId: LOGIC_CATEGORY_ID, scope: 'core', label: t('core.script.block.ifElse'), comment: t('core.script.block.ifElse.tooltip'), codeTemplate: 'if (condition) {}', type: 'statement', order: -100 },
  { id: 'core.object.literal', categoryId: OBJECT_CATEGORY_ID, scope: 'core', label: t('core.script.block.objectLiteral'), comment: t('core.script.block.objectLiteral.tooltip'), codeTemplate: '{}', type: 'value', order: -80 },
  { id: 'core.property.access', categoryId: OBJECT_CATEGORY_ID, scope: 'core', label: t('core.script.block.propertyAccess'), comment: t('core.script.block.propertyAccess.tooltip'), codeTemplate: 'object.property', type: 'value', order: -60 },
  { id: 'core.array.literal', categoryId: ARRAY_CATEGORY_ID, scope: 'core', label: t('core.script.block.arrayLiteral'), comment: t('core.script.block.arrayLiteral.tooltip'), codeTemplate: '[]', type: 'value', order: -70 },
  { id: 'core.index.access', categoryId: ARRAY_CATEGORY_ID, scope: 'core', label: t('core.script.block.indexAccess'), comment: t('core.script.block.indexAccess.tooltip'), codeTemplate: 'object[key]', type: 'value', order: -60 },
  { id: 'core.expression.value', categoryId: EXPRESSION_CATEGORY_ID, scope: 'core', label: t('core.script.block.expressionValue'), comment: t('core.script.block.expressionValue.tooltip'), codeTemplate: 'value', type: 'value', order: -80 },
  { id: 'core.call.value', categoryId: EXPRESSION_CATEGORY_ID, scope: 'core', label: t('core.script.block.callValue'), comment: t('core.script.block.callValue.tooltip'), codeTemplate: 'fn(args)', type: 'value', order: 9000 },
  { id: 'core.player.sendMessage', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.player', label: t('core.script.block.playerSendMessage'), comment: t('core.script.block.playerSendMessage.tooltip'), codeTemplate: 'emaki.player.sendMessage(message)', type: 'statement', order: -100 },
  { id: 'core.logger.info', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.logger', label: t('core.script.block.loggerInfo'), comment: t('core.script.block.loggerInfo.tooltip'), codeTemplate: 'emaki.logger.info(message)', type: 'statement', order: -90 },
  { id: 'core.logger.warn', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.logger', label: t('core.script.block.loggerWarn'), comment: t('core.script.block.loggerWarn.tooltip'), codeTemplate: 'emaki.logger.warn(message)', type: 'statement', order: -80 },
  { id: 'core.logger.error', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.logger', label: t('core.script.block.loggerError'), comment: t('core.script.block.loggerError.tooltip'), codeTemplate: 'emaki.logger.error(message)', type: 'statement', order: -70 },
  { id: 'core.context.placeholder', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.context', label: t('core.script.block.contextPlaceholder'), comment: t('core.script.block.contextPlaceholder.tooltip'), codeTemplate: 'emaki.context.placeholder(key)', type: 'string', order: -60 },
  { id: 'core.player.exists', categoryId: COMMON_API_CATEGORY_ID, scope: 'emaki.player', label: t('core.script.block.playerExists'), comment: t('core.script.block.playerExists.tooltip'), codeTemplate: 'emaki.player.exists()', type: 'boolean', order: -50 },
  { id: 'core.call.statement', categoryId: API_CATEGORY_ID, scope: 'core', label: t('core.script.block.callStatement'), comment: t('core.script.block.callStatement.tooltip'), codeTemplate: 'fn(args);', type: 'statement', order: 9010 }
];

export function normalizeScriptBlockCatalog(categories: WebScriptBlockCategory[] = [], blocks: WebScriptBlockDefinition[] = []): ScriptBlockCatalog {
  const categoriesById = new Map<string, WebScriptBlockCategory>();
  const blocksById = new Map<string, WebScriptBlockDefinition>();

  for (const category of BUILTIN_CATEGORIES) {
    categoriesById.set(categoryKey(category), { ...category, id: normalizeId(category.id) });
  }

  for (const category of categories) {
    const id = normalizeId(category.id);
    const label = String(category.label ?? '').trim();
    if (!id || !label) continue;
    const normalized: WebScriptBlockCategory = {
      moduleId: normalizeModuleId(category.moduleId),
      id,
      label,
      comment: category.comment ? String(category.comment) : undefined,
      order: safeOrder(category.order)
    };
    categoriesById.set(categoryKey(normalized), normalized);
  }

  for (const block of [...BUILTIN_BLOCK_DEFINITIONS, ...blocks]) {
    const id = normalizeId(block.id);
    if (!id || !block.scope || !block.label || !block.codeTemplate) continue;
    const moduleId = normalizeModuleId(block.moduleId);
    const requestedCategoryId = normalizeId(block.categoryId);
    const categoryId = block.id.startsWith('core.') ? requestedCategoryId : resolveRegisteredCategoryId(categoriesById, moduleId, requestedCategoryId);
    const next: WebScriptBlockDefinition = {
      ...block,
      moduleId,
      id,
      categoryId,
      scope: normalizeScope(block.scope),
      label: String(block.label).trim(),
      comment: block.comment ? String(block.comment) : undefined,
      codeTemplate: String(block.codeTemplate).trim(),
      callPattern: String(block.callPattern || '').trim(),
      type: String(block.type || 'function').trim().toLowerCase(),
      order: safeOrder(block.order)
    };
    blocksById.set(blockKey(next), next);
  }

  return {
    categories: [...categoriesById.values()].sort(sortByOrderAndLabel),
    blocks: [...blocksById.values()].sort(sortByOrderAndLabel),
    blocksById,
    categoriesById
  };
}

export function registerScriptBlocklyBlocks(catalog: ScriptBlockCatalog): void {
  registerCoreBlocks();

  const blockJson = catalog.blocks
    .filter(block => !block.id.startsWith('core.'))
    .map(parseRegisteredBlock)
    .map(parsed => {
      generatedBlocks.set(parsed.typeName, parsed);
      return blockJsonDefinition(parsed);
    })
    .filter(definition => !Blockly.Blocks[String(definition.type)]);

  if (blockJson.length) Blockly.common.defineBlocksWithJsonArray(blockJson);

  for (const [typeName, parsed] of generatedBlocks) {
    javascriptGenerator.forBlock[typeName] = (block, generator) => generateRegisteredBlock(block, generator, parsed);
  }
}

export function createScriptBlocklyToolbox(catalog: ScriptBlockCatalog): Blockly.utils.toolbox.ToolboxDefinition {
  const dynamicCategories = catalog.categories
    .filter(category => !category.id.startsWith('core.'))
    .map(category => {
      const categoryBlocks = catalog.blocks
        .filter(block => !block.id.startsWith('core.') && !isCoveredByCommonApiBlock(block) && block.categoryId === category.id && (!category.moduleId || normalizeModuleId(block.moduleId) === normalizeModuleId(category.moduleId)))
        .sort(sortByOrderAndLabel);
      return { category, categoryBlocks };
    })
    .filter(entry => entry.categoryBlocks.length > 0);
  const fallbackApiBlocks = catalog.blocks.filter(block => !block.id.startsWith('core.') && !isCoveredByCommonApiBlock(block) && block.categoryId === API_CATEGORY_ID).sort(sortByOrderAndLabel);

  return {
    kind: 'categoryToolbox',
    contents: [
      {
        kind: 'category',
        name: t('core.script.block.entry.category'),
        colour: '#166534',
        contents: [
          { kind: 'block', type: 'emaki_script_entry' },
          { kind: 'block', type: 'emaki_return_value' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.logic.category'),
        colour: '#1d4ed8',
        contents: [
          { kind: 'block', type: 'emaki_if_else' },
          { kind: 'block', type: 'controls_if' },
          { kind: 'block', type: 'logic_compare' },
          { kind: 'block', type: 'logic_operation' },
          { kind: 'block', type: 'logic_negate' },
          { kind: 'block', type: 'logic_boolean' },
          { kind: 'block', type: 'logic_null' },
          { kind: 'block', type: 'logic_ternary' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.variable.category'),
        colour: '#7c3aed',
        contents: [
          { kind: 'block', type: 'emaki_variable_declare' },
          { kind: 'block', type: 'emaki_assign' },
          variableToolboxBlock('variables_get'),
          variableToolboxBlock('variables_set')
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.text.category'),
        colour: '#b45309',
        contents: [
          shadowedBlock('text', 'TEXT', t('core.script.block.text.default')),
          { kind: 'block', type: 'text_join' },
          { kind: 'block', type: 'text_length' },
          { kind: 'block', type: 'text_isEmpty' },
          { kind: 'block', type: 'text_changeCase' },
          { kind: 'block', type: 'text_trim' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.math.category'),
        colour: '#4338ca',
        contents: [
          { kind: 'block', type: 'math_number' },
          { kind: 'block', type: 'math_arithmetic' },
          { kind: 'block', type: 'math_single' },
          { kind: 'block', type: 'math_round' },
          { kind: 'block', type: 'math_modulo' },
          { kind: 'block', type: 'math_random_int' },
          { kind: 'block', type: 'math_random_float' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.object.category'),
        colour: '#0f766e',
        contents: [
          { kind: 'block', type: 'emaki_object_literal' },
          { kind: 'block', type: 'emaki_property_access' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.array.category'),
        colour: '#0f766e',
        contents: [
          { kind: 'block', type: 'emaki_array_literal' },
          { kind: 'block', type: 'emaki_index_access' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.expression.category'),
        colour: '#0369a1',
        contents: [
          { kind: 'block', type: 'emaki_expression_value' },
          { kind: 'block', type: 'emaki_call_value' }
        ]
      },
      {
        kind: 'category',
        name: t('core.script.block.commonApi.category'),
        colour: '#0284c7',
        contents: [
          { kind: 'block', type: 'emaki_player_send_message', inputs: { MESSAGE: { shadow: { type: 'text', fields: { TEXT: '' } } } } },
          { kind: 'block', type: 'emaki_logger_info', inputs: { MESSAGE: { shadow: { type: 'text', fields: { TEXT: '' } } } } },
          { kind: 'block', type: 'emaki_logger_warn', inputs: { MESSAGE: { shadow: { type: 'text', fields: { TEXT: '' } } } } },
          { kind: 'block', type: 'emaki_logger_error', inputs: { MESSAGE: { shadow: { type: 'text', fields: { TEXT: '' } } } } },
          { kind: 'block', type: 'emaki_context_placeholder', inputs: { KEY: { shadow: { type: 'text', fields: { TEXT: 'key' } } } } },
          { kind: 'block', type: 'emaki_player_exists' }
        ]
      },
      ...dynamicCategories.map(({ category, categoryBlocks }) => ({
        kind: 'category' as const,
        name: category.label,
        colour: '#0369a1',
        contents: categoryBlocks.map(block => toolboxBlock(block))
      })),
      {
        kind: 'category',
        name: t('core.script.block.api.category'),
        colour: '#0369a1',
        contents: [
          ...fallbackApiBlocks.map(block => toolboxBlock(block)),
          { kind: 'block', type: 'emaki_call_statement' }
        ]
      }
    ]
  };
}

export function generateScriptFromWorkspace(workspace: Blockly.WorkspaceSvg): string {
  activeModuleUsages = new Map();
  javascriptGenerator.init(workspace);
  try {
    const topBlocks = (workspace.getTopBlocks(true) as Blockly.BlockSvg[]).filter(isScriptTopBlock);
    const code = topBlocks.map(block => blockToScriptCode(block)).join('\n').replace(/\n{3,}/g, '\n\n').trimEnd();
    const declarations = [...activeModuleUsages.values()]
      .sort((left, right) => left.alias.localeCompare(right.alias))
      .map(usage => `const ${usage.alias} = emaki.module(${JSON.stringify(usage.moduleId)});`);
    const next = declarations.length ? `${declarations.join('\n')}\n\n${stripDuplicateModuleDeclarations(code)}`.trimEnd() : code;
    const finished = javascriptGenerator.finish(next).trimEnd();
    return finished ? `${finished}\n` : '';
  } finally {
    activeModuleUsages = null;
  }
}

export function scriptBlockCount(workspace: Blockly.WorkspaceSvg | null): number {
  return workspace?.getAllBlocks(false).filter(isUserWorkspaceBlock).length ?? 0;
}

export function pruneDetachedValueBlocks(workspace: Blockly.WorkspaceSvg): void {
  for (const block of workspace.getTopBlocks(false) as Blockly.BlockSvg[]) {
    if (isDetachedValueBlock(block)) block.dispose(false);
  }
}

export function loadScriptSourceIntoWorkspace(workspace: Blockly.WorkspaceSvg, source: string, catalog: ScriptBlockCatalog): ScriptSourceImportResult {
  workspace.clear();
  const text = String(source ?? '').trim();
  if (!text) return { importedBlocks: 0, rawBlocks: 0, errors: [] };

  const aliases = collectModuleAliases(text);
  const errors: string[] = [];
  let importedBlocks = 0;

  try {
    const program = parse(text, { ecmaVersion: 'latest', sourceType: 'script', allowReturnOutsideFunction: true }) as any;
    let previousConnection: Blockly.Connection | null = null;
    let y = 24;

    for (const statement of program.body ?? []) {
      if (isModuleDeclarationNode(statement)) continue;
      const block = statementToBlock(workspace, statement, catalog, aliases, errors, 24, y);
      if (!block) continue;
      importedBlocks++;
      if (previousConnection && block.previousConnection) {
        safeConnect(previousConnection, block.previousConnection, errors);
      } else {
        const position = block.getRelativeToSurfaceXY();
        block.moveBy(24 - position.x, y - position.y);
      }
      previousConnection = block.nextConnection ?? null;
      if (!previousConnection) y += Math.max(96, block.getHeightWidth().height + 32);
    }

    for (const block of workspace.getAllBlocks(false)) block.render();
    layoutTopBlocks(workspace);
    return { importedBlocks, rawBlocks: errors.length, errors };
  } catch (exception) {
    return { importedBlocks: 0, rawBlocks: 1, errors: [exception instanceof Error ? exception.message : String(exception)] };
  }
}

function registerCoreBlocks(): void {
  const coreDefinitions = [
    {
      type: 'emaki_script_entry',
      message0: `${t('core.script.block.entry')} %1 ( %2 ) %3`,
      args0: [
        { type: 'field_input', name: 'NAME', text: 'main' },
        { type: 'field_input', name: 'PARAMS', text: 'ctx' },
        { type: 'input_statement', name: 'BODY' }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: '#166534',
      tooltip: t('core.script.block.entry.tooltip')
    },
    {
      type: 'emaki_return_value',
      message0: `${t('core.script.block.return')} %1`,
      args0: [{ type: 'input_value', name: 'VALUE' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#0369a1',
      tooltip: t('core.script.block.return.tooltip')
    },
    {
      type: 'emaki_variable_declare',
      message0: `${t('core.script.block.variableDeclare')} %1 %2 = %3`,
      args0: [
        { type: 'field_dropdown', name: 'KIND', options: [['const', 'const'], ['let', 'let'], ['var', 'var']] },
        { type: 'field_input', name: 'NAME', text: 'value' },
        { type: 'input_value', name: 'VALUE' }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: '#7c3aed',
      tooltip: t('core.script.block.variableDeclare.tooltip')
    },
    {
      type: 'emaki_assign',
      message0: `${t('core.script.block.assign')} %1 = %2`,
      args0: [
        { type: 'field_input', name: 'TARGET', text: 'value' },
        { type: 'input_value', name: 'VALUE' }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: '#7c3aed',
      tooltip: t('core.script.block.assign.tooltip')
    },
    {
      type: 'emaki_if_else',
      message0: `${t('core.script.block.ifElse')} %1 %2 ${t('core.script.block.then')} %3 ${t('core.script.block.else')} %4`,
      args0: [
        { type: 'input_value', name: 'IF', check: 'Boolean' },
        { type: 'input_dummy' },
        { type: 'input_statement', name: 'THEN' },
        { type: 'input_statement', name: 'ELSE' }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: '#1d4ed8',
      tooltip: t('core.script.block.ifElse.tooltip')
    },
    {
      type: 'emaki_object_literal',
      message0: `${t('core.script.block.objectLiteral')} %1`,
      args0: [{ type: 'field_input', name: 'CODE', text: '{}' }],
      output: null,
      colour: '#0f766e',
      tooltip: t('core.script.block.objectLiteral.tooltip')
    },
    {
      type: 'emaki_array_literal',
      message0: `${t('core.script.block.arrayLiteral')} %1`,
      args0: [{ type: 'field_input', name: 'CODE', text: '[]' }],
      output: null,
      colour: '#0f766e',
      tooltip: t('core.script.block.arrayLiteral.tooltip')
    },
    {
      type: 'emaki_property_access',
      message0: `${t('core.script.block.propertyAccess')} %1 . %2`,
      args0: [{ type: 'input_value', name: 'OBJECT' }, { type: 'field_input', name: 'PROPERTY', text: 'property' }],
      output: null,
      colour: '#0f766e',
      tooltip: t('core.script.block.propertyAccess.tooltip')
    },
    {
      type: 'emaki_index_access',
      message0: `${t('core.script.block.indexAccess')} %1 [ %2 ]`,
      args0: [{ type: 'input_value', name: 'OBJECT' }, { type: 'input_value', name: 'KEY' }],
      output: null,
      colour: '#0f766e',
      tooltip: t('core.script.block.indexAccess.tooltip')
    },
    {
      type: 'emaki_expression_value',
      message0: `${t('core.script.block.expressionValue', undefined, '表达式')} %1`,
      args0: [{ type: 'field_input', name: 'CODE', text: 'value' }],
      output: null,
      colour: '#0369a1',
      tooltip: t('core.script.block.expressionValue.tooltip', undefined, '任意 JavaScript 表达式')
    },
    {
      type: 'emaki_call_value',
      message0: `${t('core.script.block.callValue')} %1 ( %2 )`,
      args0: [{ type: 'field_input', name: 'CALLEE', text: 'fn' }, { type: 'field_input', name: 'ARGS', text: '' }],
      output: null,
      colour: '#0369a1',
      tooltip: t('core.script.block.callValue.tooltip')
    },
    {
      type: 'emaki_call_statement',
      message0: `${t('core.script.block.callStatement')} %1 ( %2 )`,
      args0: [{ type: 'field_input', name: 'CALLEE', text: 'fn' }, { type: 'field_input', name: 'ARGS', text: '' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#0369a1',
      tooltip: t('core.script.block.callStatement.tooltip')
    },
    {
      type: 'emaki_player_send_message',
      message0: `${t('core.script.block.playerSendMessage')} %1`,
      args0: [{ type: 'input_value', name: 'MESSAGE', check: 'String' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#0284c7',
      tooltip: t('core.script.block.playerSendMessage.tooltip')
    },
    {
      type: 'emaki_logger_info',
      message0: `${t('core.script.block.loggerInfo')} %1`,
      args0: [{ type: 'input_value', name: 'MESSAGE', check: 'String' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#0284c7',
      tooltip: t('core.script.block.loggerInfo.tooltip')
    },
    {
      type: 'emaki_logger_warn',
      message0: `${t('core.script.block.loggerWarn')} %1`,
      args0: [{ type: 'input_value', name: 'MESSAGE', check: 'String' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#b45309',
      tooltip: t('core.script.block.loggerWarn.tooltip')
    },
    {
      type: 'emaki_logger_error',
      message0: `${t('core.script.block.loggerError')} %1`,
      args0: [{ type: 'input_value', name: 'MESSAGE', check: 'String' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#be123c',
      tooltip: t('core.script.block.loggerError.tooltip')
    },
    {
      type: 'emaki_context_placeholder',
      message0: `${t('core.script.block.contextPlaceholder')} %1`,
      args0: [{ type: 'input_value', name: 'KEY', check: 'String' }],
      output: 'String',
      colour: '#0284c7',
      tooltip: t('core.script.block.contextPlaceholder.tooltip')
    },
    {
      type: 'emaki_player_exists',
      message0: t('core.script.block.playerExists'),
      output: 'Boolean',
      colour: '#0284c7',
      tooltip: t('core.script.block.playerExists.tooltip')
    }
  ].filter(definition => !Blockly.Blocks[definition.type]);

  if (coreDefinitions.length) Blockly.common.defineBlocksWithJsonArray(coreDefinitions);

  javascriptGenerator.forBlock.emaki_script_entry = (block, generator) => {
    const body = generator.statementToCode(block, 'BODY');
    const name = safeIdentifier(block.getFieldValue('NAME'), 'main');
    const params = sanitizeParameterList(block.getFieldValue('PARAMS'));
    return body ? `function ${name}(${params}) {\n${body}}\n` : `function ${name}(${params}) {\n}\n`;
  };
  javascriptGenerator.forBlock.emaki_return_value = (block, generator) => `return ${generator.valueToCode(block, 'VALUE', Order.NONE) || 'undefined'};\n`;
  javascriptGenerator.forBlock.emaki_variable_declare = (block, generator) => `${block.getFieldValue('KIND') || 'const'} ${safeIdentifier(block.getFieldValue('NAME'), 'value')} = ${generator.valueToCode(block, 'VALUE', Order.NONE) || 'undefined'};\n`;
  javascriptGenerator.forBlock.emaki_assign = (block, generator) => `${String(block.getFieldValue('TARGET') || 'value').trim()} = ${generator.valueToCode(block, 'VALUE', Order.NONE) || 'undefined'};\n`;
  javascriptGenerator.forBlock.emaki_if_else = (block, generator) => {
    const condition = generator.valueToCode(block, 'IF', Order.NONE) || 'false';
    const thenCode = generator.statementToCode(block, 'THEN');
    const elseCode = generator.statementToCode(block, 'ELSE');
    return elseCode ? `if (${condition}) {\n${thenCode}} else {\n${elseCode}}\n` : `if (${condition}) {\n${thenCode}}\n`;
  };
  javascriptGenerator.forBlock.emaki_object_literal = block => [String(block.getFieldValue('CODE') || '{}').trim() || '{}', Order.ATOMIC];
  javascriptGenerator.forBlock.emaki_array_literal = block => [String(block.getFieldValue('CODE') || '[]').trim() || '[]', Order.ATOMIC];
  javascriptGenerator.forBlock.emaki_property_access = (block, generator) => [`${generator.valueToCode(block, 'OBJECT', Order.MEMBER) || 'undefined'}.${safeIdentifier(block.getFieldValue('PROPERTY'), 'property')}`, Order.MEMBER];
  javascriptGenerator.forBlock.emaki_index_access = (block, generator) => [`${generator.valueToCode(block, 'OBJECT', Order.MEMBER) || 'undefined'}[${generator.valueToCode(block, 'KEY', Order.NONE) || 'undefined'}]`, Order.MEMBER];
  javascriptGenerator.forBlock.emaki_expression_value = block => [String(block.getFieldValue('CODE') || 'undefined').trim() || 'undefined', Order.ATOMIC];
  javascriptGenerator.forBlock.emaki_call_value = block => [`${String(block.getFieldValue('CALLEE') || 'fn').trim()}(${String(block.getFieldValue('ARGS') || '').trim()})`, Order.FUNCTION_CALL];
  javascriptGenerator.forBlock.emaki_call_statement = block => `${String(block.getFieldValue('CALLEE') || 'fn').trim()}(${String(block.getFieldValue('ARGS') || '').trim()});\n`;
  javascriptGenerator.forBlock.emaki_player_send_message = (block, generator) => `emaki.player.sendMessage(${generator.valueToCode(block, 'MESSAGE', Order.NONE) || '""'});\n`;
  javascriptGenerator.forBlock.emaki_logger_info = (block, generator) => `emaki.logger.info(${generator.valueToCode(block, 'MESSAGE', Order.NONE) || '""'});\n`;
  javascriptGenerator.forBlock.emaki_logger_warn = (block, generator) => `emaki.logger.warn(${generator.valueToCode(block, 'MESSAGE', Order.NONE) || '""'});\n`;
  javascriptGenerator.forBlock.emaki_logger_error = (block, generator) => `emaki.logger.error(${generator.valueToCode(block, 'MESSAGE', Order.NONE) || '""'});\n`;
  javascriptGenerator.forBlock.emaki_context_placeholder = (block, generator) => [`emaki.context.placeholder(${generator.valueToCode(block, 'KEY', Order.NONE) || '"key"'})`, Order.FUNCTION_CALL];
  javascriptGenerator.forBlock.emaki_player_exists = () => ['emaki.player.exists()', Order.FUNCTION_CALL];
}

function statementToBlock(workspace: Blockly.WorkspaceSvg, node: any, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[], x: number, y: number): Blockly.BlockSvg | null {
  switch (node.type) {
    case 'FunctionDeclaration': {
      const block = createBlock(workspace, 'emaki_script_entry', x, y);
      block.setFieldValue(safeIdentifier(node.id?.name, 'main'), 'NAME');
      block.setFieldValue((node.params ?? []).map(nodeSource).join(', '), 'PARAMS');
      connectStatementList(block.getInput('BODY')?.connection ?? null, blockStatements(node.body), workspace, catalog, aliases, errors);
      return block;
    }
    case 'ReturnStatement': {
      const block = createBlock(workspace, 'emaki_return_value', x, y);
      connectValue(block, 'VALUE', expressionToBlock(workspace, node.argument, catalog, aliases, errors));
      return block;
    }
    case 'VariableDeclaration': {
      let first: Blockly.BlockSvg | null = null;
      let previous: Blockly.BlockSvg | null = null;
      for (const declaration of node.declarations ?? []) {
        if (declaration.id?.type !== 'Identifier') {
          errors.push(t('core.script.parse.unsupportedNode', { type: declaration.id?.type || 'Pattern' }));
          continue;
        }
        const block = createBlock(workspace, 'emaki_variable_declare', first ? x + 32 : x, first ? y + 72 : y);
        block.setFieldValue(node.kind || 'const', 'KIND');
        block.setFieldValue(declaration.id.name, 'NAME');
        connectValue(block, 'VALUE', expressionToBlock(workspace, declaration.init, catalog, aliases, errors));
        if (!first) first = block;
        if (previous?.nextConnection && block.previousConnection) safeConnect(previous.nextConnection, block.previousConnection, errors);
        previous = block;
      }
      return first;
    }
    case 'ExpressionStatement': {
      const expression = node.expression;
      if (expression?.type === 'AssignmentExpression') {
        const block = createBlock(workspace, 'emaki_assign', x, y);
        block.setFieldValue(nodeSource(expression.left), 'TARGET');
        connectValue(block, 'VALUE', expressionToBlock(workspace, expression.right, catalog, aliases, errors));
        return block;
      }
      const coreCall = expression?.type === 'CallExpression' ? createCoreCallWorkspaceBlock(workspace, expression, x, y, catalog, aliases, errors, false) : null;
      if (coreCall) return coreCall;
      const parsed = findRegisteredCall(expression, catalog, aliases, false);
      if (parsed) return createRegisteredWorkspaceBlock(workspace, parsed, x, y, catalog, aliases, errors);
      if (expression?.type === 'CallExpression') {
        const block = createBlock(workspace, 'emaki_call_statement', x, y);
        block.setFieldValue(nodeSource(expression.callee), 'CALLEE');
        block.setFieldValue((expression.arguments ?? []).map(nodeSource).join(', '), 'ARGS');
        return block;
      }
      errors.push(t('core.script.parse.unsupportedNode', { type: expression?.type || node.type }));
      return null;
    }
    case 'IfStatement': {
      const block = createBlock(workspace, 'emaki_if_else', x, y);
      connectValue(block, 'IF', expressionToBlock(workspace, node.test, catalog, aliases, errors));
      connectStatementList(block.getInput('THEN')?.connection ?? null, blockStatements(node.consequent), workspace, catalog, aliases, errors);
      connectStatementList(block.getInput('ELSE')?.connection ?? null, blockStatements(node.alternate), workspace, catalog, aliases, errors);
      return block;
    }
    default:
      errors.push(t('core.script.parse.unsupportedNode', { type: node.type }));
      return null;
  }
}

function expressionToBlock(workspace: Blockly.WorkspaceSvg, node: any, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[]): Blockly.BlockSvg {
  if (!node) return rawExpressionBlock(workspace, 'undefined');
  switch (node.type) {
    case 'Literal':
      if (typeof node.value === 'string') {
        const block = createBlock(workspace, 'text', 0, 0);
        block.setFieldValue(node.value, 'TEXT');
        return block;
      }
      if (typeof node.value === 'number') {
        const block = createBlock(workspace, 'math_number', 0, 0);
        block.setFieldValue(String(node.value), 'NUM');
        return block;
      }
      if (typeof node.value === 'boolean') {
        const block = createBlock(workspace, 'logic_boolean', 0, 0);
        block.setFieldValue(node.value ? 'TRUE' : 'FALSE', 'BOOL');
        return block;
      }
      if (node.value === null) return createBlock(workspace, 'logic_null', 0, 0);
      return rawExpressionBlock(workspace, nodeSource(node));
    case 'Identifier':
      return rawExpressionBlock(workspace, node.name);
    case 'MemberExpression': {
      if (node.computed) {
        const block = createBlock(workspace, 'emaki_index_access', 0, 0);
        connectValue(block, 'OBJECT', expressionToBlock(workspace, node.object, catalog, aliases, errors));
        connectValue(block, 'KEY', expressionToBlock(workspace, node.property, catalog, aliases, errors));
        return block;
      }
      const block = createBlock(workspace, 'emaki_property_access', 0, 0);
      connectValue(block, 'OBJECT', expressionToBlock(workspace, node.object, catalog, aliases, errors));
      block.setFieldValue(node.property?.name || 'property', 'PROPERTY');
      return block;
    }
    case 'CallExpression': {
      const coreCall = createCoreCallWorkspaceBlock(workspace, node, 0, 0, catalog, aliases, errors, true);
      if (coreCall) return coreCall;
      const parsed = findRegisteredCall(node, catalog, aliases, true);
      if (parsed) return createRegisteredWorkspaceBlock(workspace, parsed, 0, 0, catalog, aliases, errors);
      const block = createBlock(workspace, 'emaki_call_value', 0, 0);
      block.setFieldValue(nodeSource(node.callee), 'CALLEE');
      block.setFieldValue((node.arguments ?? []).map(nodeSource).join(', '), 'ARGS');
      return block;
    }
    case 'BinaryExpression':
    case 'LogicalExpression':
      return binaryExpressionBlock(workspace, node, catalog, aliases, errors);
    case 'ObjectExpression':
      return rawExpressionBlock(workspace, `{ ${node.properties.map((property: any) => `${property.key?.name || JSON.stringify(property.key?.value || '')}: ${nodeSource(property.value)}`).join(', ')} }`, 'emaki_object_literal');
    case 'ArrayExpression':
      return rawExpressionBlock(workspace, `[${(node.elements ?? []).map(nodeSource).join(', ')}]`, 'emaki_array_literal');
    case 'UnaryExpression':
      return rawExpressionBlock(workspace, node.operator === 'typeof' ? `typeof ${nodeSource(node.argument)}` : `${node.operator}${nodeSource(node.argument)}`);
    case 'ConditionalExpression': {
      const block = createBlock(workspace, 'logic_ternary', 0, 0);
      connectValue(block, 'IF', expressionToBlock(workspace, node.test, catalog, aliases, errors));
      connectValue(block, 'THEN', expressionToBlock(workspace, node.consequent, catalog, aliases, errors));
      connectValue(block, 'ELSE', expressionToBlock(workspace, node.alternate, catalog, aliases, errors));
      return block;
    }
    default:
      errors.push(t('core.script.parse.unsupportedNode', { type: node.type }));
      return rawExpressionBlock(workspace, nodeSource(node));
  }
}

function binaryExpressionBlock(workspace: Blockly.WorkspaceSvg, node: any, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[]): Blockly.BlockSvg {
  if (['===', '==', '!==', '!=', '<', '<=', '>', '>='].includes(node.operator)) {
    const block = createBlock(workspace, 'logic_compare', 0, 0);
    const op = ({ '===': 'EQ', '==': 'EQ', '!==': 'NEQ', '!=': 'NEQ', '<': 'LT', '<=': 'LTE', '>': 'GT', '>=': 'GTE' } as Record<string, string>)[node.operator] || 'EQ';
    block.setFieldValue(op, 'OP');
    connectValue(block, 'A', expressionToBlock(workspace, node.left, catalog, aliases, errors));
    connectValue(block, 'B', expressionToBlock(workspace, node.right, catalog, aliases, errors));
    return block;
  }
  if (node.operator === '&&' || node.operator === '||') {
    const block = createBlock(workspace, 'logic_operation', 0, 0);
    block.setFieldValue(node.operator === '&&' ? 'AND' : 'OR', 'OP');
    connectValue(block, 'A', expressionToBlock(workspace, node.left, catalog, aliases, errors));
    connectValue(block, 'B', expressionToBlock(workspace, node.right, catalog, aliases, errors));
    return block;
  }
  if (['+', '-', '*', '/', '^'].includes(node.operator)) {
    const block = createBlock(workspace, 'math_arithmetic', 0, 0);
    const op = ({ '+': 'ADD', '-': 'MINUS', '*': 'MULTIPLY', '/': 'DIVIDE', '^': 'POWER' } as Record<string, string>)[node.operator] || 'ADD';
    block.setFieldValue(op, 'OP');
    connectValue(block, 'A', expressionToBlock(workspace, node.left, catalog, aliases, errors));
    connectValue(block, 'B', expressionToBlock(workspace, node.right, catalog, aliases, errors));
    return block;
  }
  return rawExpressionBlock(workspace, nodeSource(node));
}

function rawExpressionBlock(workspace: Blockly.WorkspaceSvg, code: string, type = 'emaki_expression_value'): Blockly.BlockSvg {
  const block = createBlock(workspace, type, 0, 0);
  if (type === 'emaki_object_literal' || type === 'emaki_array_literal' || type === 'emaki_expression_value') {
    block.setFieldValue(code, 'CODE');
  } else {
    block.setFieldValue(code, 'CALLEE');
    block.setFieldValue('', 'ARGS');
  }
  return block;
}

function connectStatementList(connection: Blockly.Connection | null, statements: any[], workspace: Blockly.WorkspaceSvg, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[]): void {
  let previousConnection = connection;
  for (const statement of statements) {
    const block = statementToBlock(workspace, statement, catalog, aliases, errors, 0, 0);
    if (!block) continue;
    if (previousConnection && block.previousConnection) safeConnect(previousConnection, block.previousConnection, errors);
    previousConnection = block.nextConnection ?? null;
  }
}

function blockStatements(node: any): any[] {
  if (!node) return [];
  if (node.type === 'BlockStatement') return node.body ?? [];
  return [node];
}

function isModuleDeclarationNode(node: any): boolean {
  const declaration = node?.declarations?.[0];
  return node?.type === 'VariableDeclaration'
    && declaration?.id?.type === 'Identifier'
    && declaration?.init?.type === 'CallExpression'
    && nodeSource(declaration.init.callee) === 'emaki.module';
}

function createCoreCallWorkspaceBlock(workspace: Blockly.WorkspaceSvg, node: any, x: number, y: number, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[], outputOnly: boolean): Blockly.BlockSvg | null {
  const callee = nodeSource(node?.callee);
  const args = node?.arguments ?? [];
  const connectArg = (block: Blockly.BlockSvg, name: string, index: number) => connectValue(block, name, expressionToBlock(workspace, args[index], catalog, aliases, errors));
  if (!outputOnly && callee === 'emaki.player.sendMessage') {
    const block = createBlock(workspace, 'emaki_player_send_message', x, y);
    connectArg(block, 'MESSAGE', 0);
    return block;
  }
  if (!outputOnly && callee === 'emaki.logger.info') {
    const block = createBlock(workspace, 'emaki_logger_info', x, y);
    connectArg(block, 'MESSAGE', 0);
    return block;
  }
  if (!outputOnly && callee === 'emaki.logger.warn') {
    const block = createBlock(workspace, 'emaki_logger_warn', x, y);
    connectArg(block, 'MESSAGE', 0);
    return block;
  }
  if (!outputOnly && callee === 'emaki.logger.error') {
    const block = createBlock(workspace, 'emaki_logger_error', x, y);
    connectArg(block, 'MESSAGE', 0);
    return block;
  }
  if (outputOnly && callee === 'emaki.context.placeholder') {
    const block = createBlock(workspace, 'emaki_context_placeholder', x, y);
    connectArg(block, 'KEY', 0);
    return block;
  }
  if (outputOnly && callee === 'emaki.player.exists') return createBlock(workspace, 'emaki_player_exists', x, y);
  return null;
}

function createRegisteredWorkspaceBlock(workspace: Blockly.WorkspaceSvg, match: ParsedBlock & { argumentNodes?: any[]; argumentValues?: string[] }, x: number, y: number, catalog: ScriptBlockCatalog, aliases: Map<string, string>, errors: string[]): Blockly.BlockSvg {
  const block = createBlock(workspace, match.typeName, x, y);
  for (let index = 0; index < match.params.length; index++) {
    const node = match.argumentNodes?.[index];
    const fallback = match.argumentValues?.[index] ?? defaultArgument(match.params[index]);
    connectValue(block, inputName(index), node ? expressionToBlock(workspace, node, catalog, aliases, errors) : rawExpressionBlock(workspace, fallback));
  }
  return block;
}

function createBlock(workspace: Blockly.WorkspaceSvg, type: string, x: number, y: number): Blockly.BlockSvg {
  const block = workspace.newBlock(type) as Blockly.BlockSvg;
  block.initSvg();
  block.render();
  block.moveBy(x - block.getRelativeToSurfaceXY().x, y - block.getRelativeToSurfaceXY().y);
  return block;
}

function connectValue(block: Blockly.BlockSvg, inputNameValue: string, valueBlock: Blockly.BlockSvg): void {
  const connection = block.getInput(inputNameValue)?.connection;
  if (connection && valueBlock.outputConnection) safeConnect(connection, valueBlock.outputConnection, []);
}

function safeConnect(parent: Blockly.Connection, child: Blockly.Connection, errors: string[]): void {
  try {
    parent.connect(child);
  } catch (exception) {
    errors.push(exception instanceof Error ? exception.message : String(exception));
  }
}

function toolboxBlock(block: WebScriptBlockDefinition): Blockly.utils.toolbox.BlockInfo {
  const parsed = parseRegisteredBlock(block);
  const inputs = parsed.params.reduce<Record<string, unknown>>((result, param, index) => {
    result[inputName(index)] = { shadow: defaultShadowForParam(param) };
    return result;
  }, {});
  const info: Blockly.utils.toolbox.BlockInfo = { kind: 'block', type: parsed.typeName };
  if (Object.keys(inputs).length) (info as { inputs?: Record<string, unknown> }).inputs = inputs;
  return info;
}

function parseRegisteredBlock(block: WebScriptBlockDefinition): ParsedBlock {
  const typeKey = normalizeId(`${normalizeModuleId(block.moduleId) ?? 'global'}:${block.id}`).replace(/[^a-z0-9_]+/g, '_');
  const typeName = `${BLOCK_PREFIX}${typeKey}`;
  const call = parseCall(block.codeTemplate) || parseCall(block.callPattern || '') || parseCall(`${block.scope}.${block.label}`);
  const kind = String(block.type ?? '').toLowerCase();
  const output = ['value', 'variable', 'property', 'getter', 'status', 'boolean', 'number', 'string', 'expression'].includes(kind);
  return {
    block,
    typeName,
    methodName: call?.methodName || normalizeMethodName(block.label),
    receiver: call?.receiver || normalizeScope(block.scope),
    moduleId: moduleIdForBlock(block, call?.receiver),
    params: call?.params ?? [],
    output
  };
}

function blockJsonDefinition(parsed: ParsedBlock): Record<string, unknown> {
  const params = parsed.params;
  const definition: Record<string, unknown> = {
    type: parsed.typeName,
    message0: params.length ? apiBlockLabel(parsed) : `${apiBlockLabel(parsed)} %1`,
    args0: params.length ? [] : [{ type: 'input_dummy' }],
    colour: '#0369a1',
    tooltip: parsed.block.comment || parsed.block.codeTemplate,
    ...(parsed.output ? { output: outputCheck(parsed.block.type) } : { previousStatement: null, nextStatement: null })
  };
  params.forEach((param, index) => {
    definition[`message${index + 1}`] = `${paramLabel(param, index)} %1`;
    definition[`args${index + 1}`] = [{ type: 'input_value', name: inputName(index), check: checkForParam(param) }];
  });
  return definition;
}

function apiBlockLabel(parsed: ParsedBlock): string {
  const friendly = friendlyMethodLabel(parsed);
  if (friendly) return friendly;
  if (parsed.moduleId) return `${parsed.moduleId} · ${humanizeMethodName(parsed.methodName)}`;
  const receiver = parsed.receiver && parsed.receiver !== 'global' ? `${parsed.receiver}.` : '';
  return `${receiver}${humanizeMethodName(parsed.methodName)}`;
}

function friendlyMethodLabel(parsed: ParsedBlock): string | null {
  const moduleName = parsed.moduleId || (parsed.receiver.startsWith('module:') ? parsed.receiver.slice('module:'.length) : '');
  const prefix = moduleName ? `${moduleName} · ` : '';
  const label = ({
    ready: t('core.script.block.api.ready'),
    available: t('core.script.block.api.available'),
    pluginName: t('core.script.block.api.pluginName'),
    apiVersion: t('core.script.block.api.apiVersion')
  } as Record<string, string | undefined>)[parsed.methodName];
  return label ? `${prefix}${label}` : null;
}

function humanizeMethodName(methodName: string): string {
  if (!methodName) return t('core.script.block.callStatement');
  const spaced = methodName.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_.:-]+/g, ' ').trim();
  if (!spaced) return methodName;
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function generateRegisteredBlock(block: Blockly.Block, generator: typeof javascriptGenerator, parsed: ParsedBlock): string | [string, number] {
  const args = parsed.params.map((_, index) => generator.valueToCode(block, inputName(index), Order.NONE) || defaultArgument(parsed.params[index]));
  const receiver = receiverExpression(parsed);
  const call = receiver ? `${receiver}.${parsed.methodName}(${args.join(', ')})` : `${parsed.methodName}(${args.join(', ')})`;
  return parsed.output ? [call, Order.FUNCTION_CALL] : `${call};\n`;
}

function isCoveredByCommonApiBlock(block: WebScriptBlockDefinition): boolean {
  const parsed = parseRegisteredBlock(block);
  const receiver = receiverExpression(parsed);
  const fullName = receiver ? `${receiver}.${parsed.methodName}` : parsed.methodName;
  return ['emaki.player.sendMessage', 'emaki.logger.info', 'emaki.logger.warn', 'emaki.logger.error', 'emaki.context.placeholder', 'emaki.player.exists'].includes(fullName);
}

function findRegisteredCall(node: any, catalog: ScriptBlockCatalog, aliases: Map<string, string>, outputOnly: boolean): (ParsedBlock & { argumentNodes: any[] }) | null {
  if (!node || node.type !== 'CallExpression') return null;
  const callee = parseCallee(node.callee);
  if (!callee) return null;
  for (const block of catalog.blocks) {
    if (block.id.startsWith('core.')) continue;
    const parsed = parseRegisteredBlock(block);
    if (outputOnly && !parsed.output) continue;
    if (!outputOnly && parsed.output) continue;
    if (parsed.methodName !== callee.methodName) continue;
    const callModule = aliases.get(callee.receiver) ?? undefined;
    if (parsed.moduleId) {
      if (callModule !== parsed.moduleId) continue;
    } else if (parsed.receiver && parsed.receiver !== 'global' && parsed.receiver !== callee.receiver) {
      continue;
    }
    return { ...parsed, argumentNodes: node.arguments ?? [] };
  }
  return null;
}

function parseCallee(callee: any): { receiver: string; methodName: string } | null {
  if (callee?.type === 'Identifier') return { receiver: '', methodName: callee.name };
  if (callee?.type === 'MemberExpression') return { receiver: nodeSource(callee.object), methodName: callee.computed ? nodeSource(callee.property) : callee.property?.name };
  return null;
}

function receiverExpression(parsed: ParsedBlock): string {
  if (parsed.moduleId) return moduleAlias(parsed.moduleId);
  const receiver = parsed.receiver.trim();
  if (!receiver || receiver === 'global') return '';
  return receiver;
}

function moduleAlias(moduleId: string): string {
  const normalized = moduleId.trim().toLowerCase();
  const alias = safeAlias(normalized);
  activeModuleUsages?.set(normalized, { moduleId: normalized, alias });
  return alias;
}

function parseCall(template: string): { receiver: string; methodName: string; params: string[] } | null {
  const cleaned = String(template ?? '').replace(/\b(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*emaki\.module\([^)]*\)\s*;?/g, '').trim();
  const match = /([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\.([A-Za-z_$][\w$]*)\s*\(([^)]*)\)/.exec(cleaned)
    || /^([A-Za-z_$][\w$]*)\s*\(([^)]*)\)/.exec(cleaned);
  if (!match) return null;
  if (match.length === 4) return { receiver: match[1], methodName: match[2], params: splitParams(match[3]) };
  return { receiver: '', methodName: match[1], params: splitParams(match[2]) };
}

function splitParams(params: string): string[] {
  return params.split(',').map(param => param.trim()).filter(Boolean).map(param => param.replace(/^['"]|['"]$/g, '') || 'value');
}

function collectModuleAliases(source: string): Map<string, string> {
  const aliases = new Map<string, string>();
  const pattern = /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*emaki\.module\(\s*['"]([^'"]+)['"]\s*\)\s*;?/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(source))) aliases.set(match[1], match[2].trim().toLowerCase());
  return aliases;
}

function moduleIdForBlock(block: WebScriptBlockDefinition, receiver?: string): string | undefined {
  const scope = normalizeScope(block.scope);
  if (scope.startsWith('module:')) return scope.slice('module:'.length).trim().toLowerCase();
  if (receiver && receiver !== scope && block.moduleId && scope !== 'global') return block.moduleId.trim().toLowerCase();
  return undefined;
}

function defaultArgument(param: string): string {
  const normalized = param.toLowerCase();
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price|value|min|max/.test(normalized)) return '0';
  if (/enabled|visible|active|flag|bool|global|ready|available/.test(normalized)) return 'false';
  if (/player/.test(normalized)) return 'player';
  if (/definition|object|args|arguments|meta|context|ctx|options|data|payload|source/.test(normalized)) return '{}';
  if (/player|target|caster|entity|item|inventory|world/.test(normalized)) return normalized.replace(/[^a-zA-Z0-9_$]/g, '') || 'value';
  return 'undefined';
}

function defaultShadowForParam(param: string): Record<string, unknown> {
  const normalized = param.toLowerCase();
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price|min|max|damage|power/.test(normalized)) return { type: 'math_number', fields: { NUM: 0 } };
  if (/enabled|visible|active|flag|bool|global|ready|available|exists|success/.test(normalized)) return { type: 'logic_boolean', fields: { BOOL: 'FALSE' } };
  if (/definition|object|args|arguments|options|data|payload|source/.test(normalized)) return { type: 'emaki_object_literal', fields: { CODE: '{}' } };
  if (/list|array|values/.test(normalized)) return { type: 'emaki_array_literal', fields: { CODE: '[]' } };
  if (/player|target|caster|entity|item|inventory|world|meta|context|ctx/.test(normalized)) return { type: 'emaki_expression_value', fields: { CODE: normalized.replace(/[^a-zA-Z0-9_$]/g, '') || 'value' } };
  return { type: 'text', fields: { TEXT: '' } };
}

function shadowedBlock(type: string, fieldName: string, value: string): Blockly.utils.toolbox.BlockInfo {
  return { kind: 'block', type, fields: { [fieldName]: value } };
}

function variableToolboxBlock(type: 'variables_get' | 'variables_set'): Blockly.utils.toolbox.BlockInfo {
  return { kind: 'block', type, fields: { VAR: { name: 'value' } } };
}

function layoutTopBlocks(workspace: Blockly.WorkspaceSvg): void {
  let y = 24;
  for (const block of (workspace.getTopBlocks(true) as Blockly.BlockSvg[]).filter(isScriptTopBlock)) {
    const position = block.getRelativeToSurfaceXY();
    block.moveBy(24 - position.x, y - position.y);
    block.render();
    y += Math.max(112, block.getHeightWidth().height + 36);
  }
}

function isUserWorkspaceBlock(block: Blockly.Block): boolean {
  return typeof block.isShadow !== 'function' || !block.isShadow();
}

function isScriptTopBlock(block: Blockly.Block): boolean {
  return isUserWorkspaceBlock(block) && !isDetachedValueBlock(block);
}

function isDetachedValueBlock(block: Blockly.Block): boolean {
  return isUserWorkspaceBlock(block) && !block.getParent() && Boolean(block.outputConnection) && !block.previousConnection && !block.nextConnection;
}

function blockToScriptCode(block: Blockly.Block): string {
  const generated = javascriptGenerator.blockToCode(block);
  const code = Array.isArray(generated) ? generated[0] : generated;
  return String(code || '').trimEnd();
}

function stripDuplicateModuleDeclarations(source: string): string {
  const seen = new Set<string>();
  return source.split(/\r?\n/).filter(line => {
    const match = /^\s*(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*emaki\.module\(\s*['"]([^'"]+)['"]\s*\)\s*;?\s*$/.exec(line);
    if (!match) return true;
    const moduleId = match[1].trim().toLowerCase();
    if (seen.has(moduleId) || activeModuleUsages?.has(moduleId)) return false;
    seen.add(moduleId);
    return true;
  }).join('\n').replace(/\n{3,}/g, '\n\n');
}

function nodeSource(node: any): string {
  if (!node) return 'undefined';
  switch (node.type) {
    case 'Identifier': return node.name;
    case 'Literal': return typeof node.value === 'string' ? JSON.stringify(node.value) : String(node.value);
    case 'MemberExpression': return `${nodeSource(node.object)}${node.computed ? `[${nodeSource(node.property)}]` : `.${nodeSource(node.property)}`}`;
    case 'CallExpression': return `${nodeSource(node.callee)}(${(node.arguments ?? []).map(nodeSource).join(', ')})`;
    case 'ObjectExpression': return `{ ${(node.properties ?? []).map((property: any) => `${nodeSource(property.key)}: ${nodeSource(property.value)}`).join(', ')} }`;
    case 'ArrayExpression': return `[${(node.elements ?? []).map(nodeSource).join(', ')}]`;
    case 'BinaryExpression':
    case 'LogicalExpression': return `${nodeSource(node.left)} ${node.operator} ${nodeSource(node.right)}`;
    case 'UnaryExpression': return node.operator === 'typeof' ? `typeof ${nodeSource(node.argument)}` : `${node.operator}${nodeSource(node.argument)}`;
    case 'ConditionalExpression': return `${nodeSource(node.test)} ? ${nodeSource(node.consequent)} : ${nodeSource(node.alternate)}`;
    default: return 'undefined';
  }
}

function categoryKey(category: Pick<WebScriptBlockCategory, 'moduleId' | 'id'>): string {
  return `${normalizeModuleId(category.moduleId) ?? ''}:${normalizeId(category.id)}`;
}

function blockKey(block: Pick<WebScriptBlockDefinition, 'moduleId' | 'id'>): string {
  return `${normalizeModuleId(block.moduleId) ?? ''}:${normalizeId(block.id)}`;
}

function resolveRegisteredCategoryId(categoriesById: Map<string, WebScriptBlockCategory>, moduleId: string | undefined, requestedCategoryId: string): string {
  if (requestedCategoryId && categoriesById.has(`${moduleId ?? ''}:${requestedCategoryId}`)) return requestedCategoryId;
  if (requestedCategoryId && categoriesById.has(`:${requestedCategoryId}`)) return requestedCategoryId;
  return API_CATEGORY_ID;
}

function inputName(index: number): string {
  return `ARG${index}`;
}

function paramLabel(param: string, index: number): string {
  const text = String(param ?? '').replace(/[^\p{L}\p{N}_.$:-]+/gu, '').trim();
  return text || `arg${index + 1}`;
}

function checkForParam(param: string): string | null {
  const normalized = param.toLowerCase();
  if (/message|text|name|id|key|path|command|title|label|type|function/.test(normalized)) return 'String';
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price|min|max|damage|power/.test(normalized)) return 'Number';
  if (/enabled|visible|active|flag|bool|global|ready|available|exists|success/.test(normalized)) return 'Boolean';
  return null;
}

function outputCheck(type: string | undefined): string | null {
  const normalized = String(type ?? '').toLowerCase();
  if (normalized.includes('string')) return 'String';
  if (normalized.includes('number')) return 'Number';
  if (normalized.includes('boolean') || normalized.includes('status')) return 'Boolean';
  return null;
}

function normalizeMethodName(label: string): string {
  const match = /([A-Za-z_$][\w$]*)\s*\(/.exec(label) || /([A-Za-z_$][\w$]*)/.exec(label);
  return match?.[1] || 'call';
}

function safeAlias(moduleId: string): string {
  const base = moduleId.replace(/[^a-zA-Z0-9_$]/g, '') || 'module';
  return /^[0-9]/.test(base) ? `module${base}` : base;
}

function safeIdentifier(value: unknown, fallback: string): string {
  const text = String(value ?? '').trim();
  return /^[A-Za-z_$][\w$]*$/.test(text) ? text : fallback;
}

function sanitizeParameterList(value: unknown): string {
  return String(value ?? '')
    .split(',')
    .map(param => safeIdentifier(param, ''))
    .filter(Boolean)
    .join(', ');
}

function normalizeId(id: string | undefined): string {
  return String(id ?? '').trim().toLowerCase().replace(/[^a-z0-9_.:-]+/g, '-');
}

function normalizeScope(scope: string | undefined): string {
  const text = String(scope ?? '').trim();
  return text.startsWith('module:') ? `module:${text.slice('module:'.length).trim().toLowerCase()}` : text;
}

function normalizeModuleId(moduleId: string | undefined): string | undefined {
  const text = String(moduleId ?? '').trim();
  return text ? text : undefined;
}

function safeOrder(order: unknown): number | undefined {
  const value = Number(order);
  return Number.isFinite(value) ? value : undefined;
}

function sortByOrderAndLabel(left: { order?: number; label: string }, right: { order?: number; label: string }): number {
  return (left.order ?? 0) - (right.order ?? 0) || left.label.localeCompare(right.label);
}
