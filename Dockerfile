# syntax=docker/dockerfile:1
#
# Spring Boot API image, tuned for small containers (Render free tier: 0.1 CPU, 512 MB).
# Measured at --cpus=0.1 --memory=512m (MEASUREMENTS.md, Phase 5): the plain image took minutes to start;
# a CDS archive plus C1-only JIT brings that down by several times. See the notes on each choice below.

# ---- Build: compile and package the jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Dependencies in their own layer, so source-only changes do not re-download them.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp -q dependency:go-offline
COPY src src
# Spring Boot's "extract" layout (app.jar + lib/) is what CDS needs: a plain classpath, no nested jars.
RUN ./mvnw -B -ntp -q -DskipTests package \
    && java -Djarmode=tools -jar target/paris-compass-*.jar extract --destination extracted \
    && mv extracted/paris-compass-*.jar extracted/app.jar

# ---- Runtime: JRE only, non-root, with a CDS archive ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
# Libraries change less often than the app, so they get their own layer.
COPY --from=build /workspace/extracted/lib/ lib/
COPY --from=build /workspace/extracted/app.jar app.jar

# CDS (Class Data Sharing) training run on this exact JVM: start the full Spring context with the
# "cds" profile (no database needed), record every class that loads, and exit. At runtime the JVM maps
# that archive instead of parsing and verifying the same classes again.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Dspring.profiles.active=cds -Dspring.context.exit=onRefresh -jar app.jar \
        > /tmp/cds-training.log 2>&1 \
    && test -s app.jsa && rm /tmp/cds-training.log

USER app
# - MaxRAMPercentage: heap sized from the container limit, leaving room for metaspace and threads.
# - SerialGC: no parallel GC threads competing for a fraction of a CPU.
# - TieredStopAtLevel=1: C1 JIT only. On 0.1 CPU the optimizing C2 compiler competes with startup;
#   peak throughput is lower, which this low-traffic, database-bound API does not need.
# - ExitOnOutOfMemoryError: crash and let the platform restart rather than limp along.
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -XX:SharedArchiveFile=app.jsa"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
