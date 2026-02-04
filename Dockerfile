FROM gradle:8.8.0-jdk17-alpine AS build
USER root
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/pantryman /app
EXPOSE 8080

ENV PORT=8080

CMD ["./bin/pantryman"]