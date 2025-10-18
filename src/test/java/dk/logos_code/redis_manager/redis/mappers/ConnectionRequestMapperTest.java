package dk.logos_code.redis_manager.redis.mappers;

import dk.logos_code.redis_manager.redis.datamodels.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionRequestMapperTest {

    @Test
    void mapsHostsPortAndCredentials() {
        ConnectionRequestMapper mapper = new ConnectionRequestMapperImpl();

        CreateConnectionRequest req = new CreateConnectionRequest(
                "Conn A",
                new RedisServerInfo(new String[]{"host1","host2"}, 6380, RedisConnectionType.node, 2, 1500),
                new RedisCredentials("user","pass"),
                null
        );

        ConnectionConfig cfg = mapper.toConfig(req);
        assertThat(cfg.name).isEqualTo("Conn A");
        assertThat(cfg.type).isEqualTo(RedisConnectionType.node);
        assertThat(cfg.username).isEqualTo("user");
        assertThat(cfg.password).isEqualTo("pass");
        assertThat(cfg.database).isEqualTo(2);
        assertThat(cfg.timeoutMs).isEqualTo(1500);

        // urls are built in @AfterMapping
        assertThat(cfg.urls).containsExactly("host1:6380","host2:6380");
    }
}
