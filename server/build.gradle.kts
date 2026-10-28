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
            displayName = "AWS Secrets Manager Support"
            description = "Provides AWS Secrets Manager secrets as TeamCity remote parameters, resolved on the build agent via TeamCity AWS Connections."
            version = project.version.toString()
            vendorName = "Markus Moormann"
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

        archiveName = "aws-secretsmanager-plugin.zip"
    }
}
