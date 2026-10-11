@file:Suppress("AvoidDuplicateDependencies")

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
    id("maven-publish")
}

version = project.property("mod_version")!!
group = project.property("maven_group")!!

loom {
    accessWidenerPath = file("src/main/resources/oldpackloader.accesswidener")
}

base {
    archivesName.set(project.property("archives_base_name").toString())
}

repositories {
    mavenLocal() // for ResourcePackConverter
}

dependencies {
    include("com.agentdid127.resourcepack:Library:2.2.6")
    implementation("com.agentdid127.resourcepack:Forwards:2.2.6")
    include("com.agentdid127.resourcepack:Forwards:2.2.6")

    // ResourcePackConverter needs this
    include("net.lingala.zip4j:zip4j:2.11.3")

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        java.srcDir("vendor/resourcepackconverter/src/main/java")
        resources.srcDir("vendor/resourcepackconverter/src/main/resources")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))

    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_version" to project.property("minecraft_version"),
                "loader_version" to project.property("loader_version")
            )
        )
    }
}

val targetJavaVersion = 25

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_10)) {
        options.release.set(targetJavaVersion)
    }
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)

    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.jar {
    from(files("licenses/LICENSE-ResourcePackLoader", "licenses/LICENSE-Hypixel")) {
        into("META-INF/licenses/")
    }

    from("LICENSE") {
        rename { "${it}_${project.property("archives_base_name")}" }
    }
}
