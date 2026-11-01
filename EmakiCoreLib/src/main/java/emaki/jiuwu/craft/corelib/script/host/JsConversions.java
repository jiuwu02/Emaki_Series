package emaki.jiuwu.craft.corelib.script.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jetbrains.annotations.Nullable;

public final class JsConversions {

    private JsConversions() {
    }

    public static @Nullable Object deepToJs(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), deepToJs(entry.getValue()));
            }
            return ProxyObject.fromMap(converted);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object element : list) {
                converted.add(deepToJs(element));
            }
            return ProxyArray.fromList(converted);
        }
        return value;
    }
}
