# JVM Arguments Configuration Guide

This document explains how to ensure the required JVM arguments are applied in different runtime scenarios.

## Required JVM Arguments

The application requires these JVM arguments to run properly on Java 17+:
- `--add-opens java.base/java.lang=ALL-UNNAMED` - Required for JBoss Threads
- `-Dio.netty.noUnsafe=true` - Disables Netty's use of deprecated sun.misc.Unsafe

All configuration is centralized in `application.properties` for consistency.

## Why These Are Needed

### 1. `--add-opens java.base/java.lang=ALL-UNNAMED`
JBoss Threads (used by Quarkus) needs to access internal Java APIs for thread-local reset capabilities. Without this, you'll see:
```
java.lang.IllegalAccessError: module java.base does not open java.lang to unnamed module
```

### 2. `-Dio.netty.noUnsafe=true`
Netty by default uses `sun.misc.Unsafe` for performance. These methods are deprecated and will be removed. Without this, you'll see:
```
WARNING: sun.misc.Unsafe::allocateMemory has been called
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
```

## How to Apply in Different Scenarios

### Scenario 1: Development Mode with Maven

The JVM arguments are already configured in `application.properties`:
```properties
quarkus.dev.jvm-args=--add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true
```

Simply run:
```bash
./mvnw quarkus:dev
```

✅ **Arguments are automatically applied**

---

### Scenario 2: Running Packaged JAR Locally

When you run with `java -jar`, the `quarkus.jvm.args` property in `application.properties` does NOT work because the JVM is already running.

**Option A: Use the provided script (Recommended)**
```bash
./run-app.sh
```

**Option B: Run with explicit arguments**
```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true -jar target/quarkus-app/quarkus-run.jar
```

---

### Scenario 3: Running in Docker

The `Dockerfile.jvm` has been updated with the required arguments:
```dockerfile
ENV JAVA_OPTS_APPEND="... --add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true"
```

Build and run:
```bash
./mvnw package
docker build -f src/main/docker/Dockerfile.jvm -t redis-mgr:latest .
docker run -p 8080:8080 redis-mgr:latest
```

✅ **Arguments are automatically applied via JAVA_OPTS_APPEND**

---

### Scenario 4: Running Tests

Tests are configured in `pom.xml` with the required arguments:
```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>--add-opens java.base/java.lang=ALL-UNNAMED</argLine>
        <systemPropertyVariables>
            <io.netty.noUnsafe>true</io.netty.noUnsafe>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

Simply run:
```bash
./mvnw test
```

✅ **Arguments are automatically applied**

---

### Scenario 5: IDE (IntelliJ IDEA, Eclipse, VS Code)

When running from an IDE, you need to add the JVM arguments to your run configuration:

**IntelliJ IDEA:**
1. Edit Run Configuration
2. Add to "VM options": `--add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true`

**Eclipse:**
1. Run > Run Configurations
2. Arguments tab > VM arguments: `--add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true`

**VS Code (with Java extensions):**
Add to `.vscode/launch.json`:
```json
{
    "configurations": [
        {
            "type": "java",
            "name": "Launch Quarkus App",
            "vmArgs": "--add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true"
        }
    ]
}
```

---

## Quick Reference

| Scenario | How Arguments Are Applied |
|----------|---------------------------|
| `./mvnw quarkus:dev` | ✅ Automatic via `quarkus.dev.jvm-args` |
| `./run-app.sh` | ✅ Automatic via script |
| `java -jar target/...` | ❌ Must add manually |
| Docker | ✅ Automatic via `JAVA_OPTS_APPEND` |
| Tests | ✅ Automatic via `maven-surefire-plugin` |
| IDE | ⚠️ Must configure in run settings |

---

## Verification

To verify the arguments are applied, check the application logs at startup. You should see:
```
Set system property io.netty.noUnsafe=true to disable sun.misc.Unsafe usage
```

And you should NOT see:
- ❌ `IllegalAccessError: module java.base does not open java.lang`
- ❌ `WARNING: sun.misc.Unsafe::allocateMemory has been called`

---

## Files Modified

1. `src/main/docker/Dockerfile.jvm` - Added JVM arguments to `JAVA_OPTS_APPEND`
2. `run-app.sh` - Created convenience script with arguments
3. `src/main/java/.../config/NettyConfigurer.java` - Programmatically sets Netty property
4. `README.md` - Updated with proper run instructions
