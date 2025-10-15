package dk.logos_code.redis_manager.redis.commands;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedisDbCounts {

    private static final Pattern DB_LINE =
            Pattern.compile("^db(\\d+):keys=(\\d+),expires=(\\d+),avg_ttl=.*$", Pattern.MULTILINE);

    /** Returns a map: dbIndex -> keyCount, using INFO keyspace. */
    public static Map<Integer, Long> getDbKeyCounts(StatefulRedisConnection<String, String> connection) {
        RedisCommands<String, String> cmd = connection.sync();

        String info = cmd.info("keyspace");
        Map<Integer, Long> counts = new TreeMap<>();
        Matcher m = DB_LINE.matcher(info);
        while (m.find()) {
            int db = Integer.parseInt(m.group(1));
            long keys = Long.parseLong(m.group(2));
            counts.put(db, keys);
        }

        try {
            Map<String, String> cfg = cmd.configGet("databases");
            String totalStr = cfg.get("databases");
            if (totalStr != null) {
                int total = Integer.parseInt(totalStr);
                for (int i = 0; i < total; i++) {
                    counts.putIfAbsent(i, 0L);
                }
            }
        }  catch (Exception ignored) {
            // CONFIG may be disabled (ACL/managed) — just return what INFO reported.
        }

        return counts;
    }
}

