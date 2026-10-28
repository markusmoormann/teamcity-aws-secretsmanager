package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSmCredentials
import de.moormann.teamcity.awssm.SecretValue
import de.moormann.teamcity.awssm.SecretsManagerBrowser
import io.mockk.every
import io.mockk.mockk
import jetbrains.buildServer.serverSide.SProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AwsSmSecretsLookupTest {
    private val project = mockk<SProject>()
    private val credentials = AwsSmCredentials("AKIA123", "secretKey", null, "eu-central-1")
    private val resolver = mockk<AwsSmConnectionResolver> {
        every { requestCredentials(project, "AwsProd", false) } returns credentials
    }

    private val regions = mutableListOf<String>()
    private var closed = 0
    private val lookup = AwsSmSecretsLookup(resolver).apply {
        browserFactory = { _, region ->
            regions += region
            FakeBrowser()
        }
    }

    private inner class FakeBrowser : SecretsManagerBrowser {
        private val secrets = mapOf(
            "prod/db" to """{"user":"admin","password":"s3cr3t"}""",
            "prod/token" to "plain-token"
        )

        override fun listSecretNames(namePrefix: String, maxResults: Int) =
            secrets.keys.filter { it.startsWith(namePrefix) }.take(maxResults)

        override fun getSecret(secretId: String) =
            SecretValue(secrets[secretId.substringAfterLast(":")] ?: throw IllegalStateException("Secret not found"), null)

        override fun close() {
            closed++
        }
    }

    @Test
    fun `lists secret names in the connection region`() {
        assertEquals(listOf("prod/db", "prod/token"), lookup.listSecretNames(project, "AwsProd", "prod/"))
        assertEquals(listOf("eu-central-1"), regions)
        assertEquals(1, closed)
    }

    @Test
    fun `lists only key names, never values`() {
        val keys = lookup.listKeys(project, "AwsProd", " prod/db ")
        assertEquals(listOf("user", "password"), keys)
        assertTrue(keys.none { it == "admin" || it == "s3cr3t" })
        assertEquals(1, closed)
    }

    @Test
    fun `non JSON secret has no keys`() {
        assertEquals(emptyList<String>(), lookup.listKeys(project, "AwsProd", "prod/token"))
    }

    @Test
    fun `uses the region of an ARN`() {
        lookup.listKeys(project, "AwsProd", "arn:aws:secretsmanager:us-west-2:123456789012:secret:prod/db")
        assertEquals(listOf("us-west-2"), regions)
    }

    @Test
    fun `missing secret fails and closes the client`() {
        assertThrows<IllegalStateException> { lookup.listKeys(project, "AwsProd", "missing") }
        assertEquals(1, closed)
    }
}
