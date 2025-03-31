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
    implementation("org.mineskin:java-client-jsoup:3.0.2")
}