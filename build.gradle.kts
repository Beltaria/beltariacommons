plugins {
    `java-library`
    `maven-publish`
}

group = "fr.beltaria"
version = libraryVersion()
description = "Outils sans etat partages par les plugins Beltaria : textes de lang.yml, placeholders, durees."

val serverVersion: String = providers.gradleProperty("beltaria.serverVersion").get()

/**
 * Version derivee des tags git, comme les plugins : `git tag v1.0.0` fait foi, `-PpluginVersion=1.0.0`
 * court-circuite tout (workflow de release).
 */
fun libraryVersion(): String {
    val forced = providers.gradleProperty("pluginVersion").orNull
    if (forced != null) {
        return forced
    }
    val fallback: String = providers.gradleProperty("beltaria.baseVersion").get()
    if (!file(".git").exists() || git("tag", "-l").isNullOrEmpty()) {
        return "$fallback-SNAPSHOT"
    }
    return git("describe", "--tags", "--dirty")?.removePrefix("v") ?: "$fallback-SNAPSHOT"
}

/** Sortie standard de git, ou null si la commande echoue (archive des sources sans git). */
fun git(vararg args: String): String? = try {
    val output = providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }
    if (output.result.get().exitValue == 0) output.standardOutput.asText.get().trim() else null
} catch (unavailable: Exception) {
    null
}

repositories {
    mavenLocal {
        content { includeGroup("net.beltaria") }
    }
    maven("https://raw.githubusercontent.com/Beltaria/beltariaspigot/main/") {
        content { includeGroup("net.beltaria") }
    }
    maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
        // bungeecord-chat, dependance transitive de l'API.
        content { includeGroup("net.md-5") }
    }
    mavenCentral()
}

configurations.all {
    // L'API est un SNAPSHOT : verifier a chaque build si le depot distant a republie.
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    // Fourni par le serveur : jamais dans le jar de la bibliotheque ni dans celui des plugins.
    compileOnly("net.beltaria:beltariaspigot-api:$serverVersion")

    testImplementation("net.beltaria:beltariaspigot-api:$serverVersion")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // jqwik 1.10 exige JUnit Platform 1.14, d'ou Jupiter 5.14
    testImplementation("net.jqwik:jqwik:1.10.1")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Bytecode 25, comme les plugins et le JDK de la prod.
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-options", "-Werror"))
    }

    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter", "jqwik")
        }
        // ByteBuddy (Mockito) instrumente des classes plus recentes que son plafond (tests sur JDK 25)
        jvmArgs("-Dnet.bytebuddy.experimental=true")
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("commons") {
            artifactId = "commons"
            from(components["java"])
        }
    }
}
