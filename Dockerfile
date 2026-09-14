# --- Stage 1: Jar bauen, inklusive Frontend ueber das frontend-maven-plugin ---
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Erst nur die Build-Beschreibung kopieren und die Abhaengigkeiten aufloesen.
# Diese Schicht bleibt im Cache, solange sich die pom.xml nicht aendert.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline -Dfrontend.skip=true

COPY frontend/ frontend/
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# --- Stage 2: schlankes Laufzeit-Image, nur das Jar ---
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S -g 10001 dashboard \
 && adduser -S -u 10001 -G dashboard -H -s /sbin/nologin dashboard

WORKDIR /app
COPY --from=build --chown=root:root /build/target/dashboard-*.jar /app/dashboard.jar

USER dashboard:dashboard

# Im Container muss an alle Adressen gebunden werden, sonst erreicht die
# Portfreigabe den Dienst nicht. Die Beschraenkung aufs lokale Netz leistet
# hier das Host-Binding 127.0.0.1:8080 in der compose.yml, nicht die
# Anwendung selbst.
ENV SERVER_ADDRESS=0.0.0.0
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# busybox-wget ist im Alpine-Image enthalten, es muss nichts nachinstalliert
# werden. start-period deckt den Anwendungsstart auf schwacher Hardware ab.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/dashboard.jar"]
