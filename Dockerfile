FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle :server:installDist --no-daemon --stacktrace --info 2>&1 | tail -100

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/server/build/install/server ./server
EXPOSE 8080
CMD ["./server/bin/server"]