package dk.logos_code.redis_manager.redis;

import dk.logos_code.redis_manager.redis.commands.RedisDbCounts;
import dk.logos_code.redis_manager.redis.commands.RedisGetValue;
import dk.logos_code.redis_manager.redis.commands.RedisListKeys;
import dk.logos_code.redis_manager.redis.datamodels.*;
import dk.logos_code.redis_manager.redis.mappers.ConnectionRequestMapper;
import dk.logos_code.redis_manager.redis.mappers.ConnectionResponseMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for RedisService using Mockito (no Redis required).
 */
public class RedisServiceTest {

    private RedisService service;
    private RedisConnectionRepository repo;
    private RedisConnector connector;
    private ConnectionRequestMapper requestMapper;
    private ConnectionResponseMapper responseMapper;

    private ConnectionConfig sampleCfg(long id) {
        ConnectionConfig cfg = new ConnectionConfig();
        cfg.id = id;
        cfg.name = "Local";
        cfg.type = RedisConnectionType.node;
        cfg.urls = List.of("127.0.0.1:6379");
        cfg.database = 0;
        cfg.timeoutMs = 1000;
        return cfg;
    }

    @BeforeEach
    void setup() {
        service = new RedisService();
        repo = mock(RedisConnectionRepository.class);
        connector = mock(RedisConnector.class);
        requestMapper = mock(ConnectionRequestMapper.class);
        responseMapper = mock(ConnectionResponseMapper.class);

        // Inject mocks (fields have package visibility)
        service.repo = repo;
        service.connector = connector;
        service.requestMapper = requestMapper;
        service.responseMapper = responseMapper;
    }

    @Test
    void getDatabases_happyPath_returnsCounts() throws Exception {
        long id = 1L;
        ConnectionConfig cfg = sampleCfg(id);
        when(repo.get(id)).thenReturn(cfg);

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> conn = mock(StatefulRedisConnection.class);
        when(connector.GetClient()).thenReturn(connector);
        when(connector.Connect(cfg, cfg.timeoutMs)).thenReturn(conn);

        Map<Integer, Long> expected = new TreeMap<>();
        expected.put(0, 12L);
        expected.put(1, 0L);

        try (MockedStatic<RedisDbCounts> ms = mockStatic(RedisDbCounts.class)) {
            ms.when(() -> RedisDbCounts.getDbKeyCounts(conn)).thenReturn(expected);
            Map<Integer, Long> result = service.getDatabases(id);
            assertThat(result).isEqualTo(expected);
        }

        // Verify resources are used
        verify(connector).GetClient();
        verify(connector).Connect(cfg, cfg.timeoutMs);
    }

    @Test
    void getDatabases_missingConnection_throws() {
        when(repo.get(42L)).thenReturn(null);
        assertThatThrownBy(() -> service.getDatabases(42L))
                .isInstanceOf(Exception.class);
    }

    @Test
    void listKeys_streamsPipelinedToList() throws Exception {
        long id = 2L;
        ConnectionConfig cfg = sampleCfg(id);
        when(repo.get(id)).thenReturn(cfg);

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> conn = mock(StatefulRedisConnection.class);
        when(connector.GetClient()).thenReturn(connector);
        when(connector.Connect(cfg, cfg.timeoutMs)).thenReturn(conn);

        List<RedisKeyInfo> emitted = new ArrayList<>();

        try (MockedStatic<RedisListKeys> ms = mockStatic(RedisListKeys.class)) {
            ms.when(() -> RedisListKeys.streamKeysWithTypeInDbPipelined(eq(conn), eq(0), isNull(), eq(100), any()))
              .thenAnswer(invocation -> {
                  @SuppressWarnings("unchecked")
                  var consumer = (java.util.function.Consumer<RedisKeyInfo>) invocation.getArgument(4);
                  consumer.accept(new RedisKeyInfo("a", RedisValueType.STRING, 10, 1000));
                  consumer.accept(new RedisKeyInfo("b", RedisValueType.LIST,  -1, -1));
                  return null;
              });

            List<RedisKeyInfo> result = service.listKeys(id, 0, null, 100);
            emitted.addAll(result);
        }

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).key()).isEqualTo("a");
        assertThat(emitted.get(1).type()).isEqualTo(RedisValueType.LIST);
    }

    @Test
    void getKeyValue_delegatesToHelper() throws Exception {
        long id = 3L;
        ConnectionConfig cfg = sampleCfg(id);
        when(repo.get(id)).thenReturn(cfg);

        @SuppressWarnings("unchecked")
        StatefulRedisConnection<String, String> conn = mock(StatefulRedisConnection.class);
        when(connector.GetClient()).thenReturn(connector);
        when(connector.Connect(cfg, cfg.timeoutMs)).thenReturn(conn);

        RedisValue val = RedisValue.ofString("k1", "v1", 10, 1000);
        try (MockedStatic<RedisGetValue> ms = mockStatic(RedisGetValue.class)) {
            ms.when(() -> RedisGetValue.get(conn, 0, "k1")).thenReturn(val);
            RedisValue out = service.getKeyValue(id, 0, "k1");
            assertThat(out).isSameAs(val);
        }
    }

    @Test
    void addUpdateGetDelete_connectionFlow_usesMappersAndRepo() {
        CreateConnectionRequest req = new CreateConnectionRequest(
                "Conn-1",
                new RedisServerInfo(new String[]{"h1","h2"}, 6379, RedisConnectionType.node, 0, 2000),
                new RedisCredentials("user", "pass"),
                null
        );

        ConnectionConfig mapped = sampleCfg(0);
        when(requestMapper.toConfig(req)).thenReturn(mapped);

        ConnectionConfig stored = sampleCfg(10);
        when(repo.store(mapped)).thenReturn(stored);

        ConnectionResponse resp = new ConnectionResponse(10, "Conn-1", "node",
                new RedisServerInfo(new String[]{"h1","h2"}, 6379, RedisConnectionType.node, 0, 2000));
        when(responseMapper.toResponse(stored)).thenReturn(resp);

        // add
        ConnectionResponse added = service.addConnection(req);
        assertThat(added).isSameAs(resp);
        verify(repo).store(mapped);

        // get list
        when(repo.getAll()).thenReturn(List.of(stored));
        when(responseMapper.toResponse(stored)).thenReturn(resp);
        List<ConnectionResponse> all = service.getConnections();
        assertThat(all).containsExactly(resp);

        // get single
        when(repo.get(10L)).thenReturn(stored);
        assertThat(service.getConnection(10)).isSameAs(resp);

        // update
        when(repo.get(10L)).thenReturn(stored);
        when(repo.update(stored)).thenReturn(stored);
        ConnectionResponse updated = service.updateConnection(10, req);
        assertThat(updated).isSameAs(resp);

        // delete
        service.deleteConnection(10);
        verify(repo).delete(10);
    }

    @Test
    void updateConnection_validationFails_forMissingUrls() {
        CreateConnectionRequest badReq = new CreateConnectionRequest(
                "Conn-2",
                new RedisServerInfo(new String[]{}, 6379, RedisConnectionType.sentinel, 0, 1000),
                new RedisCredentials(null, null),
                "mymaster"
        );
        ConnectionConfig fromRepo = sampleCfg(22);
        fromRepo.urls = Collections.emptyList();
        fromRepo.type = RedisConnectionType.sentinel;
        when(repo.get(22L)).thenReturn(fromRepo);

        assertThatThrownBy(() -> service.updateConnection(22, badReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one endpoint");
    }
}
