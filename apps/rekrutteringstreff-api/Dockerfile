# Liste over Chainguard images finner du her: https://console.cloud.google.com/artifacts/docker/cgr-nav/europe-north1/pull-through
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre@sha256:58231f5dbdebd2cdad1304b6713f0d693269d6f806b249ca4c4e36f140eb4462
ARG APP_NAME
WORKDIR /$APP_NAME
COPY build/libs/*.jar ./

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

# Asume that logback.xml is located in the project/app root dir.
# The unconventional location is a signal to developers to make them aware that we use this file in an unconventional
# way in the ENTRYPOINT command in this Dockerfile.
COPY logback.xml /

# Set logback.xml explicitly and with an absolute path, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-jar", "app.jar"]

EXPOSE 8080
