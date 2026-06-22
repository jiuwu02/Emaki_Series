import * as Blockly from 'blockly/core';
import { javascriptGenerator, Order } from 'blockly/javascript';
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
  statement: boolean;
  rawTemplate?: string;
};

type ModuleUsage = {
  moduleId: string;
  alias: string;
};

const CORE_CATEGORY_ID = 'core.entry';
const RAW_CATEGORY_ID = 'core.raw';
const BLOCK_PREFIX = 'emaki_script_';
const generatedBlocks = new Map<string, ParsedBlock>();
let activeModuleUsages: Map<string, ModuleUsage> | null = null;

const BUILTIN_CATEGORIES: WebScriptBlockCategory[] = [
  { id: CORE_CATEGORY_ID, label: t('core.script.block.entry.category'), comment: t('core.script.block.entry.categoryComment'), order: -200 },
  { id: RAW_CATEGORY_ID, label: t('core.script.block.raw.category'), comment: t('core.script.block.raw.categoryComment'), order: 9999 }
];

const BUILTIN_BLOCK_DEFINITIONS: WebScriptBlockDefinition[] = [
  {
    id: 'core.entry.script',
    categoryId: CORE_CATEGORY_ID,
    scope: 'core',
    label: t('core.script.block.entry'),
    comment: t('core.script.block.entry.tooltip'),
    codeTemplate: 'function main(ctx) {\n}',
    type: 'control',
    order: -200
  },
  {
    id: 'core.return.value',
    categoryId: CORE_CATEGORY_ID,
    scope: 'core',
    label: t('core.script.block.return'),
    comment: t('core.script.block.return.tooltip'),
    codeTemplate: 'return value;',
    type: 'return',
    order: -100
  },
  {
    id: 'core.raw.statement',
    categoryId: RAW_CATEGORY_ID,
    scope: 'core',
    label: t('core.script.block.rawStatement'),
    comment: t('core.script.block.rawStatement.tooltip'),
    codeTemplate: '/* JavaScript */',
    type: 'statement',
    order: 0
  },
  {
    id: 'core.raw.value',
    categoryId: RAW_CATEGORY_ID,
    scope: 'core',
    label: t('core.script.block.rawValue'),
    comment: t('core.script.block.rawValue.tooltip'),
    codeTemplate: 'value',
    type: 'value',
    order: 10
  }
];

export function normalizeScriptBlockCatalog(categories: WebScriptBlockCategory[] = [], blocks: WebScriptBlockDefinition[] = []): ScriptBlockCatalog {
  const categoriesById = new Map<string, WebScriptBlockCategory>();
  const blocksById = new Map<string, WebScriptBlockDefinition>();

  for (const category of [...BUILTIN_CATEGORIES, ...categories]) {
    const id = normalizeId(category.id);
    if (!id || !category.label) continue;
    categoriesById.set(id, { ...category, id, order: safeOrder(category.order) });
  }

  for (const block of [...BUILTIN_BLOCK_DEFINITIONS, ...blocks]) {
    const id = normalizeId(block.id);
    const categoryId = normalizeId(block.categoryId);
    if (!id || !categoryId || !block.scope || !block.label || !block.codeTemplate) continue;
    const next: WebScriptBlockDefinition = {
      ...block,
      id,
      categoryId,
      scope: normalizeScope(block.scope),
      callPattern: String(block.callPattern || '').trim(),
      type: String(block.type || 'function').trim().toLowerCase(),
      order: safeOrder(block.order)
    };
    blocksById.set(id, next);
    if (!categoriesById.has(categoryId)) {
      categoriesById.set(categoryId, {
        id: categoryId,
        moduleId: block.moduleId,
        label: categoryId.replace(/[_.:-]+/g, ' '),
        order: 9000
      });
    }
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

  if (blockJson.length) {
    Blockly.common.defineBlocksWithJsonArray(blockJson);
  }

  for (const [typeName, parsed] of generatedBlocks) {
    javascriptGenerator.forBlock[typeName] = (block, generator) => generateRegisteredBlock(block, generator, parsed);
  }
}

export function createScriptBlocklyToolbox(catalog: ScriptBlockCatalog): Blockly.utils.toolbox.ToolboxDefinition {
  const blocksByCategory = new Map<string, WebScriptBlockDefinition[]>();
  for (const block of catalog.blocks) {
    const list = blocksByCategory.get(block.categoryId) ?? [];
    list.push(block);
    blocksByCategory.set(block.categoryId, list);
  }

  const customCategories = catalog.categories.flatMap(category => {
    const categoryBlocks = (blocksByCategory.get(category.id) ?? []).sort(sortByOrderAndLabel);
    if (!categoryBlocks.length) return [];
    return [{
      kind: 'category',
      name: category.label,
      colour: toolboxColour(category.id),
      contents: categoryBlocks.map(block => toolboxBlock(block))
    }];
  });

  return {
    kind: 'categoryToolbox',
    contents: [
      ...customCategories,
      {
        kind: 'category',
        name: t('core.script.block.logic.category'),
        colour: '#1d4ed8',
        contents: [
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
        name: t('core.script.block.variable.category'),
        colour: '#7c3aed',
        custom: 'VARIABLE'
      }
    ]
  };
}

export function generateScriptFromWorkspace(workspace: Blockly.WorkspaceSvg): string {
  activeModuleUsages = new Map();
  try {
    let code = javascriptGenerator.workspaceToCode(workspace).replace(/\n{3,}/g, '\n\n').trimEnd();
    const declarations = [...activeModuleUsages.values()]
      .sort((left, right) => left.alias.localeCompare(right.alias))
      .map(usage => `const ${usage.alias} = emaki.module(${JSON.stringify(usage.moduleId)});`);
    if (declarations.length) {
      code = `${declarations.join('\n')}\n\n${stripDuplicateModuleDeclarations(code)}`.trimEnd();
    }
    return `${code}\n`;
  } finally {
    activeModuleUsages = null;
  }
}

export function scriptBlockCount(workspace: Blockly.WorkspaceSvg | null): number {
  return workspace?.getAllBlocks(false).length ?? 0;
}

export type ScriptSourceImportResult = {
  importedBlocks: number;
  rawBlocks: number;
  errors: string[];
};

export function loadScriptSourceIntoWorkspace(workspace: Blockly.WorkspaceSvg, source: string, catalog: ScriptBlockCatalog): ScriptSourceImportResult {
  workspace.clear();
  const text = String(source ?? '').trim();
  if (!text) return { importedBlocks: 0, rawBlocks: 0, errors: [] };

  const aliases = collectModuleAliases(text);
  const statements = extractMainFunctionBody(text)
    ? splitTopLevelStatements(extractMainFunctionBody(text) ?? '')
    : splitTopLevelStatements(text);
  const hasMain = extractMainFunctionBody(text) !== null;
  const errors: string[] = [];
  let rawBlocks = 0;
  let importedBlocks = 0;

  const entry = hasMain ? createBlock(workspace, 'emaki_script_entry', 24, 24) : null;
  if (entry) importedBlocks++;
  let previousConnection = entry?.getInput('BODY')?.connection ?? null;
  let topY = hasMain ? 140 : 24;

  for (const statement of statements) {
    const normalized = statement.trim();
    if (!normalized || isModuleDeclaration(normalized)) continue;
    const block = createStatementBlock(workspace, normalized, catalog, aliases, hasMain ? 0 : 24, topY);
    if (!block) continue;
    importedBlocks++;
    if (block.type === 'emaki_raw_statement') rawBlocks++;
    if (previousConnection && block.previousConnection) {
      try {
        previousConnection.connect(block.previousConnection);
      } catch (exception) {
        errors.push(exception instanceof Error ? exception.message : String(exception));
      }
    } else if (!hasMain) {
      block.moveBy(0, topY - block.getRelativeToSurfaceXY().y);
      topY += 72;
    }
    previousConnection = block.nextConnection ?? null;
  }

  for (const block of workspace.getAllBlocks(false)) {
    block.initSvg();
    block.render();
  }
  workspace.cleanUp();
  return { importedBlocks, rawBlocks, errors };
}

function createStatementBlock(workspace: Blockly.WorkspaceSvg, statement: string, catalog: ScriptBlockCatalog, aliases: Map<string, string>, x: number, y: number): Blockly.BlockSvg | null {
  const returnMatch = /^return\s+([\s\S]*?);?$/.exec(statement);
  if (returnMatch) {
    const block = createBlock(workspace, 'emaki_return_value', x, y);
    connectValue(block, 'VALUE', createValueBlock(workspace, returnMatch[1], catalog, aliases));
    return block;
  }

  const parsed = findRegisteredCall(statement.replace(/;\s*$/, ''), catalog, aliases, false);
  if (parsed) return createRegisteredWorkspaceBlock(workspace, parsed, x, y, catalog, aliases);

  const raw = createBlock(workspace, 'emaki_raw_statement', x, y);
  raw.setFieldValue(stripTrailingSemicolon(statement), 'CODE');
  return raw;
}

function createValueBlock(workspace: Blockly.WorkspaceSvg, expression: string, catalog: ScriptBlockCatalog, aliases: Map<string, string>): Blockly.BlockSvg {
  const value = String(expression ?? '').trim();
  const stringMatch = /^(['"])([\s\S]*)\1$/.exec(value);
  if (stringMatch) {
    const block = createBlock(workspace, 'text', 0, 0);
    block.setFieldValue(unescapeStringLiteral(stringMatch[2]), 'TEXT');
    return block;
  }
  if (/^-?\d+(?:\.\d+)?$/.test(value)) {
    const block = createBlock(workspace, 'math_number', 0, 0);
    block.setFieldValue(value, 'NUM');
    return block;
  }
  if (value === 'true' || value === 'false') {
    const block = createBlock(workspace, 'logic_boolean', 0, 0);
    block.setFieldValue(value.toUpperCase(), 'BOOL');
    return block;
  }
  if (value === 'null') return createBlock(workspace, 'logic_null', 0, 0);

  const parsed = findRegisteredCall(value, catalog, aliases, true);
  if (parsed) return createRegisteredWorkspaceBlock(workspace, parsed, 0, 0, catalog, aliases);

  const raw = createBlock(workspace, 'emaki_raw_value', 0, 0);
  raw.setFieldValue(value || 'undefined', 'CODE');
  return raw;
}

function createRegisteredWorkspaceBlock(
  workspace: Blockly.WorkspaceSvg,
  match: ParsedBlock & { argumentValues?: string[] },
  x: number,
  y: number,
  catalog: ScriptBlockCatalog,
  aliases: Map<string, string>
): Blockly.BlockSvg {
  const block = createBlock(workspace, match.typeName, x, y);
  const values = match.argumentValues ?? [];
  for (let index = 0; index < match.params.length; index++) {
    connectValue(block, inputName(index), createValueBlock(workspace, values[index] ?? defaultArgument(match.params[index]), catalog, aliases));
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
  if (connection && valueBlock.outputConnection) connection.connect(valueBlock.outputConnection);
}

function findRegisteredCall(expression: string, catalog: ScriptBlockCatalog, aliases: Map<string, string>, outputOnly: boolean): (ParsedBlock & { argumentValues: string[] }) | null {
  const call = parseCallExpression(expression);
  if (!call) return null;
  for (const block of catalog.blocks) {
    if (block.id.startsWith('core.')) continue;
    const parsed = parseRegisteredBlock(block);
    if (outputOnly && !parsed.output) continue;
    if (!outputOnly && parsed.output) continue;
    if (parsed.methodName !== call.methodName) continue;
    const callModule = aliases.get(call.receiver) ?? undefined;
    if (parsed.moduleId) {
      if (callModule !== parsed.moduleId) continue;
    } else if (parsed.receiver && parsed.receiver !== 'global' && parsed.receiver !== call.receiver) {
      continue;
    }
    return { ...parsed, argumentValues: call.args };
  }
  return null;
}

function parseCallExpression(expression: string): { receiver: string; methodName: string; args: string[] } | null {
  const text = String(expression ?? '').trim().replace(/;\s*$/, '');
  const open = text.indexOf('(');
  const close = text.lastIndexOf(')');
  if (open <= 0 || close < open) return null;
  const callee = text.slice(0, open).trim();
  if (!/^[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$/.test(callee)) return null;
  const dot = callee.lastIndexOf('.');
  const receiver = dot >= 0 ? callee.slice(0, dot) : '';
  const methodName = dot >= 0 ? callee.slice(dot + 1) : callee;
  return { receiver, methodName, args: splitArguments(text.slice(open + 1, close)) };
}

function collectModuleAliases(source: string): Map<string, string> {
  const aliases = new Map<string, string>();
  const pattern = /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*emaki\.module\(\s*['"]([^'"]+)['"]\s*\)\s*;?/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(source))) {
    aliases.set(match[1], match[2].trim().toLowerCase());
  }
  return aliases;
}

function isModuleDeclaration(statement: string): boolean {
  return /^\s*(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*emaki\.module\(/.test(statement);
}

function extractMainFunctionBody(source: string): string | null {
  const match = /function\s+main\s*\([^)]*\)\s*\{/.exec(source);
  if (!match) return null;
  const open = match.index + match[0].lastIndexOf('{');
  const close = findMatchingBrace(source, open);
  return close > open ? source.slice(open + 1, close) : null;
}

function findMatchingBrace(source: string, open: number): number {
  let depth = 0;
  let quote = '';
  let escaped = false;
  for (let index = open; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === quote) {
        quote = '';
      }
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '{') depth++;
    if (char === '}') {
      depth--;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function splitTopLevelStatements(source: string): string[] {
  const statements: string[] = [];
  let start = 0;
  let quote = '';
  let escaped = false;
  let parenDepth = 0;
  let braceDepth = 0;
  for (let index = 0; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === quote) {
        quote = '';
      }
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(') parenDepth++;
    if (char === ')') parenDepth = Math.max(0, parenDepth - 1);
    if (char === '{') braceDepth++;
    if (char === '}') braceDepth = Math.max(0, braceDepth - 1);
    const atStatementEnd = char === ';' && parenDepth === 0 && braceDepth === 0;
    const atBlockEnd = char === '}' && parenDepth === 0 && braceDepth === 0;
    if (atStatementEnd || atBlockEnd) {
      const statement = source.slice(start, index + 1).trim();
      if (statement) statements.push(statement);
      start = index + 1;
    }
  }
  const rest = source.slice(start).trim();
  if (rest) statements.push(rest);
  return statements;
}

function splitArguments(args: string): string[] {
  const result: string[] = [];
  let start = 0;
  let quote = '';
  let escaped = false;
  let depth = 0;
  for (let index = 0; index < args.length; index++) {
    const char = args[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = '';
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(' || char === '[' || char === '{') depth++;
    if (char === ')' || char === ']' || char === '}') depth = Math.max(0, depth - 1);
    if (char === ',' && depth === 0) {
      result.push(args.slice(start, index).trim());
      start = index + 1;
    }
  }
  const rest = args.slice(start).trim();
  if (rest) result.push(rest);
  return result;
}

function stripTrailingSemicolon(statement: string): string {
  return statement.trim().replace(/;\s*$/, '');
}

function unescapeStringLiteral(value: string): string {
  return value.replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\t/g, '\t').replace(/\\(['"\\])/g, '$1');
}

function registerCoreBlocks(): void {
  const coreDefinitions = [
    {
      type: 'emaki_script_entry',
      message0: `${t('core.script.block.entry')} %1 %2`,
      args0: [
        { type: 'input_dummy' },
        { type: 'input_statement', name: 'BODY' }
      ],
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
      type: 'emaki_raw_statement',
      message0: `${t('core.script.block.rawStatement')} %1`,
      args0: [{ type: 'field_input', name: 'CODE', text: '/* JavaScript */' }],
      previousStatement: null,
      nextStatement: null,
      colour: '#92400e',
      tooltip: t('core.script.block.rawStatement.tooltip')
    },
    {
      type: 'emaki_raw_value',
      message0: `${t('core.script.block.rawValue')} %1`,
      args0: [{ type: 'field_input', name: 'CODE', text: 'value' }],
      output: null,
      colour: '#a16207',
      tooltip: t('core.script.block.rawValue.tooltip')
    }
  ].filter(definition => !Blockly.Blocks[definition.type]);

  if (coreDefinitions.length) {
    Blockly.common.defineBlocksWithJsonArray(coreDefinitions);
  }

  javascriptGenerator.forBlock.emaki_script_entry = (block, generator) => {
    const body = generator.statementToCode(block, 'BODY');
    return body ? `function main(ctx) {\n${body}}\n` : 'function main(ctx) {\n}\n';
  };
  javascriptGenerator.forBlock.emaki_return_value = (block, generator) => {
    const value = generator.valueToCode(block, 'VALUE', Order.NONE) || 'undefined';
    return `return ${value};\n`;
  };
  javascriptGenerator.forBlock.emaki_raw_statement = block => {
    const code = String(block.getFieldValue('CODE') ?? '').trim();
    if (!code) return '';
    return /[;{}]\s*$/.test(code) ? `${code}\n` : `${code};\n`;
  };
  javascriptGenerator.forBlock.emaki_raw_value = block => [String(block.getFieldValue('CODE') ?? 'undefined').trim() || 'undefined', Order.ATOMIC];
}

function toolboxBlock(block: WebScriptBlockDefinition): Blockly.utils.toolbox.BlockInfo {
  const parsed = block.id.startsWith('core.') ? null : parseRegisteredBlock(block);
  const type = coreBlockType(block.id) || parsed?.typeName || `${BLOCK_PREFIX}${normalizeId(block.id)}`;
  const inputs = parsed?.params.reduce<Record<string, unknown>>((result, param, index) => {
    result[inputName(index)] = { shadow: defaultShadowForParam(param) };
    return result;
  }, {});
  if (!inputs || Object.keys(inputs).length === 0) return { kind: 'block', type };
  const info: Blockly.utils.toolbox.BlockInfo = { kind: 'block', type };
  (info as { inputs?: Record<string, unknown> }).inputs = inputs;
  return info;
}

function coreBlockType(id: string): string | null {
  if (id === 'core.entry.script') return 'emaki_script_entry';
  if (id === 'core.return.value') return 'emaki_return_value';
  if (id === 'core.raw.statement') return 'emaki_raw_statement';
  if (id === 'core.raw.value') return 'emaki_raw_value';
  return null;
}

function parseRegisteredBlock(block: WebScriptBlockDefinition): ParsedBlock {
  const typeName = `${BLOCK_PREFIX}${normalizeId(block.id).replace(/[^a-z0-9_]+/g, '_')}`;
  const call = parseCall(block.codeTemplate) || parseCall(block.callPattern || '') || parseCall(`${block.scope}.${block.label}`);
  const kind = String(block.type ?? '').toLowerCase();
  const output = ['value', 'variable', 'property', 'getter', 'status', 'boolean', 'number', 'string', 'expression'].includes(kind);
  const rawTemplate = call ? undefined : block.codeTemplate;
  return {
    block,
    typeName,
    methodName: call?.methodName || normalizeMethodName(block.label),
    receiver: call?.receiver || normalizeScope(block.scope),
    moduleId: moduleIdForBlock(block, call?.receiver),
    params: call?.params ?? [],
    output,
    statement: !output,
    rawTemplate
  };
}

function blockJsonDefinition(parsed: ParsedBlock): Record<string, unknown> {
  const params = parsed.params.length ? parsed.params : parsed.rawTemplate ? ['CODE'] : [];
  const args = params.map((param, index) => ({ type: 'input_value', name: inputName(index), check: checkForParam(param) }));
  const message = params.length
    ? `${parsed.block.label} ${params.map((param, index) => `${paramLabel(param, index)} %${index + 1}`).join(' ')}`
    : `${parsed.block.label} %1`;
  const args0 = params.length ? args : [{ type: 'input_dummy' }];
  return {
    type: parsed.typeName,
    message0: message,
    args0,
    colour: blockColour(parsed.block.categoryId),
    tooltip: parsed.block.comment || parsed.block.codeTemplate,
    ...(parsed.output ? { output: outputCheck(parsed.block.type) } : { previousStatement: null, nextStatement: null })
  };
}

function generateRegisteredBlock(block: Blockly.Block, generator: typeof javascriptGenerator, parsed: ParsedBlock): string | [string, number] {
  if (parsed.rawTemplate) {
    const raw = generator.valueToCode(block, inputName(0), Order.NONE) || JSON.stringify(parsed.rawTemplate);
    return parsed.output ? [raw, Order.ATOMIC] : `${raw};\n`;
  }

  const args = parsed.params.map((_, index) => generator.valueToCode(block, inputName(index), Order.NONE) || defaultArgument(parsed.params[index]));
  const receiver = receiverExpression(parsed);
  const call = receiver ? `${receiver}.${parsed.methodName}(${args.join(', ')})` : `${parsed.methodName}(${args.join(', ')})`;
  return parsed.output ? [call, Order.FUNCTION_CALL] : `${call};\n`;
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
  if (match.length === 4) {
    return { receiver: match[1], methodName: match[2], params: splitParams(match[3]) };
  }
  return { receiver: '', methodName: match[1], params: splitParams(match[2]) };
}

function splitParams(params: string): string[] {
  return params.split(',').map(param => param.trim()).filter(Boolean).map(param => param.replace(/^['"]|['"]$/g, '') || 'value');
}

function moduleIdForBlock(block: WebScriptBlockDefinition, receiver?: string): string | undefined {
  const scope = normalizeScope(block.scope);
  if (scope.startsWith('module:')) return scope.slice('module:'.length).trim().toLowerCase();
  if (receiver && receiver !== scope && block.moduleId && scope !== 'global') return block.moduleId.trim().toLowerCase();
  return undefined;
}

function defaultArgument(param: string): string {
  const normalized = param.toLowerCase();
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price|value/.test(normalized)) return '0';
  if (/enabled|visible|active|flag|bool|global/.test(normalized)) return 'false';
  if (/player/.test(normalized)) return 'player';
  return 'undefined';
}

function defaultShadowForParam(param: string): Record<string, unknown> {
  const normalized = param.toLowerCase();
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price/.test(normalized)) {
    return { type: 'math_number', fields: { NUM: 0 } };
  }
  if (/enabled|visible|active|flag|bool|global/.test(normalized)) {
    return { type: 'logic_boolean', fields: { BOOL: 'FALSE' } };
  }
  if (/player/.test(normalized)) {
    return { type: 'emaki_raw_value', fields: { CODE: 'player' } };
  }
  return { type: 'text', fields: { TEXT: '' } };
}

function shadowedBlock(type: string, fieldName: string, value: string): Blockly.utils.toolbox.BlockInfo {
  return { kind: 'block', type, fields: { [fieldName]: value } };
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

function blockColour(categoryId: string): string {
  return toolboxColour(categoryId);
}

function toolboxColour(categoryId: string): string {
  const hue = hueForCategory(categoryId);
  return `hsl(${hue} 72% 42%)`;
}

export function hueForCategory(categoryId: string | undefined): number {
  const normalized = String(categoryId ?? 'core').trim();
  let hash = 0;
  for (let index = 0; index < normalized.length; index++) {
    hash = (hash * 31 + normalized.charCodeAt(index)) % 360;
  }
  return (hash + 188) % 360;
}

function inputName(index: number): string {
  return `ARG${index}`;
}

function paramLabel(param: string, index: number): string {
  const text = String(param ?? '').replace(/[^\p{L}\p{N}_.$:-]+/gu, '').trim();
  return text || `参数${index + 1}`;
}

function checkForParam(param: string): string | null {
  const normalized = param.toLowerCase();
  if (/message|text|name|id|key|path|command|title|label|location|world|type/.test(normalized)) return 'String';
  if (/count|amount|number|size|time|tick|delay|level|index|x|y|z|price/.test(normalized)) return 'Number';
  if (/enabled|visible|active|flag|bool|global/.test(normalized)) return 'Boolean';
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

function normalizeId(id: string | undefined): string {
  return String(id ?? '').trim().toLowerCase().replace(/[^a-z0-9_.:-]+/g, '-');
}

function normalizeScope(scope: string | undefined): string {
  const text = String(scope ?? '').trim();
  return text.startsWith('module:') ? `module:${text.slice('module:'.length).trim().toLowerCase()}` : text;
}

function safeOrder(order: unknown): number | undefined {
  const value = Number(order);
  return Number.isFinite(value) ? value : undefined;
}

function sortByOrderAndLabel(left: { order?: number; label: string }, right: { order?: number; label: string }): number {
  return (left.order ?? 0) - (right.order ?? 0) || left.label.localeCompare(right.label);
}
