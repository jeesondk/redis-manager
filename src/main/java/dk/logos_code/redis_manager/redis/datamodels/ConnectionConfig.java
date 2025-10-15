package dk.logos_code.redis_manager.redis.datamodels;

import java.util.List;

/**
 * A saved connection configuration. Stored in-memory for now.
 */
public class ConnectionConfig {
    public Long id;
    public String name;

    // Reuse request schema for connection details
    public RedisConnectionType type; // node | sentinel | cluster
    public List<String> urls;
    public String username;
    public String password;
    public String sentinelMasterId;
    public Integer database;
    public Integer timeoutMs;

    public static class RedisValueTypeMapper {
        public static RedisValueType mapType(String t) {
            if (t == null) return RedisValueType.UNKNOWN;
            return switch (t) {
                case "string" -> RedisValueType.STRING;
                case "list" -> RedisValueType.LIST;
                case "set" -> RedisValueType.SET;
                case "zset" -> RedisValueType.ZSET;
                case "hash" -> RedisValueType.HASH;
                case "none" -> RedisValueType.NONE;
                default -> RedisValueType.UNKNOWN;
            };
        }
    }
}
