// This is to suppress false warnings generated by a bug in IntelliJ
@file:Suppress("DSL_SCOPE_VIOLATION", "MISSING_DEPENDENCY_CLASS", "FUNCTION_CALL_EXPECTED", "PropertyName")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	java
	`maven-publish`

	alias(libs.plugins.kotlin)
	alias(libs.plugins.quilt.loom)
}

val archives_base_name: String by project
base.archivesName.set(archives_base_name)

val javaVersion = 17

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	maven("https://oss.sonatype.org/content/repositories/snapshots")
	maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
	repositories {
		exclusiveContent {
			forRepository {
				maven {
					name = "Modrinth"
					url = uri("https://api.modrinth.com/maven")
				}
			}
			filter {
				includeGroup("maven.modrinth")
			}
		}
	}
	maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
}


val transitiveInclude: Configuration by configurations.creating

// All the dependencies are declared at gradle/libs.version.toml and referenced with "libs.<id>"
// See https://docs.gradle.org/current/userguide/platforms.html for information on how version catalogs work.
dependencies {
	implementation("io.ktor:ktor-client-logging-jvm:2.2.4")
    minecraft(libs.minecraft)
	mappings(
		variantOf(libs.quilt.mappings) {
			classifier("intermediary-v2")
		}
	)

	// Replace the above line with the block below if you want to use Mojang mappings as your primary mappings, falling back on QM for parameters and Javadocs
	/*
	mappings(
		loom.layered {
			mappings(variantOf(libs.quilt.mappings) { classifier("intermediary-v2") })
			officialMojangMappings()
		}
	)
	*/

	modImplementation(libs.quilt.loader)
	implementation(libs.kordex.core)
	@Suppress("UnstableApiUsage") transitiveInclude(libs.kordex.core)
	implementation(libs.kordex.pluralkit)
	@Suppress("UnstableApiUsage") transitiveInclude(libs.kordex.pluralkit)

	// QSL is not a complete API; You will need Quilted Fabric API to fill in the gaps.
	// Quilted Fabric API will automatically pull in the correct QSL version.
	modImplementation(libs.qfapi)
	// modImplementation(libs.bundles.qfapi) // If you wish to use the deprecated Fabric API modules

	modImplementation(libs.qkl)
	modCompileOnly(libs.fabric.tailor)

	include(libs.placeholder.api)
	modImplementation(libs.placeholder.api)
}

val includeBlacklist = setOf<String>(
	"commons-validator:commons-validator",
	"commons-beanutils:commons-beanutils",
	"commons-logging:commons-logging",
	"commons-collections:commons-collections",
	"commons-digester:commons-digester",
	"org.jetbrains:annotations",
	"org.jetbrains.kotlin:kotlin-stdlib",
	"org.jetbrains.kotlin:kotlin-stdlib-common",
	"org.jetbrains.kotlin:kotlin-stdlib-jdk7",
	"org.jetbrains.kotlin:kotlin-stdlib-jdk8",
	"org.slf4j:slf4j-api",
	"com.ibm.icu:icu4j"
)

afterEvaluate {
	val ignoredModules = mutableSetOf<String>()
	val mavenCoords = configurations.getByName("transitiveInclude").incoming.resolutionResult.allComponents.filter {
		it.id is ModuleComponentIdentifier
	}.map {
		it.id as ModuleComponentIdentifier
	}

	mavenCoords.forEach {
		if (it.module.endsWith("-jvm")) {
			ignoredModules.add("${it.group}:${it.module.substring(0, it.module.length - 4)}")
		}
	}

	mavenCoords.filter { !ignoredModules.contains("${it.group}:${it.module}") }
		.filter { !includeBlacklist.contains("${it.group}:${it.module}") }
		.map { "${it.group}:${it.module}:${it.version}" }
		.forEach {
			project.dependencies.include(it)
		}
}

tasks {
	withType<KotlinCompile> {
		kotlinOptions {
			jvmTarget = javaVersion.toString()
			// languageVersion: A.B of the kotlin plugin version A.B.C
			languageVersion = libs.plugins.kotlin.get().version.requiredVersion.substringBeforeLast('.')
		}
	}

	withType<JavaCompile>.configureEach {
		options.encoding = "UTF-8"
		options.isDeprecation = true
		options.release.set(javaVersion)
	}

	processResources {
		filteringCharset = "UTF-8"
		inputs.property("version", project.version)

		filesMatching("quilt.mod.json") {
			expand(
				mapOf(
					"version" to project.version
				)
			)
		}
	}

	javadoc {
		options.encoding = "UTF-8"
	}

	// Run `./gradlew wrapper --gradle-version <newVersion>` or `gradle wrapper --gradle-version <newVersion>` to update gradle scripts
	// BIN distribution should be sufficient for the majority of mods
	wrapper {
		distributionType = Wrapper.DistributionType.BIN
	}

	jar {
		from("LICENSE.md") {
			rename { "LICENSE_${archives_base_name}.md" }
		}
	}
}

val targetJavaVersion = JavaVersion.toVersion(javaVersion)
if (JavaVersion.current() < targetJavaVersion) {
	kotlin.jvmToolchain(javaVersion)

	java.toolchain {
		languageVersion.set(JavaLanguageVersion.of(javaVersion))
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	// If this mod is going to be a library, then it should also generate Javadocs in order to aid with development.
	// Uncomment this line to generate them.
	// withJavadocJar()

	// Still required by IDEs such as Eclipse and VSC
	sourceCompatibility = targetJavaVersion
	targetCompatibility = targetJavaVersion
}

// Configure the maven publication
publishing {
	publications {
		register<MavenPublication>("Maven") {
			from(components.getByName("java"))
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
