FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
RUN chmod +x gradlew

RUN ./gradlew --no-daemon dependencies

COPY src/ src/
RUN ./gradlew --no-daemon shadowJar

RUN cp build/libs/*-all.jar app.jar

ENV APP_PORT="28433" \
    APP_USERAGENT="SkinShuffle/Proxy"
EXPOSE ${APP_PORT}

CMD ["java", "-jar", "app.jar"]