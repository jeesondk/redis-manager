package dk.logos_code.redis_manager.redis.commands;

import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import dk.logos_code.redis_manager.redis.datamodels.RedisValueType;
import dk.logos_code.redis_manager.redis.datamodels.RedisValue;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Helpers to fetch a Redis key as its native structure.
 * Supports: string, list, set, zset (with scores), hash.
 */
public final class RedisGetValue {

    private RedisGetValue() {
    }

    /**
     * Legacy convenience: returns the key as a String (only if the value is a String).
     * If the key isn't a string (or doesn't exist), this returns null.
     */
    public static String getValueByKey(StatefulRedisConnection<String, String> conn, int dbIndex, String key) {
        RedisValue v = get(conn, dbIndex, key);
        return (v.type == RedisValueType.STRING) ? v.stringValue : null;
    }

    /**
     * Get a key in its native structure with TTL/PTTL.
     * Returns RedisValue.type == NONE if the key doesn't exist.
     */
    public static RedisValue get(StatefulRedisConnection<String, String> conn, int dbIndex, String key) {
        Objects.requireNonNull(conn, "connection");
        Objects.requireNonNull(key, "key");

        RedisCommands<String, String> cmd = conn.sync();

        // Try to switch DB (no-op/exception on cluster or limited ACLs -> ignore)
        try {
            cmd.select(dbIndex);
        } catch (Exception ignored) { /* cluster or managed Redis often forbids SELECT */ }

        String typeStr = cmd.type(key); // "string","list","set","zset","hash","none",...
        RedisValueType type = ConnectionConfig.RedisValueTypeMapper.mapType(typeStr);

        long ttlSeconds = cmd.ttl(key);   // -1 no expire, -2 key missing
        long pttlMillis = cmd.pttl(key);  // -1 no expire, -2 key missing

        return switch (type) {
            case STRING -> {
                String v = cmd.get(key);
                yield (v != null)
                        ? RedisValue.ofString(key, v, ttlSeconds, pttlMillis)
                        : RedisValue.none(key);
            }
            case LIST -> {
                // Full list; for huge lists consider pagination via LRANGE windows
                List<String> list = cmd.lrange(key, 0, -1);
                yield RedisValue.ofList(key, list, ttlSeconds, pttlMillis);
            }
            case SET -> {
                // Sets are unordered; for huge sets consider SSCAN streaming
                Set<String> members = cmd.smembers(key);
                yield RedisValue.ofSet(key, members, ttlSeconds, pttlMillis);
            }
            case ZSET -> {
                // Entire sorted set; for huge ones consider ZRANGE with LIMIT windows
                List<ScoredValue<String>> entries = cmd.zrangeWithScores(key, 0, -1);
                yield RedisValue.ofZSet(key, entries, ttlSeconds, pttlMillis);
            }
            case HASH -> {
                Map<String, String> map = cmd.hgetall(key);
                yield RedisValue.ofHash(key, map, ttlSeconds, pttlMillis);
            }
            case NONE -> RedisValue.none(key);
            default ->
                // Extend here if/when you add support for streams, bitmaps, JSON, etc.
                    RedisValue.none(key);
        };
    }
}
