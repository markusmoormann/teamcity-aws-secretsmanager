package de.moormann.teamcity.awssm

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64

/**
 * Credentials of a TeamCity AWS Connection, transferred from the server to the agent.
 */
data class AwsSmCredentials(
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val sessionToken: String? = null,
    val region: String = ""
) {
    override fun toString() = "AwsSmCredentials(accessKeyId=$accessKeyId, region=$region)"

    fun encode(): String = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(this))

    companion object {
        private val mapper = ObjectMapper()

        @Throws(IllegalArgumentException::class)
        fun decode(encoded: String): AwsSmCredentials = try {
            mapper.readValue(Base64.getDecoder().decode(encoded), AwsSmCredentials::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode AWS credentials: ${e.message}", e)
        }

        fun fromConnectionProperties(properties: Map<String, String>): AwsSmCredentials {
            val c = AwsSmConstants.AwsConnectionCredentials
            val accessKeyId = properties[c.ACCESS_KEY_ID]
            val secretAccessKey = properties[c.SECRET_ACCESS_KEY]
            require(!accessKeyId.isNullOrBlank() && !secretAccessKey.isNullOrBlank()) { "The AWS connection did not provide credentials" }
            return AwsSmCredentials(
                accessKeyId,
                secretAccessKey,
                properties[c.SESSION_TOKEN]?.takeIf { it.isNotBlank() },
                properties[c.REGION].orEmpty()
            )
        }
    }
}
