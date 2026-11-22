# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/sai-backend-*.jar extract --layers --launcher --destination target/extracted

# ONNX-Modell, Tokenizer und DJL-PyTorch-Native-Libs (passend zur Build-Architektur) vorab laden,
# damit der Container zur Laufzeit offline embedden kann.
ENV SAI_MODEL_CACHE=/opt/sai/onnx \
    DJL_CACHE_DIR=/opt/sai/djl
RUN java --enable-native-access=ALL-UNNAMED \
        -Dloader.main=io.github.kirchrath.sai.knowledge.EmbeddingWarmup \
        -cp target/sai-backend-*.jar org.springframework.boot.loader.launch.PropertiesLauncher

FROM eclipse-temurin:25-jre
RUN groupadd --system sai && useradd --system --gid sai --home-dir /app sai \
    && mkdir -p /app /config && chown -R sai:sai /app
WORKDIR /app
COPY --from=build --chown=sai:sai /opt/sai /opt/sai
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER sai
ENV SAI_AGENTS_FILE=/config/agents.yml \
    SAI_MODEL_CACHE=/opt/sai/onnx \
    DJL_CACHE_DIR=/opt/sai/djl \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dai.djl.offline=true"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\n\r\n" >&3 && grep -q "UP" <&3'
# ONNX Runtime und DJL laden native Bibliotheken
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "org.springframework.boot.loader.launch.JarLauncher"]
