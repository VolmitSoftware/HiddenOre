plugins {
    java
    id("com.gradleup.shadow") version "9.2.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.volmit"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper)
    implementation(libs.bstats)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks{
    jar { enabled = false }
    build { dependsOn(shadowJar) }
    shadowJar {
        archiveClassifier = null
        relocate("org.bstats", "com.volmit.hiddenore.bstats")
    }
    runServer {
        minecraftVersion("1.21.10")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
