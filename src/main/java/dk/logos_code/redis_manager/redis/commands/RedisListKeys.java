package dk.logos_code.redis_manager.redis.commands;

import dk.logos_code.redis_manager.redis.datamodels.ConnectionConfig;
import dk.logos_code.redis_manager.redis.datamodels.RedisKeyInfo;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class RedisListKeys {
    /**
     * Streams all keys in the given logical DB using SCAN and passes them to the provided consumer.
     * Safe for large datasets. (Original method, unchanged)
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
        boolean selected = trySelect(cmd, dbIndex);

        ScanArgs args = buildScanArgs(matchPattern, scanCount);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> ks = cmd.scan(cursor, args);
            for (String k : ks.getKeys()) {
                keyConsumer.accept(k);
            }
            cursor = ks;
        } while (!cursor.isFinished());

        tryUnselect(cmd, selected, dbIndex);
    }

    /**
     * Streams (key, type) for each key via the given consumer.
     * Also fills ttlSeconds and pttlMillis for convenience (you can ignore them).
     */
    public static void streamKeysWithTypeInDb(
            StatefulRedisConnection<String, String> connection,
            int dbIndex,
            String matchPattern,
            int scanCount,
            Consumer<RedisKeyInfo> keyInfoConsumer
    ) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(keyInfoConsumer, "keyInfoConsumer");
        if (scanCount <= 0) scanCount = 1000;

        RedisCommands<String, String> cmd = connection.sync();
        boolean selected = trySelect(cmd, dbIndex);

        ScanArgs args = buildScanArgs(matchPattern, scanCount);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> ks = cmd.scan(cursor, args);
            for (String key : ks.getKeys()) {
                String t = cmd.type(key);
                long ttl = cmd.ttl(key);
                long pttl = cmd.pttl(key);
                keyInfoConsumer.accept(new RedisKeyInfo(key, ConnectionConfig.RedisValueTypeMapper.mapType(t), ttl, pttl));
            }
            cursor = ks;
        } while (!cursor.isFinished());

        tryUnselect(cmd, selected, dbIndex);
    }

    /**
     * Like streamKeysWithTypeInDb but performs TYPE/TTL/PTTL in a pipelined batch per SCAN page.
     * This can be significantly faster over high latency connections.
     */
    public static void streamKeysWithTypeInDbPipelined(
            StatefulRedisConnection<String, String> connection,
            int dbIndex,
            String matchPattern,
            int scanCount,
            Consumer<RedisKeyInfo> keyInfoConsumer
    ) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(keyInfoConsumer, "keyInfoConsumer");
        if (scanCount <= 0) scanCount = 1000;

        RedisCommands<String, String> sync = connection.sync();
        RedisAsyncCommands<String, String> async = connection.async();
        boolean selected = trySelect(sync, dbIndex);

        ScanArgs args = buildScanArgs(matchPattern, scanCount);
        ScanCursor cursor = ScanCursor.INITIAL;
        do {
            KeyScanCursor<String> ks = sync.scan(cursor, args);
            List<String> keys = ks.getKeys();
            if (!keys.isEmpty()) {
                // Pipeline: TYPE + TTL + PTTL per key
                async.setAutoFlushCommands(false);
                List<CompletableFuture<String>> typeF = new ArrayList<>(keys.size());
                List<CompletableFuture<Long>> ttlF  = new ArrayList<>(keys.size());
                List<CompletableFuture<Long>> pttlF = new ArrayList<>(keys.size());
                for (String k : keys) {
                    typeF.add(async.type(k).toCompletableFuture());
                    ttlF.add(async.ttl(k).toCompletableFuture());
                    pttlF.add(async.pttl(k).toCompletableFuture());
                }
                async.flushCommands();

                // Wait for batch results (best-effort)
                for (int i = 0; i < keys.size(); i++) {
                    String k = keys.get(i);
                    String t  = joinSilently(typeF.get(i), "unknown");
                    long ttl  = joinSilently(ttlF.get(i), -2L);
                    long pttl = joinSilently(pttlF.get(i), -2L);
                    keyInfoConsumer.accept(new RedisKeyInfo(k, ConnectionConfig.RedisValueTypeMapper.mapType(t), ttl, pttl));
                }
                async.setAutoFlushCommands(true);
            }
            cursor = ks;
        } while (!cursor.isFinished());

        tryUnselect(sync, selected, dbIndex);
    }

    // Convenience: if you just want (key, type) as strings.
    public static void streamKeysAndTypes(
            StatefulRedisConnection<String, String> connection,
            int dbIndex,
            String matchPattern,
            int scanCount,
            BiConsumer<String, String> consumer
    ) {
        streamKeysWithTypeInDb(connection, dbIndex, matchPattern, scanCount, info ->
                consumer.accept(info.key(), info.type().name().toLowerCase())
        );
    }


    private static boolean trySelect(RedisCommands<String, String> cmd, int dbIndex) {
        boolean selected = false;
        try {
            cmd.select(dbIndex);
            selected = true;
        } catch (Exception ignored) { /* cluster/managed may forbid SELECT */ }
        return selected;
    }

    private static void tryUnselect(RedisCommands<String, String> cmd, boolean selected, int dbIndex) {
        if (selected && dbIndex != 0) {
            try {
                cmd.select(0);
            } catch (Exception ignored) { }
        }
    }

    private static ScanArgs buildScanArgs(String matchPattern, int scanCount) {
        ScanArgs args = ScanArgs.Builder.limit(scanCount);
        if (matchPattern != null && !matchPattern.equals("*")) {
            args = args.match(matchPattern);
        }
        return args;
    }


    private static <T> T joinSilently(CompletableFuture<T> f, T fallback) {
        try {
            return f.join();
        } catch (Exception e) {
            return fallback;
        }
    }
}
