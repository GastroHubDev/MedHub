# Build multi-estagio. O contexto e a raiz do repositorio porque o reator Maven precisa do
# pom pai e do modulo comum para compilar qualquer servico.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Os poms primeiro, sozinhos: assim a camada de dependencias so e invalidada quando um pom
# muda, e nao a cada alteracao de codigo.
COPY pom.xml .
COPY comum/pom.xml comum/
COPY agendamento-service/pom.xml agendamento-service/
COPY notificacao-service/pom.xml notificacao-service/
COPY historico-service/pom.xml historico-service/
RUN mvn -B -pl agendamento-service -am dependency:go-offline

COPY comum/src comum/src
COPY agendamento-service/src agendamento-service/src
RUN mvn -B -pl agendamento-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sem privilegios: um comprometimento da aplicacao nao vira root no container.
RUN addgroup -S hospital && adduser -S hospital -G hospital
USER hospital

COPY --from=build --chown=hospital:hospital /build/agendamento-service/target/*.jar app.jar

# MaxRAMPercentage em vez de -Xmx fixo: a JVM respeita o limite de memoria do container.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
