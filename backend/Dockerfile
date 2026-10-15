# eclipse-temurin images are multi-arch (amd64 + arm64), so this also builds on Apple Silicon
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
# Use the wrapper so the Gradle version matches local builds and CI
RUN ./gradlew clean installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/pantryman /app
EXPOSE 8000

CMD ["./bin/pantryman", "-port=8000"]
