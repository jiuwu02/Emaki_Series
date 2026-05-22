/**
 * Item data manipulation utilities.
 */
import type { ItemPreviewEffect, ItemPreviewResult } from '../types';
import { textValue } from './miniMessage';

export type AnyMap = Record<string, unknown>;

export function asRecord(value: unknown): AnyMap {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as AnyMap : {};
}

export function asList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (value == null || value === '') return [];
  return [value];
}

export function asStringList(value: unknown): string[] {
  return asList(value).flatMap(stringListEntry).filter(entry => entry !== '');
}

function stringListEntry(value: unknown): string[] {
  if (Array.isArray(value)) return value.flatMap(stringListEntry);
  if (typeof value === 'string') return [value];
  if (value == null) return [];
  if (typeof value === 'number' || typeof value === 'boolean') return [String(value)];
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>;
    for (const key of ['text', 'value', 'content', 'line', 'display_name', 'display']) {
      const nested = record[key];
      if (typeof nested === 'string' || typeof nested === 'number' || typeof nested === 'boolean') return [String(nested)];
    }
    try {
      return [JSON.stringify(value)];
    } catch {
      return [];
    }
  }
  return [String(value)];
}

export function setDeepValue(source: AnyMap, path: string[], value: unknown): AnyMap {
  if (!path.length) return source;
  const [head, ...tail] = path;
  if (!tail.length) return cleanMap({ ...source, [head]: value });
  return cleanMap({ ...source, [head]: setDeepValue(asRecord(source[head]), tail, value) });
}

export function cleanMap(map: AnyMap): AnyMap {
  return Object.fromEntries(Object.entries(map).filter(([, value]) => value !== undefined)) as AnyMap;
}

export function itemKind(_path: string, preview?: ItemPreviewResult | null): string {
  return preview?.kind || 'generic_item';
}

export function firstItemSource(value: unknown): string {
  return asStringList(value)[0] ?? '';
}

export function materialFromItemSource(source: unknown): string {
  const text = String(source ?? '').trim();
  const match = text.match(/^(?:minecraft[-:])?([a-z0-9_]+)$/i);
  return match ? match[1] : text || 'stone';
}

export function displaySource(source: unknown): string {
  const text = String(source ?? '').trim();
  if (!text) return '未设置来源';
  if (text.startsWith('minecraft-')) return `minecraft:${text.slice('minecraft-'.length)}`;
  return text;
}

export function effectsByType(preview: ItemPreviewResult | null, type: string): ItemPreviewEffect[] {
  return (preview?.effects ?? []).filter((effect) => effect.type === type);
}

export function mapEntries(value: unknown): Array<{ key: string; value: unknown }> {
  return Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
}

export function variableEntries(preview: ItemPreviewResult | null): Array<{ key: string; value: unknown }> {
  return Object.entries(preview?.variables ?? {}).map(([key, value]) => ({ key, value }));
}

export function slotRows(value: unknown): Array<{ index: number; type: string; displayName: string; opened: boolean }> {
  const slots = asList(value);
  return slots.map((slot, fallbackIndex) => {
    const row = asRecord(slot);
    return {
      index: Number(row.index ?? fallbackIndex),
      type: textValue(row.type, 'universal'),
      displayName: textValue(row.display_name, textValue(row.type, 'universal')),
      opened: false
    };
  });
}

export function markOpenSlots(slots: ReturnType<typeof slotRows>, openSlots: unknown[]): ReturnType<typeof slotRows> {
  const open = new Set(openSlots.map((entry) => Number(entry)));
  return slots.map((slot) => ({ ...slot, opened: open.has(slot.index) }));
}

export function upgradeLevels(preview: ItemPreviewResult | null): AnyMap[] {
  const levels = asList(asRecord(preview?.upgrade).levels);
  return levels.map((entry) => asRecord(entry));
}

export function concise(value: unknown): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/\.00$/, '');
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (Array.isArray(value)) return value.map(concise).join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
