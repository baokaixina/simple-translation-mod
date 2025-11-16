package translation.modid.tooltip;

import java.util.concurrent.ConcurrentHashMap;

public class TooltipCache {
    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public static String get(String key) {
        return cache.get(key);
    }

    public static void put(String key, String value) {
        cache.put(key, value);
    }

    public static void clear() {
        cache.clear();
    }
}

