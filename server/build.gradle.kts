plugins {
    id("io.github.rodm.teamcity-server")
}

val teamcityVersion = property("teamcityVersion") as String

// TeamCity provides slf4j-api with a log4j binding; a bundled copy causes a LinkageError
// when the AWS SDK initializes its logger (NoClassDefFoundError: Could not initialize class ...ClientBuilder)
configurations.runtimeClasspath {
    exclude(group = "org.slf4j")
}

dependencies {
    implementation(project(":common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    // Remote parameter API is not part of the open API (same as the HashiCorp Vault plugin)
    compileOnly("org.jetbrains.teamcity.internal:server:$teamcityVersion")
    compileOnly("org.jetbrains.teamcity.internal:web:$teamcityVersion")

    agent(project(path = ":agent", configuration = "plugin"))

    testImplementation("org.jetbrains.teamcity:server-api:$teamcityVersion")
    testImplementation("org.jetbrains.teamcity.internal:server:$teamcityVersion")
}

teamcity {
    version = teamcityVersion

    server {
        descriptor {
            name = "aws-secretsmanager"
            displayName = "AWS Secrets Manager"
            description = "Provides AWS Secrets Manager secrets as TeamCity remote parameters, resolved on the build agent via TeamCity AWS Connections."
            version = project.version.toString()
            vendorName = "Markus Moormann"
            vendorUrl = "https://github.com/markusmoormann"
            useSeparateClassloader = true
            nodeResponsibilitiesAware = true
        }

        files {
            into("kotlin-dsl") {
                from("kotlin-dsl")
            }
            from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
            into("licenses") {
                from(tasks.named("generateLicenseReport"))
            }
        }

        archiveName = "aws-secretsmanager-plugin-${project.version}.zip"

        // JetBrains Marketplace upload (./gradlew :server:publishPlugin), used by .github/workflows/release.yml
        publish {
            token = (findProperty("jetbrains.token") as String?) ?: System.getenv("JETBRAINS_MARKETPLACE_TOKEN")
            channels = listOf((findProperty("jetbrains.channel") as String?) ?: "default")
            notes = (findProperty("jetbrains.notes") as String?)
                ?: rootProject.file("build/release-notes.md").takeIf { it.isFile }?.readText()
        }
    }
}
