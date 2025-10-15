package dk.logos_code.redis_manager.redis;

import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class RedisConnectionRepository {

    private static final AtomicLong IDGEN = new AtomicLong(1);
    private static final Map<Long, ConnectionConfig> STORE = new ConcurrentHashMap<>();

    public ConnectionConfig store(ConnectionConfig cfg) {
        cfg.id = IDGEN.getAndIncrement();
        STORE.put(cfg.id, cfg);
        return cfg;
    }

    public ConnectionConfig get(Long id) {
        return STORE.get(id);
    }

    public List<ConnectionConfig> getAll() {
        return STORE.values().stream()
                .sorted((a,b) -> Long.compare(a.id, b.id))
                .toList();
    }

    public void delete(long id) {
        STORE.remove(id);
    }
}
