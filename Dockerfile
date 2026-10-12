FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/mcp-domain-0.1.0.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java","-jar","/app/app.jar"]
