FROM gradle:8.8.0-jdk17-alpine AS build
USER root
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/pantryman /app
EXPOSE 80

ENV PORT=80

CMD ["./bin/pantryman"]