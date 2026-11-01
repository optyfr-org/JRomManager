#FROM dhi.io/eclipse-temurin:25-alpine
FROM dhi.io/eclipse-temurin@sha256:8994131351999f5bb8d54917d3d5ea9db503ab587724a56a5674bba9283d4d17 AS aot-train

# The base image defaults to nonroot and has no root entry in /etc/passwd;
# the builder stage needs to write /train/work, so use numeric UID 0.
USER 0

# Train the server AOT cache (JEP 483/514) with the exact JVM of the final image,
# so the cache is accepted at runtime. The jrm.aot.train hook loads the fixture DAT,
# runs one scan, then stops the server so the JVM exits and writes the cache.
# The base image is shell-less, so RUN uses exec form (no /bin/sh) and all
# directories come from COPY.
WORKDIR /train
COPY lib *.jar ./
COPY aot-fixture/ work/
RUN ["java", "--enable-native-access=ALL-UNNAMED", "-XX:AOTCacheOutput=/train/JRomManagerServer.aot", "-Djrm.aot.train=true", "-Djrm.aot.fixture=/train/work/aot-fixture.dat", "-cp", "*:lib/*", "jrm.server.Server", "--bind", "127.0.0.1", "--http", "18080", "--work", "/train/work"]

#FROM dhi.io/eclipse-temurin:25-alpine
FROM dhi.io/eclipse-temurin@sha256:8994131351999f5bb8d54917d3d5ea9db503ab587724a56a5674bba9283d4d17

LABEL org.opencontainers.image.title="JRomManager"
LABEL org.opencontainers.image.description="Docker image of JRomManager (with JRE 25)"
LABEL org.opencontainers.image.source="https://github.com/optyfr/JRomManager"

WORKDIR /app

COPY lib *.jar ./
COPY --from=aot-train /train/JRomManagerServer.aot ./aot/JRomManagerServer.aot

ENV JRM_SERVER_WORKPATH=/home/nonroot
ENV JRM_SERVER_HTTP=8080
ENV JRM_SERVER_BIND=0.0.0.0
ENV JRM_SERVER_LOG_LEVEL=INFO

EXPOSE ${JRM_SERVER_HTTP}

VOLUME ["${JRM_SERVER_WORKPATH}"]

USER nonroot

# -XX:AOTCache is best-effort (AOTMode=auto): a missing/invalid cache only warns.
# --sun-misc-unsafe-memory-access=allow silences the Fory Unsafe warning, and
# --enable-native-access=ALL-UNNAMED silences the SevenZipJBinding System.load
# restricted-method warning (classpath mode = unnamed module), as on the jpackage launchers.
ENTRYPOINT ["java", "-XX:AOTCache=/app/aot/JRomManagerServer.aot", "--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED", "-cp", "*:lib/*", "jrm.server.Server"]
