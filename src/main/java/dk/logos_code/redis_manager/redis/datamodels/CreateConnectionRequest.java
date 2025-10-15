package dk.logos_code.redis_manager.redis.datamodels;

public record CreateConnectionRequest (String name, RedisServerInfo serverInfo, RedisCredentials credentials, String sentinelMasterId) {
}