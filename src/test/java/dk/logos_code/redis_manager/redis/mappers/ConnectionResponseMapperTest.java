package dk.logos_code.redis_manager.redis.mappers;

import dk.logos_code.redis_manager.redis.datamodels.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionResponseMapperTest {

    @Test
    void mapsConfigToResponseWithServerInfo() {
        ConnectionResponseMapper mapper = new ConnectionResponseMapperImpl();

        ConnectionConfig cfg = new ConnectionConfig();
        cfg.id = 99L;
        cfg.name = "Prod";
        cfg.type = RedisConnectionType.sentinel;
        cfg.urls = List.of("sentinel-a:26379", "sentinel-b:26379");
        cfg.database = 5;
        cfg.timeoutMs = 5000;

        ConnectionResponse resp = mapper.toResponse(cfg);
        assertThat(resp.id()).isEqualTo(99);
        assertThat(resp.name()).isEqualTo("Prod");
        assertThat(resp.mode()).isEqualTo("sentinel");

        RedisServerInfo si = resp.serverInfo();
        assertThat(si).isNotNull();
        assertThat(si.hosts()).containsExactly("sentinel-a","sentinel-b");
        assertThat(si.port()).isEqualTo(26379);
        assertThat(si.defaultDb()).isEqualTo(5);
        assertThat(si.timeoutMs()).isEqualTo(5000);
        assertThat(si.mode()).isEqualTo(RedisConnectionType.sentinel);
    }
}
