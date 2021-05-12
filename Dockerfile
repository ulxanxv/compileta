FROM openjdk:8-alpine
ADD target/compileta.jar compileta.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "compileta.jar"]