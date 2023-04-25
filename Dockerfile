FROM ghcr.io/navikt/poao-baseimages/java:17
COPY /build/libs/amt-arrangor-*.jar app.jar
