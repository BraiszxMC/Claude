plugins {
    java
}

group = "com.braiszx"
version = "1.0.0"

java {
    // Minecraft 1.21.7 corre sobre Java 21.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")

    // BlockBall NO está publicado en Maven Central: hay que meter el jar del
    // plugin (el mismo que va en /plugins) dentro de la carpeta libs/.
    compileOnly(fileTree("libs") { include("*.jar") })

    compileOnly("me.clip:placeholderapi:2.11.6")

    // Solo para que javac entienda las anotaciones de Kotlin del jar de BlockBall.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.25")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("BlockBallRanked-${project.version}.jar")
}
