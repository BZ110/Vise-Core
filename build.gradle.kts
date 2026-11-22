import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.7"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.albi"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.feathermc.net/artifactory/maven-releases")
    maven("https://repo.minehub.live/releases")
}

dependencies {
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")

    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.digitalingot.feather-server-api:api:0.0.5")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.12.3")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.12.3")

    compileOnly("live.minehub:polarpaper:1.21.11.17")

    implementation("com.samjakob:SpiGUI:1.4.1")
    implementation("redis.clients:jedis:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("org.apache.commons:commons-pool2:2.12.0")
    implementation("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
}

paperweight {
    reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}