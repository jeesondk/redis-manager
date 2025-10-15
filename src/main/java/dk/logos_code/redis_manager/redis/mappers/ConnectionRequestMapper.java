// src/main/java/dk/logos_code/redis_manager/redis/datamodels/ConnectionRequestMapper.java
package dk.logos_code.redis_manager.redis.mappers;

import dk.logos_code.redis_manager.redis.ConnectionType;
import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import dk.logos_code.redis_manager.redis.datamodels.CreateConnectionRequest;
import dk.logos_code.redis_manager.redis.datamodels.RedisServerInfo;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ConnectionRequestMapper {

    @Mappings({
            // id is generated elsewhere
            @Mapping(target = "id", ignore = true),

            @Mapping(target = "name", source = "name"),
            @Mapping(target = "sentinelMasterId", source = "sentinelMasterId"),

            // String -> enum (node|sentinel|cluster)
            @Mapping(target = "type", expression = "java(mapType(req.type(), req.serverInfo()))"),

            // credentials
            @Mapping(target = "username", source = "credentials.username"),
            @Mapping(target = "password", source = "credentials.password"),

            // db + timeout
            @Mapping(target = "database", source = "serverInfo.defaultDb"),
            @Mapping(target = "timeoutMs", source = "serverInfo.timeoutMs"),

            // build urls from hosts+port in @AfterMapping
            @Mapping(target = "urls", ignore = true)
    })
    ConnectionConfig toConfig(CreateConnectionRequest req);

    @AfterMapping
    default void buildUrls(@MappingTarget ConnectionConfig cfg, CreateConnectionRequest req) {
        RedisServerInfo si = req.serverInfo();
        if (si == null) return;

        String[] hosts = si.hosts();
        int port = si.port() > 0 ? si.port() : 6379;

        if (hosts != null && hosts.length > 0) {
            List<String> list = new ArrayList<>(hosts.length);
            for (String h : hosts) {
                if (h != null && !h.isBlank()) {
                    list.add(h.trim() + ":" + port);
                }
            }
            cfg.urls = list.isEmpty() ? null : list;
        }
    }

    /** Map the request `type` or, if missing, try to infer from serverInfo.mode. */
    default ConnectionType mapType(String rawType, RedisServerInfo si) {
        if (rawType != null && !rawType.isBlank()) {
            return toEnum(rawType.trim());
        }
        // optional fallback from serverInfo.mode
        if (si != null && si.mode() != null && !si.mode().isBlank()) {
            return toEnum(si.mode().trim());
        }
        return null;
    }

    private ConnectionType toEnum(String v) {
        return switch (v.toLowerCase()) {
            case "node", "single", "standalone" -> ConnectionType.node;
            case "sentinel" -> ConnectionType.sentinel;
            case "cluster" -> ConnectionType.cluster;
            default -> throw new IllegalArgumentException("Unknown connection type/mode: " + v);
        };
    }
}
