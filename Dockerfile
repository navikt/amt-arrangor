FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY /build/libs/amt-arrangor-*.jar app.jar
ENV TZ="Europe/Oslo"
EXPOSE 8080
CMD ["app.jar"]