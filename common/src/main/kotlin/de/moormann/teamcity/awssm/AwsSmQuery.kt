package de.moormann.teamcity.awssm

/**
 * A query in the format `<secret name or ARN>[!/<json key>]`.
 *
 * Without a JSON key the whole `SecretString` is returned.
 */
data class AwsSmQuery(val secretId: String, val jsonKey: String?) {

    /** Region encoded in the secret ARN (`arn:aws:secretsmanager:<region>:<account>:secret:<name>`) */
    val arnRegion: String?
        get() {
            if (!secretId.startsWith("arn:")) return null
            return secretId.split(":").getOrNull(3)?.takeIf { it.isNotBlank() }
        }

    override fun toString(): String = if (jsonKey == null) secretId else "$secretId${AwsSmConstants.KEY_SEPARATOR}$jsonKey"

    companion object {
        @Throws(IllegalArgumentException::class)
        fun parse(query: String): AwsSmQuery {
            val trimmed = query.trim().removePrefix(AwsSmConstants.VALUE_PREFIX)
            require(trimmed.isNotEmpty()) { "The query is empty" }

            val separatorIndex = trimmed.indexOf(AwsSmConstants.KEY_SEPARATOR)
            if (separatorIndex < 0) return AwsSmQuery(trimmed, null)

            val secretId = trimmed.substring(0, separatorIndex)
            val jsonKey = trimmed.substring(separatorIndex + AwsSmConstants.KEY_SEPARATOR.length)
            require(secretId.isNotEmpty()) { "The secret name in query '$query' is empty" }
            require(jsonKey.isNotEmpty()) { "The JSON key in query '$query' is empty" }
            return AwsSmQuery(secretId, jsonKey)
        }
    }
}
