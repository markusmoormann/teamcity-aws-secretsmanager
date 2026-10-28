package de.moormann.teamcity.awssm

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.Filter
import software.amazon.awssdk.services.secretsmanager.model.FilterNameStringType
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest

class AwsSdkSecretsManagerFetcher(credentials: AwsSmCredentials, region: String) : SecretsManagerBrowser {

    private val client: SecretsManagerClient = SecretsManagerClient.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(toSdkCredentials(credentials)))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build()

    override fun getSecret(secretId: String): SecretValue {
        val response = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build())
        val secretString = response.secretString()
            ?: throw IllegalArgumentException("The secret '$secretId' has no string value (binary secrets are not supported)")
        return SecretValue(secretString, response.versionId())
    }

    /**
     * Names of the secrets in the region whose name starts with [namePrefix] (requires `secretsmanager:ListSecrets`).
     */
    override fun listSecretNames(namePrefix: String, maxResults: Int): List<String> {
        val request = ListSecretsRequest.builder().maxResults(maxResults)
        if (namePrefix.isNotBlank()) {
            request.filters(Filter.builder().key(FilterNameStringType.NAME).values(namePrefix.trim()).build())
        }
        return client.listSecrets(request.build()).secretList().map { it.name() }.sorted()
    }

    override fun close() = client.close()

    companion object {
        const val DEFAULT_REGION = "us-east-1"

        fun regionFor(query: AwsSmQuery, credentials: AwsSmCredentials): String =
            query.arnRegion ?: credentials.region.ifBlank { DEFAULT_REGION }

        private fun toSdkCredentials(credentials: AwsSmCredentials): AwsCredentials =
            if (credentials.sessionToken.isNullOrBlank()) {
                AwsBasicCredentials.create(credentials.accessKeyId, credentials.secretAccessKey)
            } else {
                AwsSessionCredentials.create(credentials.accessKeyId, credentials.secretAccessKey, credentials.sessionToken)
            }
    }
}
