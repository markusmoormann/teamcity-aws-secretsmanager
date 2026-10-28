package de.moormann.teamcity.awssm.agent

import de.moormann.teamcity.awssm.AwsSdkSecretsManagerFetcher
import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmCredentials
import de.moormann.teamcity.awssm.SecretValue
import de.moormann.teamcity.awssm.SecretValueExtractor
import de.moormann.teamcity.awssm.SecretsManagerFetcher
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.Constants
import java.io.Closeable

typealias SecretsManagerFetcherFactory = (credentials: AwsSmCredentials, region: String) -> SecretsManagerFetcher

class AwsSmParametersResolver(
    private val fetcherFactory: SecretsManagerFetcherFactory
) {
    // Spring bean constructor
    constructor() : this({ credentials, region -> AwsSdkSecretsManagerFetcher(credentials, region) })

    /**
     * Fetches the secrets and sets them as (masked) build parameters.
     * On failure a build problem is reported and the build is stopped.
     *
     * @return true if all parameters were resolved
     */
    fun resolveParameters(build: AgentRunningBuild, parameters: List<AwsSmParameter>): Boolean {
        var secretCount = 0
        for ((connectionId, connectionParameters) in parameters.groupBy { it.connectionId }) {
            val credentials = takeCredentials(build, connectionId) ?: return false
            secretCount += resolveWithCredentials(build, connectionId, credentials, connectionParameters) ?: return false
        }
        build.buildLogger.message("Resolved ${parameters.size} parameter(s) from $secretCount secret(s)")
        return true
    }

    private fun takeCredentials(build: AgentRunningBuild, connectionId: String): AwsSmCredentials? {
        val parameterName = AwsSmConstants.credentialsParameterName(connectionId)
        val encoded = build.sharedConfigParameters[parameterName]
        if (encoded.isNullOrBlank()) {
            fail(build, "C_$connectionId", "The server did not provide credentials of AWS connection '$connectionId'")
            return null
        }

        return try {
            AwsSmCredentials.decode(encoded).also { credentials ->
                build.passwordReplacer.addPassword(credentials.secretAccessKey)
                credentials.sessionToken?.let { build.passwordReplacer.addPassword(it) }
            }
        } catch (e: IllegalArgumentException) {
            fail(build, "C_$connectionId", e.message ?: "Failed to decode AWS credentials")
            null
        } finally {
            // Credentials are only needed here, don't expose them to build steps
            build.addSharedConfigParameter(parameterName, "")
        }
    }

    private fun resolveWithCredentials(
        build: AgentRunningBuild,
        connectionId: String,
        credentials: AwsSmCredentials,
        parameters: List<AwsSmParameter>
    ): Int? {
        val connectionRegion = credentials.region.ifBlank { AwsSdkSecretsManagerFetcher.DEFAULT_REGION }
        build.buildLogger.message("Connection '$connectionId' (region $connectionRegion, access key ${abbreviate(credentials.accessKeyId)})")

        val fetchers = mutableMapOf<String, SecretsManagerFetcher>()
        val secrets = mutableMapOf<Pair<String, String>, SecretValue>()
        try {
            for (parameter in parameters) {
                val query = parameter.query
                val region = AwsSdkSecretsManagerFetcher.regionFor(query, credentials)
                val secret: SecretValue
                val value = try {
                    secret = secrets.getOrPut(region to query.secretId) {
                        fetchers.getOrPut(region) { fetcherFactory(credentials, region) }.getSecret(query.secretId)
                    }
                    SecretValueExtractor.extract(secret.secretString, query.jsonKey)
                } catch (e: Exception) {
                    fail(build, "P_${parameter.name}",
                        "Failed to resolve AWS Secrets Manager parameter '${parameter.name}' (query '$query', connection '$connectionId'): ${e.message}")
                    return null
                }

                if (value.isNotEmpty()) build.passwordReplacer.addPassword(value)
                setParameter(build, parameter.name, value)
                build.buildLogger.message(describe(parameter, region.takeIf { it != connectionRegion }, secret.versionId))
            }
            return secrets.size
        } finally {
            fetchers.values.forEach { (it as? Closeable)?.close() }
        }
    }

    // Names only, never values, lengths or hashes of values
    private fun describe(parameter: AwsSmParameter, deviatingRegion: String?, versionId: String?): String =
        "${parameter.name} <- ${parameter.query}" +
            (deviatingRegion?.let { " [$it]" } ?: "") +
            (versionId?.let { " (version ${it.take(8)})" } ?: "")

    private fun abbreviate(accessKeyId: String): String =
        if (accessKeyId.length <= 8) accessKeyId else "${accessKeyId.take(4)}…${accessKeyId.takeLast(4)}"

    private fun setParameter(build: AgentRunningBuild, name: String, value: String) = when {
        name.startsWith(Constants.ENV_PREFIX) -> build.addSharedEnvironmentVariable(name.removePrefix(Constants.ENV_PREFIX), value)
        name.startsWith(Constants.SYSTEM_PREFIX) -> build.addSharedSystemProperty(name.removePrefix(Constants.SYSTEM_PREFIX), value)
        else -> build.addSharedConfigParameter(name, value)
    }

    private fun fail(build: AgentRunningBuild, identitySuffix: String, message: String) {
        build.buildLogger.logBuildProblem(
            BuildProblemData.createBuildProblem("AWSSM_${build.buildTypeId}_$identitySuffix", PROBLEM_TYPE, message)
        )
        build.stopBuild(message)
    }

    companion object {
        const val PROBLEM_TYPE = "AwsSecretsManager"
    }
}
