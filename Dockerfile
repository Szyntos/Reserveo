# Stage 1: build the server distribution
FROM gradle:8.14-jdk17 AS build
WORKDIR /app
# Satisfy AGP's config-time SDK check without installing Android SDK
ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p /opt/android-sdk
COPY . .
RUN echo "sdk.dir=/opt/android-sdk" > local.properties
RUN gradle :server:installDist --no-daemon -x test

# Stage 2: run with a slim JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/build/install/server .
EXPOSE 8080
CMD ["bin/server"]
