package dk.logos_code.redis_manager.redis.datamodels;

public record RedisKeyInfo(String key, RedisValueType type, long ttlSeconds, long pttlMillis) {}