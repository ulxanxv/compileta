FROM openjdk:11

RUN mkdir /classes

ADD target/compileta.jar compileta.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "compileta.jar"]