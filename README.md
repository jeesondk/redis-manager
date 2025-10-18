# redis-mgr

A full-stack app built with:
- Quarkus (Java backend)
- Quarkus Quinoa (integrates the JS frontend build and dev server)
- Vite + React + Tailwind v4 (frontend in `src/main/webui`)

This README explains how the pieces fit together and how to run the app in development and production.

Useful links when running in dev mode:
- App (SPA): http://localhost:8080/
- Quarkus Dev UI: http://localhost:8080/q/dev-ui
- Base REST API: http://localhost:8080/api
- Example endpoint: http://localhost:8080/api/hello

---

## How it works (Quarkus + Quinoa + Vite)

- The Java backend is a standard Quarkus application. A sample resource is exposed at `/api/hello` that returns JSON.
- The frontend lives under `src/main/webui` and is built by Vite. Tailwind v4 is enabled with a single import in `src/main/webui/src/index.css`.
- Quarkus Quinoa bridges the two:
  - In dev mode, Quinoa will install Node/npm (if not present), start the Vite dev server, and proxy requests from Quarkus (8080) to Vite (5173). You only run `./mvnw quarkus:dev`.
  - In production builds, Quinoa runs `npm run build` and serves the generated static assets from within the Quarkus app. The app is then self-contained at port 8080.

Relevant configuration (already set in `src/main/resources/application.properties`):
- `quarkus.quinoa.ui-dir=src/main/webui` — where the frontend lives
- `quarkus.quinoa.build-dir=dist/client` — where Vite emits the production build
- `quarkus.quinoa.dev-server.port=5173` — Vite dev server port (proxied by Quarkus during dev)
- `quarkus.quinoa.package-manager-install=true` (+ versions) — Quinoa will download its own Node/npm

---

## Run in development (hot reload for both Java and JS)

1) Start Quarkus in dev mode (this also starts Vite via Quinoa):

```
./mvnw quarkus:dev
```

2) Open these URLs:
- App (frontend served via Vite through Quarkus): http://localhost:8080/
- Quarkus Dev UI: http://localhost:8080/q/dev-ui
- Example REST endpoint: http://localhost:8080/api/hello

Changes to Java classes reload automatically, and frontend changes hot-reload through Vite.

Optional: run the frontend standalone (without Quarkus) for pure UI work:

```
cd src/main/webui
npm install
npm run dev
```

This serves the UI at http://localhost:5173/ (Quarkus is not involved in this mode).

---

## Build for production

1) Package the app (Quinoa will build the UI and Quarkus will embed the static assets):

```
./mvnw package
```

2) Run the JAR with required JVM arguments:

```bash
# Option 1: Use the provided script (recommended)
./run-app.sh

# Option 2: Run directly with java -jar
java --add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true -jar target/quarkus-app/quarkus-run.jar
```

**Important:** The JVM arguments `--add-opens java.base/java.lang=ALL-UNNAMED` and `-Dio.netty.noUnsafe=true` are required for Java 17+ to avoid runtime errors and warnings related to JBoss Threads and Netty.

Then open http://localhost:8080/.

Uber-jar and native builds also work as usual:

```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
# Run uber-jar with:
java --add-opens java.base/java.lang=ALL-UNNAMED -Dio.netty.noUnsafe=true -jar target/*-runner.jar

# Native build (no JVM args needed)
./mvnw package -Dnative
```

---

## Project layout

- Backend (Quarkus): `src/main/java/...`
  - Example resource: `GET /api/hello`
- Frontend (Vite + React + Tailwind): `src/main/webui`
  - Dev scripts in `src/main/webui/package.json`
  - Production output: `src/main/webui/dist/client`
- Configuration: `src/main/resources/application.properties`

---

## References

- Quarkus: https://quarkus.io/
- Quarkus Quinoa: https://quarkiverse.github.io/quarkiverse-docs/quarkus-quinoa/dev/
- Vite: https://vitejs.dev/
- React: https://react.dev/
- Tailwind CSS v4: https://tailwindcss.com/blog/tailwindcss-v4
