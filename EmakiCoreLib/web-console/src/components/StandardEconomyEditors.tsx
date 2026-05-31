import React, { createContext, useContext, useEffect, useState } from 'react';
import type { EconomyProvidersResult } from '../api';
import { optionLabel, textValue } from '../lib';

/**
 * Canonical CoreLib economy provider ids. These mirror the server-side
 * EconomyManager built-in providers (`auto` plus registered Vault /
 * ExcellentEconomy) and act as a fallback before the backend
 * `/api/economy/providers` response arrives.
 */
export const CANONICAL_ECONOMY_PROVIDERS: string[] = ['auto', 'vault', 'excellenteconomy'];

const FALLBACK_PROVIDERS: EconomyProvidersResult = { providers: CANONICAL_ECONOMY_PROVIDERS, availableProviders: ['auto'] };

const EconomyProvidersContext = createContext<EconomyProvidersResult>(FALLBACK_PROVIDERS);

type EconomyProvidersSource = { economyProviders(): Promise<EconomyProvidersResult> };

/**
 * Provides CoreLib economy provider ids to every standard economy field in the
 * subtree. Fetches once from the backend and falls back to the canonical list,
 * so the `economyProvider` field type always renders a proper dropdown.
 */
export function EconomyProvidersProvider({ api, children }: { api: EconomyProvidersSource; children: React.ReactNode }) {
  const [providers, setProviders] = useState<EconomyProvidersResult>(FALLBACK_PROVIDERS);
  useEffect(() => {
    let active = true;
    api.economyProviders()
      .then(result => {
        if (!active) return;
        setProviders({
          providers: result.providers?.length ? result.providers : CANONICAL_ECONOMY_PROVIDERS,
          availableProviders: result.availableProviders?.length ? result.availableProviders : ['auto']
        });
      })
      .catch(() => { /* keep canonical fallback */ });
    return () => { active = false; };
  }, [api]);
  return <EconomyProvidersContext.Provider value={providers}>{children}</EconomyProvidersContext.Provider>;
}

/** Read the current CoreLib economy provider ids from context (with canonical fallback). */
export function useEconomyProviders(): string[] {
  return useContext(EconomyProvidersContext).providers;
}

/**
 * Unified economy provider selector. Reads available providers from the
 * EconomyProvidersProvider so callers never need to wire them up. Unknown
 * current values are preserved as an extra option for forward compatibility.
 */
export function StandardEconomyProviderSelect({ value, onChange, providers, moduleId, optionPrefix = 'economyProvider' }: {
  value: unknown;
  onChange: (value: string) => void;
  providers?: string[];
  moduleId?: string;
  optionPrefix?: string;
}) {
  const contextProviders = useEconomyProviders();
  const resolved = providers?.length ? providers : contextProviders;
  const current = textValue(value);
  const merged = current && !resolved.includes(current) ? [...resolved, current] : resolved;
  return <select value={current} onChange={event => onChange(event.target.value)}>
    {merged.map(option => <option key={option} value={option}>{optionLabel(optionPrefix, option, { moduleId, namespace: moduleId, fallback: option })}</option>)}
  </select>;
}
