FROM eclipse-temurin:25.0.3_9-jdk

LABEL org.opencontainers.image.description "Docker image of JRomManager (with JDK 21)"
LABEL org.opencontainers.image.source https://github.com/optyfr/JRomManager

WORKDIR /app

COPY lib lib/
COPY *.jar ./

ENV JRM_SERVER_WORKPATH=/work
ENV JRM_SERVER_HTTP=8080
ENV JRM_SERVER_BIND=0.0.0.0
ENV JRM_SERVER_DEBUG=1

EXPOSE ${JRM_SERVER_HTTP}

VOLUME ["${JRM_SERVER_WORKPATH}"]

RUN chown -R 1000:1000 /app
USER 1000

ENTRYPOINT ["java", "-cp", "JRomManager.jar", "jrm.server.Server"]
