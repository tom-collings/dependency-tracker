FROM harbor-dockerhub-docker-virtual.usw1.packages.broadcom.com/maven:latest AS build
RUN mkdir /project
WORKDIR /project
COPY pom.xml /project
RUN mvn verify clean --fail-never
#Maven project restore
COPY . /project
RUN mvn package -DskipTests


FROM harbor-dockerhub-docker-virtual.usw1.packages.broadcom.com/alpine:latest

RUN apk add --no-cache --upgrade openjdk17-jre

RUN apk add --no-cache --upgrade git
RUN apk add --no-cache --upgrade libtasn1


RUN mkdir /app

COPY --from=build /project/target/app-project-metadata-collector-1.0-SNAPSHOT-jar-with-dependencies.jar /app/java-application.jar
WORKDIR /app

ENTRYPOINT [ "java", "-jar",  "java-application.jar" ]