import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("io.github.rodm.teamcity-common") version "1.5.6" apply false
    id("io.github.rodm.teamcity-agent") version "1.5.6" apply false
    id("io.github.rodm.teamcity-server") version "1.5.6" apply false
    id("com.github.jk1.dependency-license-report") version "2.9" apply false
}

group = "de.moormann.teamcity.awssm"
version = (findProperty("PluginVersion") as String?) ?: "1.0.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
        maven("https://download.jetbrains.com/teamcity-repository")
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    dependencies {
        // TeamCity ships its own Kotlin stdlib for plugins compiled with Kotlin; we bundle ours (separate classloader)
        "implementation"(kotlin("stdlib"))

        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("io.mockk:mockk:1.13.17")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Licenses and notices of the bundled third-party libraries, packaged into the plugin ZIPs (see agent/server builds)
configure(listOf(project(":agent"), project(":server"))) {
    apply(plugin = "com.github.jk1.dependency-license-report")

    extensions.configure<com.github.jk1.license.LicenseReportExtension> {
        configurations = arrayOf("runtimeClasspath")
        renderers = arrayOf(com.github.jk1.license.render.TextReportRenderer("THIRD-PARTY-LICENSES.txt"))
        filters = arrayOf(com.github.jk1.license.filter.LicenseBundleNormalizer())
    }
}
