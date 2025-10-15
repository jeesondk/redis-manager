package dk.logos_code.redis_manager.redis.datamodels;

public record ConnectionResponse(int id, String name, String mode, RedisServerInfo serverInfo) {}
