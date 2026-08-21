FROM gradle:9.5.1-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN cp settings.server.gradle.kts settings.gradle.kts && gradle --no-daemon :server:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/server/ /app/
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
