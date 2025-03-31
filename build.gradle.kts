plugins {
    id("java")
    application
    id("com.gradleup.shadow") version "+"
}

group = "dev.imb11"
version = "1.0.0"

application {
    mainClass = "dev.imb11.skinshuffleproxy.AppMain"
}

repositories {
    mavenCentral()
    maven("https://repo.inventivetalent.org/repository/public/")
}

dependencies {
    implementation("org.mineskin:java-client:3.0.2")
    implementation("org.mineskin:java-client-java11:3.0.2")
    implementation("io.javalin:javalin:6.5.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}