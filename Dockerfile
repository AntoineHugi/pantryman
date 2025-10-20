FROM gradle:jdk17-alpine AS build
WORKDIR /app
COPY . .
RUN gradle clean installDist --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/pantryman /app

EXPOSE 8000

CMD ["./bin/pantryman"]
