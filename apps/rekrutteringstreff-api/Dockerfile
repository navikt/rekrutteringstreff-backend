FROM gcr.io/distroless/java21
ARG APP_NAME
WORKDIR /$APP_NAME
COPY build/libs/*.jar ./
EXPOSE 8301
CMD ["app.jar"]
