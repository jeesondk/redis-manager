package dk.logos_code.redis_manager.redis.datamodels;

public record RedisServerInfo(String[] hosts, int port, RedisConnectionType mode, int defaultDb, int timeoutMs){}
