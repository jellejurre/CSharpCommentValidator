FROM openjdk:17-alpine

RUN mkdir /opt/validation

COPY out/artifacts/CSharpCommentValidator_jar/*.jar /opt/validation/