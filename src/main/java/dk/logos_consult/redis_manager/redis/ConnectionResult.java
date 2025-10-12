package dk.logos_consult.redis_manager.redis;

public class ConnectionResult {
    public boolean success;
    public String message;
    public String mode; // node|sentinel|cluster
    public String serverInfo; // optional short info

    public static ConnectionResult ok(String mode, String serverInfo) {
        ConnectionResult r = new ConnectionResult();
        r.success = true;
        r.mode = mode;
        r.serverInfo = serverInfo;
        r.message = "OK";
        return r;
    }

    public static ConnectionResult error(String mode, String message) {
        ConnectionResult r = new ConnectionResult();
        r.success = false;
        r.mode = mode;
        r.message = message;
        return r;
    }
}
