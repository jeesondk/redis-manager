package dk.logos_code.redis_manager.redis.commands;

import java.util.function.Consumer;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Objects;

public class RedisListKeys {

    /**
     * Streams all keys in the given logical DB using SCAN and passes them to the provided consumer.
     * Safe for large datasets.
     *
     * @param connection Lettuce connection
     * @param dbIndex logical database (ignored by Redis Cluster)
     * @param matchPattern optional glob (e.g., "user:*"), null or "*" for all
     * @param scanCount hint for batch size per SCAN iteration
     * @param keyConsumer consumer that will receive each key
     * Usage:
     * RedisDbCounts.streamKeysInDb(connection, 2, "*", 2000, key -> {
     *   // process each key
     *   System.out.println(key);
     * });
     */
    public static void streamKeysInDb(
            StatefulRedisConnection<String, String> connection,
            int dbIndex,
            String matchPattern,
            int scanCount,
            Consumer<String> keyConsumer
    ) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(keyConsumer, "keyConsumer");
        if (scanCount <= 0) scanCount = 1000;

        RedisCommands<String, String> cmd = connection.sync();

        // Switch to the requested logical DB (no-op on providers that disable SELECT; handle gracefully)
        boolean selected = false;
        try {
            cmd.select(dbIndex);
            selected = true;
        } catch (Exception ignored) {
            // SELECT may be disabled or you might be on a managed service / ACL limited user.
            // We'll still try to SCAN (will be the current DB).
        }

        ScanArgs args = ScanArgs.Builder.limit(scanCount);
        if (matchPattern != null && !matchPattern.equals("*")) {
            args = args.match(matchPattern);
        }

        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> ks = cmd.scan(cursor, args);
            for (String k : ks.getKeys()) {
                keyConsumer.accept(k);
            }
            cursor = ks;
        } while (!cursor.isFinished());

        // Optional: return to DB 0 to avoid surprising callers that reuse the connection
        if (selected && dbIndex != 0) {
            try {
                cmd.select(0);
            } catch (Exception ignored) { }
        }
    }

}
