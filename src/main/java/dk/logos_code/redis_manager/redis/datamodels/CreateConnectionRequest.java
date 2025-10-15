package dk.logos_code.redis_manager.redis.datamodels;

public record CreateConnectionRequest (String name, String type, RedisServerInfo serverInfo, RedisCredentials credentials, String sentinelMasterId) {
}