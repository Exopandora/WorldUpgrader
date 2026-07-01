import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
	id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
	id("idea")
	id("org.jetbrains.kotlin.jvm") version "2.4.0"
}

val modVersion: String by project
val minecraftVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val loaderVersion: String by project
val fabricKotlinVersion: String by project
val javaVersion: String by project
val javaToolchainVersion: String by project

version = modVersion
group = mavenGroup

base {
	archivesName.set(archivesBaseName)
}

java {
	toolchain.languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
	sourceCompatibility = JavaVersion.toVersion(javaVersion)
	targetCompatibility = JavaVersion.toVersion(javaVersion)
	withSourcesJar()
}

idea {
	module {
		isDownloadSources = true
		isDownloadJavadoc = true
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${minecraftVersion}")
	implementation("net.fabricmc:fabric-loader:${loaderVersion}")
	implementation("net.fabricmc:fabric-language-kotlin:${fabricKotlinVersion}")
}

loom {
	accessWidenerPath = file("src/main/resources/worldupgrader.accesswidener")

	runs {
		named("client") {
			client()
			programArguments.addAll("--username", "Dev")
			jvmArguments.add("-Dworldupgrader.versions=1.21.6")
		}
	}
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${archivesBaseName}" }
	}
}

tasks.withType<ProcessResources> {
	val properties = mapOf(
		"version" to project.version
	)

	inputs.properties(properties)

	filesMatching("fabric.mod.json") {
		expand(properties)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(JavaLanguageVersion.of(javaVersion).asInt())
}

tasks.withType<KotlinJvmCompile>().configureEach {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}
