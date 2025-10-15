package dk.logos_code.redis_manager.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

/**
 * Integration test that can be run to load random keys into a local Redis instance.
 *
 * Defaults:
 *  - host: localhost
 *  - port: 6379
 *  - db: 0
 *  - keyCount: 1000
 *  - keyPrefix: it:rand:
 *  - ttlSeconds: 0 (disabled); set to a positive value to apply same TTL to all keys
 *
 * You can override with environment variables or system properties, e.g.:
 *  -Dredis.host=localhost -Dredis.port=6379 -Dredis.db=0 -Dredis.count=5000 -Dredis.prefix=load: -Dredis.ttl=3600
 * or via env:
 *  REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_KEY_COUNT, REDIS_KEY_PREFIX, REDIS_TTL_SECONDS
 *
 * Notes:
 *  - This test uses JUnit Assumptions to SKIP if Redis is not reachable, so CI won't fail.
 *  - To ensure Failsafe runs integration tests, execute: mvn -DskipITs=false verify
 */
public class RedisRandomLoadIT {

    private static RedisClient client;
    private static StatefulRedisConnection<String, String> connection;
    private static RedisCommands<String, String> cmd;

    private static String host;
    private static int port;
    private static int db;
    private static int keyCount;
    private static String keyPrefix;
    private static int ttlSeconds; // 0 = disabled

    @BeforeAll
    static void setup() {
        host = get("redis.host", "REDIS_HOST", "localhost");
        port = Integer.parseInt(get("redis.port", "REDIS_PORT", "6379"));
        db = Integer.parseInt(get("redis.db", "REDIS_DB", "0"));
        keyCount = Integer.parseInt(get("redis.count", "REDIS_KEY_COUNT", "1000"));
        keyPrefix = get("redis.prefix", "REDIS_KEY_PREFIX", "it:rand:");
        ttlSeconds = Integer.parseInt(get("redis.ttl", "REDIS_TTL_SECONDS", "0"));

        RedisURI uri = RedisURI.Builder.redis(host, port)
                .withDatabase(db)
                .withTimeout(Duration.ofSeconds(2))
                .build();

        client = RedisClient.create(uri);
        try {
            connection = client.connect();
            cmd = connection.sync();
            // verify connectivity
            String pong = cmd.ping();
            Assumptions.assumeTrue("PONG".equals(pong), () -> "Redis not responding with PONG at " + host + ":" + port);
        } catch (Exception e) {
            // Skip the test if we cannot connect to the local Redis
            Assumptions.assumeTrue(false, () -> "Cannot connect to Redis at " + host + ":" + port + ": " + e.getMessage());
        }
    }

    @AfterAll
    static void teardown() {
        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }
        if (client != null) {
            try { client.shutdown(); } catch (Exception ignored) {}
        }
    }

    @Test
    void loadRandomKeys() {
        Random rnd = new Random();
        long start = System.currentTimeMillis();

        int strings = 0;
        int hashes = 0;
        int lists = 0;
        int sets = 0;
        int zsets = 0;
        int vsets = 0;

        // Distribute keys across all 16 logical DBs (0..15)
        final int totalDbs = 16;
        int basePerDb = keyCount / totalDbs;
        int remainder = keyCount % totalDbs; // first 'remainder' DBs get one extra

        for (int dbIdx = 0; dbIdx < totalDbs; dbIdx++) {
            int toInsert = basePerDb + (dbIdx < remainder ? 1 : 0);
            if (toInsert == 0) continue;

            // Attempt to select DB; if not permitted, continue using current DB
            try {
                cmd.select(dbIdx);
            } catch (Exception ignored) {
                // Some environments may disable SELECT; we'll still insert, but all to current DB
            }

            for (int i = 0; i < toInsert; i++) {
                int t = i % 6; // round-robin types
                switch (t) {
                    case 0 -> { // String
                        String key = keyPrefix + "str:" + dbIdx + ":" + UUID.randomUUID();
                        cmd.set(key, randomAscii(rnd));
                        maybeExpire(key);
                        strings++;
                    }
                    case 1 -> { // Hash
                        String key = keyPrefix + "hash:" + dbIdx + ":" + UUID.randomUUID();
                        int fields = 2 + rnd.nextInt(7); // 2..8 fields
                        for (int f = 0; f < fields; f++) {
                            cmd.hset(key, "f" + f, randomAscii(rnd));
                        }
                        maybeExpire(key);
                        hashes++;
                    }
                    case 2 -> { // List
                        String key = keyPrefix + "list:" + dbIdx + ":" + UUID.randomUUID();
                        int count = 1 + rnd.nextInt(20);
                        for (int j = 0; j < count; j++) {
                            cmd.lpush(key, randomAscii(rnd));
                        }
                        maybeExpire(key);
                        lists++;
                    }
                    case 3 -> { // Set
                        String key = keyPrefix + "set:" + dbIdx + ":" + UUID.randomUUID();
                        int count = 1 + rnd.nextInt(20);
                        for (int j = 0; j < count; j++) {
                            cmd.sadd(key, randomAscii(rnd));
                        }
                        maybeExpire(key);
                        sets++;
                    }
                    case 4 -> { // Sorted set
                        String key = keyPrefix + "zset:" + dbIdx + ":" + UUID.randomUUID();
                        int count = 1 + rnd.nextInt(20);
                        for (int j = 0; j < count; j++) {
                            double score = rnd.nextDouble() * 1000.0;
                            cmd.zadd(key, score, randomAscii(rnd));
                        }
                        maybeExpire(key);
                        zsets++;
                    }
                    case 5 -> { // Vector set (simulate as a Set with vector-like members)
                        String key = keyPrefix + "vset:" + dbIdx + ":" + UUID.randomUUID();
                        int vectors = 1 + rnd.nextInt(10);
                        int dim = 8 + rnd.nextInt(9); // 8..16 dims
                        for (int j = 0; j < vectors; j++) {
                            String vec = randomVectorCsv(rnd, dim);
                            cmd.sadd(key, vec);
                        }
                        maybeExpire(key);
                        vsets++;
                    }
                }
            }
        }

        // Optionally return to the originally configured DB
        if (db != 0) {
            try { cmd.select(db); } catch (Exception ignored) {}
        }

        long ms = System.currentTimeMillis() - start;
        int total = strings + hashes + lists + sets + zsets + vsets;
        System.out.println("[DEBUG_LOG] RedisRandomLoadIT inserted=" + total +
                " keys across 16 DBs into redis://" + host + ":" + port +
                " in " + ms + "ms using prefix '" + keyPrefix + "'" +
                " | types: str=" + strings + ", hash=" + hashes + ", list=" + lists +
                ", set=" + sets + ", zset=" + zsets + ", vset=" + vsets);
    }

    private static void maybeExpire(String key) {
        if (ttlSeconds > 0) {
            try { cmd.expire(key, ttlSeconds); } catch (Exception ignored) {}
        }
    }

    private static String randomAscii(Random rnd) {
        int len = 20 + rnd.nextInt(100); // 20..119 chars
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int t = rnd.nextInt(62);
            char c;
            if (t < 10) c = (char) ('0' + t);
            else if (t < 36) c = (char) ('A' + (t - 10));
            else c = (char) ('a' + (t - 36));
            sb.append(c);
        }
        return sb.toString();
    }

    private static String randomVectorCsv(Random rnd, int dim) {
        StringBuilder sb = new StringBuilder(dim * 6);
        for (int i = 0; i < dim; i++) {
            if (i > 0) sb.append(',');
            // random float in [0,1)
            float v = rnd.nextFloat();
            // limit decimals for compactness
            sb.append(String.format(java.util.Locale.ROOT, "%.4f", v));
        }
        return sb.toString();
    }

    private static String get(String sysProp, String env, String def) {
        String v = System.getProperty(sysProp);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(env);
        if (v != null && !v.isBlank()) return v;
        return def;
    }
}
