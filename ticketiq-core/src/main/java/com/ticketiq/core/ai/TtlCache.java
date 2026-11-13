package com.ticketiq.core.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A small in-memory cache whose entries expire. Used to avoid paying twice for the same triage. */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    public TtlCache(Duration ttl, int maxEntries) {
        this(ttl, maxEntries, Clock.systemUTC());
    }

    public TtlCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public Optional<V> get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(e.expiresAt())) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    public void put(K key, V value) {
        if (map.size() >= maxEntries) {
            Instant now = clock.instant();
            map.entrySet().removeIf(en -> !now.isBefore(en.getValue().expiresAt()));
            if (map.size() >= maxEntries) {
                map.keySet().stream().findFirst().ifPresent(map::remove);
            }
        }
        map.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }

    public int size() {
        return map.size();
    }
}
