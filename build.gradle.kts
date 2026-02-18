
import kotlin.system.exitProcess

plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

group = "art.arcane"
version = "1.1.0-1.21.11"
val apiVersion = "1.21"
val main = "art.arcane.hiddenore.HiddenOre"
val lib = "art.arcane.hiddenore.libs"
val volmLibCoordinate: String = providers.gradleProperty("volmLibCoordinate")
    .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT")
    .get()
val authTokenProperty: String? = providers.gradleProperty("authToken").orNull
val jitpackAuthToken: String? = authTokenProperty ?: providers.gradleProperty("jitpackAuthToken").orNull

registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]/consumers/plugin-consumers/dropins/plugins")

tasks {
    jar { enabled = false }

    build { dependsOn(shadowJar) }

    shadowJar {
        archiveClassifier = null
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        relocate("org.bstats", "$lib.bstats")
    }

    processResources {
        inputs.properties(
            "name" to rootProject.name,
            "version" to version,
            "main" to main,
            "apiVersion" to apiVersion,
        )

        filesMatching("**/plugin.yml") {
            expand(inputs.properties)
        }
    }

    compileJava {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
        options.debugOptions.debugLevel = "none"
        options.release.set(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        if (jitpackAuthToken != null) {
            credentials {
                username = jitpackAuthToken
                password = "."
            }
        }
    }
}

dependencies {
    compileOnly(libs.paper)
    implementation(volmLibCoordinate) {
        isChanging = true
        isTransitive = false
    }
    implementation(libs.bstats)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21 or newer. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21")
    System.err.println("2. Configure the bundled gradle to use Java 21 in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 21 from https://www.oracle.com/java/technologies/downloads/#java21")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}

fun registerCustomOutputTask(name: String, path: String, doRename: Boolean = true) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    createOutputTask(name, path, doRename)
}

fun registerCustomOutputTaskUnix(name: String, path: String, doRename: Boolean = true) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    createOutputTask(name, path, doRename)
}

fun createOutputTask(name: String, path: String, doRename: Boolean = true) {
    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(tasks.shadowJar)
        from(tasks.shadowJar.flatMap { it.archiveFile })
        into(file(path))
        if (doRename) rename { "HiddenOre.jar" }
    }
}
