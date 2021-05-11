FROM openjdk:11.0.11
ADD target/compileta.jar compileta.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "compileta.jar"]