plugins {
    id("io.github.rodm.teamcity-common")
}

val awsSdkVersion = property("awsSdkVersion") as String

teamcity {
    version = property("teamcityVersion") as String
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    implementation("software.amazon.awssdk:secretsmanager:$awsSdkVersion") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("software.amazon.awssdk:url-connection-client:$awsSdkVersion")
}
