import type { WebRegistryFile, WebRegistryModule } from './types';

export type RegistryChildFile = { name: string; relativePath: string; fullPath?: string };

export function normalizeDocumentPath(path: string | undefined): string {
  return String(path ?? '').trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
}

export function normalizeLookupPath(path: string | undefined): string {
  return normalizeDocumentPath(path).toLowerCase();
}

export function isGlobPath(path: string | undefined): boolean {
  return /[*?]/.test(String(path ?? ''));
}

export function isConcretePath(path: string | undefined): path is string {
  const normalized = normalizeDocumentPath(path);
  return Boolean(normalized && !isGlobPath(normalized));
}

export function concreteRegistryChildPath(file: Pick<WebRegistryFile, 'kind'>, child: RegistryChildFile): string {
  return normalizeDocumentPath(String(file.kind ?? '').toUpperCase() === 'SCRIPT' ? child.relativePath : (child.fullPath ?? child.relativePath));
}

export function treeDirtyKey(moduleId: string, fileId: string, filePath: string): string {
  return JSON.stringify([moduleId, fileId, normalizeDocumentPath(filePath)]);
}

export function globBaseDir(path: string | undefined): string {
  const normalized = normalizeDocumentPath(path);
  const starIndex = normalized.search(/[?*]/);
  const base = starIndex >= 0 ? normalized.slice(0, starIndex) : normalized;
  return base.replace(/\/+$/g, '');
}

export function leafFileName(path: string | undefined): string {
  const normalized = normalizeDocumentPath(path);
  return normalized.substring(normalized.lastIndexOf('/') + 1);
}

export function childPathCandidates(file: Pick<WebRegistryFile, 'kind'>, child: RegistryChildFile): string[] {
  const relativePath = normalizeDocumentPath(child.relativePath);
  const fullPath = concreteRegistryChildPath(file, child);
  return Array.from(new Set([fullPath, relativePath, leafFileName(fullPath), leafFileName(relativePath)].filter(Boolean)));
}

export function resolveConcreteChildPath(module: WebRegistryModule, targetPath: string): { file: WebRegistryFile; path: string } | null {
  const normalizedTarget = normalizeLookupPath(targetPath);
  for (const file of module.files) {
    const directPath = normalizeLookupPath(file.path);
    if (directPath === normalizedTarget && !isGlobPath(file.path)) return { file, path: normalizeDocumentPath(file.path) };
    for (const child of file.children ?? []) {
      const concrete = concreteRegistryChildPath(file, child);
      if (!isConcretePath(concrete)) continue;
      if (childPathCandidates(file, child).some(candidate => normalizeLookupPath(candidate) === normalizedTarget)) {
        return { file, path: concrete };
      }
    }
  }
  return null;
}
