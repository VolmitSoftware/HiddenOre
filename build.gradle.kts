plugins {
    java
    id("com.gradleup.shadow") version "9.2.1"
}

group = "com.volmit"
version = "1.0.0"

repositories {
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
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
