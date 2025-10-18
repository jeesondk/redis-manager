package dk.logos_code.redis_manager.redis.resources;

import dk.logos_code.redis_manager.redis.RedisService;
import dk.logos_code.redis_manager.redis.datamodels.RedisKeyInfo;
import dk.logos_code.redis_manager.redis.datamodels.RedisValue;
import dk.logos_code.redis_manager.redis.datamodels.RedisValueType;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisInstanceResourceTest {

    private RedisInstanceResource resource;
    private RedisService service;

    @BeforeEach
    void setup() {
        resource = new RedisInstanceResource();
        service = mock(RedisService.class);
        resource.redisService = service; // package-private field
    }

    @Test
    void databases_success() throws Exception {
        when(service.getDatabases(1L)).thenReturn(Map.of(0, 5L, 1, 0L));
        Map<Integer, Long> out = resource.databases(1);
        assertThat(out).containsEntry(0, 5L).containsEntry(1, 0L);
    }

    @Test
    void databases_error_mapsToBadRequest() throws Exception {
        when(service.getDatabases(1L)).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> resource.databases(1)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void keys_success() throws Exception {
        List<RedisKeyInfo> keys = List.of(
                new RedisKeyInfo("k1", RedisValueType.STRING, 10, 1000),
                new RedisKeyInfo("k2", RedisValueType.LIST, -1, -1)
        );
        when(service.listKeys(1L, 0, null, 100)).thenReturn(keys);
        List<RedisKeyInfo> out = resource.keys(1, 0, null, 100);
        assertThat(out).isEqualTo(keys);
    }

    @Test
    void keys_error_mapsToBadRequest() throws Exception {
        when(service.listKeys(1L, 0, "*", 10)).thenThrow(new RuntimeException("x"));
        assertThatThrownBy(() -> resource.keys(1, 0, "*", 10))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getKeyValue_success() throws Exception {
        RedisValue v = RedisValue.ofString("k1", "v1", 10, 1000);
        when(service.getKeyValue(1L, 0, "k1")).thenReturn(v);
        RedisValue out = resource.getKeyValue(1, 0, "k1");
        assertThat(out).isSameAs(v);
    }

    @Test
    void getKeyValue_error_mapsToBadRequest() throws Exception {
        when(service.getKeyValue(1L, 0, "k1")).thenThrow(new RuntimeException("x"));
        assertThatThrownBy(() -> resource.getKeyValue(1, 0, "k1"))
                .isInstanceOf(BadRequestException.class);
    }
}
