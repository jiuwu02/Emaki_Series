import { renderMiniMessageParts } from '../lib/miniMessage';

/** Renders MiniMessage-formatted text with color spans. */
export function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}
