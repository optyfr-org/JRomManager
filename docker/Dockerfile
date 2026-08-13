FROM eclipse-temurin:25.0.3_9-jdk@sha256:c42fecf62f32725c65cfea284c012526d6fb31cc78123c740ebdc1cfd2dced12

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

RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*

RUN chown -R 1000:1000 /app
USER 1000

ENTRYPOINT ["java", "-cp", "JRomManager.jar", "jrm.server.Server"]
