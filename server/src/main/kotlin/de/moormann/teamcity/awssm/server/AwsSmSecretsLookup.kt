package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSdkSecretsManagerFetcher
import de.moormann.teamcity.awssm.AwsSmCredentials
import de.moormann.teamcity.awssm.AwsSmQuery
import de.moormann.teamcity.awssm.SecretValueExtractor
import de.moormann.teamcity.awssm.SecretsManagerBrowser
import jetbrains.buildServer.serverSide.SProject

typealias SecretsManagerBrowserFactory = (credentials: AwsSmCredentials, region: String) -> SecretsManagerBrowser

/**
 * Lookups for the parameter spec editor: secret name suggestions and the JSON keys of a secret.
 * Secret values never leave this class.
 */
class AwsSmSecretsLookup(private val connectionResolver: AwsSmConnectionResolver) {

    // replaced in tests
    internal var browserFactory: SecretsManagerBrowserFactory = { credentials, region -> AwsSdkSecretsManagerFetcher(credentials, region) }

    fun listSecretNames(project: SProject, connectionId: String, namePrefix: String): List<String> {
        val credentials = connectionResolver.requestCredentials(project, connectionId, forBuild = false)
        val region = credentials.region.ifBlank { AwsSdkSecretsManagerFetcher.DEFAULT_REGION }
        return browserFactory(credentials, region).use { it.listSecretNames(namePrefix, MAX_SUGGESTIONS) }
    }

    /** @return the top level keys, empty if the secret is not a JSON object */
    fun listKeys(project: SProject, connectionId: String, secretId: String): List<String> {
        val query = AwsSmQuery(secretId.trim(), null)
        val credentials = connectionResolver.requestCredentials(project, connectionId, forBuild = false)
        return browserFactory(credentials, AwsSdkSecretsManagerFetcher.regionFor(query, credentials)).use {
            SecretValueExtractor.listKeys(it.getSecret(query.secretId).secretString)
        }
    }

    companion object {
        const val MAX_SUGGESTIONS = 25
    }
}
