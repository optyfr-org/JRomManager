FROM dhi.io/eclipse-temurin:25-alpine

LABEL org.opencontainers.image.title "JRomManager"
LABEL org.opencontainers.image.description "Docker image of JRomManager (with JRE 25)"
LABEL org.opencontainers.image.source "https://github.com/optyfr/JRomManager"

WORKDIR /app

COPY lib *.jar ./

ENV JRM_SERVER_WORKPATH=/home/nonroot
ENV JRM_SERVER_HTTP=8080
ENV JRM_SERVER_BIND=0.0.0.0
ENV JRM_SERVER_LOG_LEVEL=INFO

EXPOSE ${JRM_SERVER_HTTP}

VOLUME ["${JRM_SERVER_WORKPATH}"]

USER nonroot

ENTRYPOINT ["java", "-cp", "JRomManager.jar", "jrm.server.Server"]
