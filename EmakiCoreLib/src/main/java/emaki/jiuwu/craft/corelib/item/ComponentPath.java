package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ComponentPath {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private final List<Segment> segments;

    private ComponentPath(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public static ComponentPath parse(String raw) {
        String path = Texts.toStringSafe(raw).trim();
        if (path.isEmpty()) {
            return new ComponentPath(List.of());
        }
        List<Segment> parsed = new ArrayList<>();
        int index = 0;
        StringBuilder pending = new StringBuilder();
        while (index < path.length()) {
            char current = path.charAt(index);
            if (current == '"' || current == '\'') {
                int closing = path.indexOf(current, index + 1);
                if (closing < 0) {
                    pending.append(path, index + 1, path.length());
                    index = path.length();
                    continue;
                }
                pending.append(path, index + 1, closing);
                index = closing + 1;
                continue;
            }
            if (current == '.') {
                flush(pending, parsed);
                index++;
                continue;
            }
            if (current == '[') {
                flush(pending, parsed);
                int closing = path.indexOf(']', index + 1);
                if (closing < 0) {
                    index = path.length();
                    continue;
                }
                String token = path.substring(index + 1, closing).trim();
                parsed.add(indexSegment(token));
                index = closing + 1;
                continue;
            }
            pending.append(current);
            index++;
        }
        flush(pending, parsed);
        return new ComponentPath(parsed);
    }

    private static Segment indexSegment(String token) {
        if ("*".equals(token)) {
            return new Wildcard();
        }
        try {
            return new Index(Integer.parseInt(token));
        } catch (NumberFormatException _) {
            return new Key(token);
        }
    }

    private static void flush(StringBuilder pending, List<Segment> destination) {
        String key = pending.toString().trim();
        pending.setLength(0);
        if (!key.isEmpty()) {
            destination.add(new Key(key));
        }
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    public List<Object> evaluate(Object root) {
        List<Object> current = new ArrayList<>();
        current.add(root);
        for (Segment segment : segments) {
            List<Object> next = new ArrayList<>();
            for (Object node : current) {
                segment.resolve(node, next);
            }
            if (next.isEmpty()) {
                return List.of();
            }
            current = next;
        }
        return current;
    }

    private sealed interface Segment permits Key, Index, Wildcard {
        void resolve(Object node, List<Object> destination);
    }

    private record Key(String name) implements Segment {
        @Override
        public void resolve(Object node, List<Object> destination) {
            if (!(node instanceof Map<?, ?> map)) {
                return;
            }
            if (map.containsKey(name)) {
                destination.add(map.get(name));
                return;
            }
            String alternate = alternateKey(name);
            if (alternate != null && map.containsKey(alternate)) {
                destination.add(map.get(alternate));
            }
        }

        private static String alternateKey(String name) {
            if (name.indexOf(':') < 0) {
                return MINECRAFT_PREFIX + name;
            }
            if (name.startsWith(MINECRAFT_PREFIX)) {
                return name.substring(MINECRAFT_PREFIX.length());
            }
            return null;
        }
    }

    private record Index(int position) implements Segment {
        @Override
        public void resolve(Object node, List<Object> destination) {
            if (!(node instanceof List<?> list)) {
                return;
            }
            if (position >= 0 && position < list.size()) {
                destination.add(list.get(position));
            }
        }
    }

    private record Wildcard() implements Segment {
        @Override
        public void resolve(Object node, List<Object> destination) {
            if (node instanceof List<?> list) {
                destination.addAll(list);
                return;
            }
            if (node instanceof Map<?, ?> map) {
                destination.addAll(map.values());
            }
        }
    }
}
