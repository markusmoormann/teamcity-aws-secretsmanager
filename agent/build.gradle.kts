plugins {
    id("io.github.rodm.teamcity-agent")
}

val teamcityVersion = property("teamcityVersion") as String

// The agent provides slf4j-api with a log4j binding; a bundled copy causes a LinkageError
// when the AWS SDK initializes its logger
configurations.runtimeClasspath {
    exclude(group = "org.slf4j")
}

dependencies {
    implementation(project(":common"))

    // AgentRunningBuildEx#getParameterControlDescription is not part of the open API (same as the HashiCorp Vault plugin)
    compileOnly("org.jetbrains.teamcity.internal:agent:$teamcityVersion")

    testImplementation("org.jetbrains.teamcity:agent-api:$teamcityVersion")
    testImplementation("org.jetbrains.teamcity.internal:agent:$teamcityVersion")
}

teamcity {
    version = teamcityVersion

    agent {
        descriptor {
            pluginDeployment {
                useSeparateClassloader = true
            }
        }
        archiveName = "aws-secretsmanager"

        files {
            from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
            into("licenses") {
                from(tasks.named("generateLicenseReport"))
            }
        }
    }
}
