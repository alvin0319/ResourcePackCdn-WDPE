plugins {
    kotlin("jvm") version "1.9.22"
    id("io.github.goooler.shadow") version "8.1.5"
    `java-library`
    idea
}

group = "dev.minjae.wdpe.resourcepackcdn"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "opencollab-repo-snapshots-mirror"
        url = uri("https://repo.minjae.dev/opencollab-snapshots-mirror")
    }
    maven {
        url = uri("https://repo.waterdog.dev/snapshots")
    }
    maven {
        url = uri("https://repo.opencollab.dev/maven-snapshots")
    }
    maven {
        url = uri("https://repo.opencollab.dev/maven-releases")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("org.cloudburstmc:nbt:3.0.0.Final")
    compileOnly("org.cloudburstmc.math:immutable:2.0-SNAPSHOT")
    compileOnly("dev.waterdog.waterdogpe:waterdog:2.0.3-SNAPSHOT") {
        exclude(group = "org.cloudburstmc.protocol", module = "bedrock-codec")
    }
    compileOnly("org.cloudburstmc.protocol:bedrock-codec:3.0.0.Beta5-SNAPSHOT")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-blackbird:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    implementation("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.12")
    implementation("io.javalin:javalin:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}
tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("com.fasterxml.jackson.module.kotlin", "dev.minjae.wdpe.resourcepackcdn.jackson.kotlin")
        relocate("com.fasterxml.jackson.module.blackbird", "dev.minjae.wdpe.resourcepackcdn.jackson.blackbird")
        relocate("com.fasterxml.jackson.dataformat.yaml", "dev.minjae.wdpe.resourcepackcdn.jackson.yaml")
        relocate("com.squareup.okhttp3", "dev.minjae.wdpe.resourcepackcdn.okhttp3")
        relocate("io.javalin", "dev.minjae.wdpe.resourcepackcdn.javalin")

        if (System.getenv("ARTIFACT_OUTPUT_DIR") != null) {
            destinationDirectory.set(file(System.getenv("ARTIFACT_OUTPUT_DIR")))
        }
    }
}

kotlin {
    jvmToolchain(19)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}