package dk.logos_code.redis_manager.redis.datamodels;

import dk.logos_code.redis_manager.redis.ConnectionType;

import java.util.List;

/**
 * A saved connection configuration. Stored in-memory for now.
 */
public class ConnectionConfig {
    public Long id;
    public String name;

    // Reuse request schema for connection details
    public ConnectionType type; // node | sentinel | cluster
    public List<String> urls;
    public String username;
    public String password;
    public String sentinelMasterId;
    public Integer database;
    public Integer timeoutMs;
}
