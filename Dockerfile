FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/pantryman /app

EXPOSE 8080

CMD ["./bin/pantryman"]
