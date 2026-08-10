import java.io.DataInputStream

plugins {
    java
}

group = "dev.foggy"
version = "2.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("com.github.retrooper:packetevents-spigot:2.13.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 8
    options.compilerArgs.addAll(listOf(
        "-Xlint:all", "-Xlint:-processing", "-Xlint:-deprecation", "-Xlint:-options"
    ))
}

tasks.processResources {
    inputs.property("foggyVersion", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("load")
    }
}

tasks.register<Test>("loadTest") {
    description = "Runs the deterministic visibility-engine throughput test."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform {
        includeTags("load")
    }
    testLogging.showStandardStreams = true
}

val verifyJava8Bytecode by tasks.registering {
    description = "Rejects class files that cannot be loaded by a Java 8 Minecraft server."
    group = "verification"
    dependsOn(tasks.classes)
    doLast {
        fileTree(layout.buildDirectory.dir("classes/java/main")) {
            include("**/*.class")
        }.forEach { classFile ->
            DataInputStream(classFile.inputStream().buffered()).use { input ->
                check(input.readInt() == 0xCAFEBABE.toInt()) {
                    "Invalid class file: $classFile"
                }
                input.readUnsignedShort() // minor version
                val major = input.readUnsignedShort()
                check(major <= 52) {
                    "$classFile has class-file version $major; Foggy requires Java 8 bytecode (52)"
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyJava8Bytecode)
}

tasks.jar {
    archiveBaseName = "Foggy"
    manifest.attributes(
        "Implementation-Title" to "Foggy",
        "Implementation-Version" to project.version
    )
}
