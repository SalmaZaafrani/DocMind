# Étape 1 : compilation avec Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src src
RUN mvn -B -q -Dmaven.test.skip=true package

# Étape 2 : exécution, avec Tesseract pour l'OCR (français et anglais)
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng libtesseract-dev \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
