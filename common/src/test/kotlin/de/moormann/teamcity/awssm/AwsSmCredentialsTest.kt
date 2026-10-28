package de.moormann.teamcity.awssm

import de.moormann.teamcity.awssm.AwsSmConstants.AwsConnectionCredentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AwsSmCredentialsTest {

    @Test
    fun `encode and decode`() {
        val credentials = AwsSmCredentials("AKIA123", "secret", "token", "eu-central-1")
        assertEquals(credentials, AwsSmCredentials.decode(credentials.encode()))
    }

    @Test
    fun `from connection properties`() {
        val credentials = AwsSmCredentials.fromConnectionProperties(mapOf(
            AwsConnectionCredentials.ACCESS_KEY_ID to "AKIA123",
            AwsConnectionCredentials.SECRET_ACCESS_KEY to "secret",
            AwsConnectionCredentials.SESSION_TOKEN to "",
            AwsConnectionCredentials.REGION to "eu-west-1"
        ))
        assertEquals(AwsSmCredentials("AKIA123", "secret", null, "eu-west-1"), credentials)
        assertThrows<IllegalArgumentException> { AwsSmCredentials.fromConnectionProperties(emptyMap()) }
    }

    @Test
    fun `toString does not leak secrets`() {
        val text = AwsSmCredentials("AKIA123", "secret", "token", "eu-central-1").toString()
        assertFalse(text.contains("secret") || text.contains("token"))
    }

    @Test
    fun `region from ARN wins`() {
        val credentials = AwsSmCredentials("a", "b", null, "eu-central-1")
        assertEquals("us-west-2", AwsSdkSecretsManagerFetcher.regionFor(AwsSmQuery.parse("arn:aws:secretsmanager:us-west-2:1:secret:x"), credentials))
        assertEquals("eu-central-1", AwsSdkSecretsManagerFetcher.regionFor(AwsSmQuery.parse("x"), credentials))
        assertEquals("us-east-1", AwsSdkSecretsManagerFetcher.regionFor(AwsSmQuery.parse("x"), credentials.copy(region = "")))
        assertNull(AwsSmQuery.parse("x").arnRegion)
    }
}
