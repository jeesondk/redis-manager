package dk.logos_code.redis_manager.redis.datamodels;

import io.lettuce.core.ScoredValue;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RedisValue {
    public final String key;
    public final RedisValueType type;

    public final String stringValue;                      // when STRING
    public final List<String> listValue;                  // when LIST
    public final Set<String> setValue;                    // when SET
    public final List<ScoredValue<String>> zsetValue;     // when ZSET
    public final Map<String, String> hashValue;           // when HASH

    /** TTL in seconds (TTL). -1 = no expire, -2 = key missing. */
    public final long ttlSeconds;
    /** PTTL in milliseconds (PTTL). -1 = no expire, -2 = key missing. */
    public final long pttlMillis;

    private RedisValue(
            String key,
            RedisValueType type,
            String stringValue,
            List<String> listValue,
            Set<String> setValue,
            List<ScoredValue<String>> zsetValue,
            Map<String, String> hashValue,
            long ttlSeconds,
            long pttlMillis
    ) {
        this.key = key;
        this.type = type;
        this.stringValue = stringValue;
        this.listValue = listValue;
        this.setValue = setValue;
        this.zsetValue = zsetValue;
        this.hashValue = hashValue;
        this.ttlSeconds = ttlSeconds;
        this.pttlMillis = pttlMillis;
    }

    public static RedisValue ofString(String key, String value, long ttl, long pttl) {
        return new RedisValue(key, RedisValueType.STRING, value, null, null, null, null, ttl, pttl);
    }
    public static RedisValue ofList(String key, List<String> value, long ttl, long pttl) {
        return new RedisValue(key, RedisValueType.LIST, null, value, null, null, null, ttl, pttl);
    }
    public static RedisValue ofSet(String key, Set<String> value, long ttl, long pttl) {
        return new RedisValue(key, RedisValueType.SET, null, null, value, null, null, ttl, pttl);
    }
    public static RedisValue ofZSet(String key, List<ScoredValue<String>> value, long ttl, long pttl) {
        return new RedisValue(key, RedisValueType.ZSET, null, null, null, value, null, ttl, pttl);
    }
    public static RedisValue ofHash(String key, Map<String, String> value, long ttl, long pttl) {
        return new RedisValue(key, RedisValueType.HASH, null, null, null, null, value, ttl, pttl);
    }
    public static RedisValue none(String key) {
        return new RedisValue(key, RedisValueType.NONE, null, null, null, null, null, -2, -2);
    }
}
