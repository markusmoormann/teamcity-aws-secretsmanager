package de.moormann.teamcity.awssm

/**
 * Reads the `SecretString` of a secret. Implemented with the AWS SDK on the server (test query) and the agent.
 */
fun interface SecretsManagerFetcher {
    @Throws(Exception::class)
    fun getSecret(secretId: String): SecretValue
}

/**
 * Additional read operations used by the parameter editor on the server.
 */
interface SecretsManagerBrowser : SecretsManagerFetcher, java.io.Closeable {
    @Throws(Exception::class)
    fun listSecretNames(namePrefix: String, maxResults: Int): List<String>
}

/**
 * @param versionId version of the secret, logged for traceability (e.g. after a rotation)
 */
class SecretValue(val secretString: String, val versionId: String?) {
    override fun toString() = "SecretValue(versionId=$versionId)"
}
