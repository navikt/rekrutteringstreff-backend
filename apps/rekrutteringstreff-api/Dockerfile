FROM gcr.io/distroless/java21
ARG APP_NAME
WORKDIR /$APP_NAME

# Everything in workdir ends up in the Java classpath, see below
COPY build/libs/*.jar ./

# Asume that logback.xml is located in the project/app root dir. Copy to image root dir, not working dir, to avoid duplicates on the classpath
COPY logback.xml /

# Set logback.xml explicitly, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "*", "-jar", "app.jar"]

EXPOSE 8080
