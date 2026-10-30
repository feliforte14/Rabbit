# =====================================================================
# Rabbit en un contenedor: WildFly 41 (standalone-full) con el WAR ya
# desplegado y toda la configuración aplicada en la construcción.
#
#   docker compose up --build
#
# Etapa 1 compila el WAR (los tests corren en el CI, no acá).
# Etapa 2 baja el driver de PostgreSQL, configura WildFly sin arrancarlo
# (embed-server) y copia el WAR a deployments/.
# Ninguna credencial queda en la imagen: el datasource lee usuario y
# contraseña de variables de entorno al arrancar (ver .env.example).
# =====================================================================

FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /fuente
# Primero el pom solo: las dependencias quedan en caché mientras no cambie.
COPY pom.xml .
COPY .mvn ./.mvn
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk21

ARG POSTGRES_DRIVER=42.7.7
USER jboss

RUN curl -fsSL -o /tmp/postgresql.jar \
      "https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_DRIVER}/postgresql-${POSTGRES_DRIVER}.jar"

COPY --chown=jboss:root docker/wildfly/configurar.cli /tmp/configurar.cli
# El servidor embebido necesita resolver ${env.DB_PASSWORD} para instalar
# el datasource: se le da un valor al azar que solo vive en este RUN (no
# queda en la imagen ni en el repo). En el XML queda la expresión.
RUN DB_PASSWORD="$(head -c 18 /dev/urandom | base64)" /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configurar.cli \
 && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history \
           /opt/jboss/wildfly/standalone/data /opt/jboss/wildfly/standalone/log \
           /tmp/postgresql.jar /tmp/configurar.cli

COPY --from=compilacion --chown=jboss:root /fuente/target/Rabbit.war /opt/jboss/wildfly/standalone/deployments/

EXPOSE 8080 8443 9990
# -b y -bmanagement en 0.0.0.0: dentro del contenedor, para que el puerto
# publicado llegue. jboss-cli se usa con "docker compose exec".
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-full.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
