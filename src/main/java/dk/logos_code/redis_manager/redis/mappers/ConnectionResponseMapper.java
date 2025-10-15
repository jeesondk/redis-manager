// src/main/java/dk/logos_code/redis_manager/redis/datamodels/ConnectionResponseMapper.java
package dk.logos_code.redis_manager.redis.mappers;

import dk.logos_code.redis_manager.redis.ConnectionType;
import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import dk.logos_code.redis_manager.redis.datamodels.ConnectionResponse;
import dk.logos_code.redis_manager.redis.datamodels.RedisServerInfo;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ConnectionResponseMapper {

    @Mappings({
            // Long -> int (null-safe)
            @Mapping(target = "id", expression = "java(cfg.id != null ? cfg.id.intValue() : 0)"),
            @Mapping(target = "name", source = "name"),
            @Mapping(target = "mode", expression = "java(toModeString(cfg.type))"),
            @Mapping(target = "serverInfo", expression = "java(toServerInfo(cfg))")
    })
    ConnectionResponse toResponse(ConnectionConfig cfg);

    // ----- helpers -----

    default String toModeString(ConnectionType t) {
        return t == null ? null : t.name().toLowerCase();
    }

    /** Build RedisServerInfo from urls like host[:port]. */
    default RedisServerInfo toServerInfo(ConnectionConfig cfg) {
        if (cfg == null) return null;

        List<String> endpoints = cfg.urls != null ? cfg.urls : List.of();

        String[] hosts;
        int port = 6379; // default if not present in urls

        if (!endpoints.isEmpty()) {
            HostPort first = parseHostPort(endpoints.get(0));
            port = first.port > 0 ? first.port : 6379;

            List<String> hs = new ArrayList<>(endpoints.size());
            for (String ep : endpoints) {
                hs.add(parseHostPort(ep).host);
            }
            hosts = hs.toArray(String[]::new);
        } else {
            hosts = new String[0];
        }

        String mode = toModeString(cfg.type);
        int defaultDb = cfg.database != null ? cfg.database : 0;
        int timeout = cfg.timeoutMs != null ? cfg.timeoutMs : 10000;

        return new RedisServerInfo(hosts, port, mode, defaultDb, timeout);
    }

    /** Parses formats like \"host:port\"; strips scheme and credentials if present. */
    private static HostPort parseHostPort(String s) {
        if (s == null) return new HostPort("", 0);
        String t = s.trim();

        // strip scheme and credentials (e.g., redis://user@host:6379)
        int schemeIdx = t.indexOf("://");
        if (schemeIdx >= 0) t = t.substring(schemeIdx + 3);
        int atIdx = t.indexOf('@');
        if (atIdx >= 0) t = t.substring(atIdx + 1);

        int colon = t.lastIndexOf(':');
        if (colon > 0 && colon < t.length() - 1) {
            String host = t.substring(0, colon);
            try {
                int p = Integer.parseInt(t.substring(colon + 1));
                return new HostPort(host, p);
            } catch (NumberFormatException ignored) { }
        }
        return new HostPort(t, 0);
    }

    class HostPort { final String host; final int port; HostPort(String h, int p){ host=h; port=p; } }
}
