package net.mysterria.stuff.features.dungeons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Thread-safe insertion-ordered map that evicts its oldest entry once it exceeds {@code capacity}. */
final class BoundedMap<K, V> {

    private final Map<K, V> entries;

    BoundedMap(int capacity) {
        this.entries = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    synchronized V get(K key) {
        return entries.get(key);
    }

    synchronized void put(K key, V value) {
        entries.put(key, value);
    }

    synchronized V remove(K key) {
        return entries.remove(key);
    }

    synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> factory) {
        return entries.computeIfAbsent(key, factory);
    }

}
