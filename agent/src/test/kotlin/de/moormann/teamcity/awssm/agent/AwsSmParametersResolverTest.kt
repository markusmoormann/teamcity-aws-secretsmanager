package de.moormann.teamcity.awssm.agent

import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmCredentials
import de.moormann.teamcity.awssm.AwsSmQuery
import de.moormann.teamcity.awssm.SecretValue
import de.moormann.teamcity.awssm.SecretsManagerFetcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.util.PasswordReplacer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AwsSmParametersResolverTest {
    private val credentials = AwsSmCredentials("AKIAEXAMPLEA123", "secretKey", "sessionToken", "eu-central-1")
    private val secrets = mapOf(
        "prod/db" to """{"user":"admin","password":"s3cr3t"}""",
        "api-token" to "plain-token"
    )

    private val passwordReplacer = mockk<PasswordReplacer>(relaxed = true)
    private val logger = mockk<BuildProgressLogger>(relaxed = true)
    private val build = mockk<AgentRunningBuild>(relaxed = true)
    private val fetchCalls = mutableListOf<Pair<String, String>>()
    private val regions = mutableListOf<String>()

    private val resolver = AwsSmParametersResolver { creds, region ->
        assertEquals(credentials, creds)
        regions += region
        SecretsManagerFetcher { secretId ->
            fetchCalls += region to secretId
            val secretString = secrets[secretId] ?: throw IllegalStateException("Secrets Manager can't find the specified secret.")
            SecretValue(secretString, "3f2a9c1e-0000-4000-8000-000000000000")
        }
    }

    @BeforeEach
    fun setUp() {
        every { build.passwordReplacer } returns passwordReplacer
        every { build.buildLogger } returns logger
        every { build.buildTypeId } returns "Bt"
        every { build.sharedConfigParameters } returns mapOf(
            AwsSmConstants.credentialsParameterName("AwsProd") to credentials.encode()
        )
    }

    private fun resolve(resolver: AwsSmParametersResolver, parameters: List<AwsSmParameter>): Boolean =
        resolver.resolveParameters(build, parameters, resolver.takeAllCredentials(build))

    @Test
    fun `resolves and masks parameters`() {
        val ok = resolve(resolver, listOf(
            AwsSmParameter("env.DB_USER", "AwsProd", AwsSmQuery.parse("prod/db!/user")),
            AwsSmParameter("system.db.password", "AwsProd", AwsSmQuery.parse("prod/db!/password")),
            AwsSmParameter("apiToken", "AwsProd", AwsSmQuery.parse("api-token"))
        ))

        assertTrue(ok)
        verify { build.addSharedEnvironmentVariable("DB_USER", "admin") }
        verify { build.addSharedSystemProperty("db.password", "s3cr3t") }
        verify { build.addSharedConfigParameter("apiToken", "plain-token") }

        listOf("admin", "s3cr3t", "plain-token", "secretKey", "sessionToken", credentials.encode()).forEach {
            verify { passwordReplacer.addPassword(it) }
        }
        // credentials are removed from the build parameters
        verify { build.addSharedConfigParameter(AwsSmConstants.credentialsParameterName("AwsProd"), "") }
        // each secret is fetched once, with one client per region
        assertEquals(listOf("eu-central-1" to "prod/db", "eu-central-1" to "api-token"), fetchCalls)
        assertEquals(listOf("eu-central-1"), regions)
        verify(exactly = 0) { build.stopBuild(any()) }

        // the log names connection, queries and versions, never values
        verify { logger.message("Connection 'AwsProd' (region eu-central-1, access key AKIA…A123)") }
        verify { logger.message("env.DB_USER <- prod/db!/user (version 3f2a9c1e)") }
        verify { logger.message("apiToken <- api-token (version 3f2a9c1e)") }
        verify { logger.message("Resolved 3 parameter(s) from 2 secret(s)") }
        verify(exactly = 0) { logger.message(match { msg -> listOf("admin", "s3cr3t", "plain-token", "secretKey").any { msg.contains(it) } }) }
    }

    @Test
    fun `uses the region of an ARN`() {
        val arn = "arn:aws:secretsmanager:us-west-2:123456789012:secret:api-token"
        val resolver = AwsSmParametersResolver { _, region ->
            regions += region
            SecretsManagerFetcher { SecretValue("value", null) }
        }

        assertTrue(resolve(resolver, listOf(AwsSmParameter("token", "AwsProd", AwsSmQuery.parse(arn)))))
        assertEquals(listOf("us-west-2"), regions)
        verify { logger.message("token <- $arn [us-west-2]") }
    }

    @Test
    fun `stops build if secret is missing`() {
        val ok = resolve(resolver, listOf(
            AwsSmParameter("env.MISSING", "AwsProd", AwsSmQuery.parse("missing!/x"))
        ))

        assertFalse(ok)
        verify { build.stopBuild(match { it.contains("env.MISSING") && it.contains("can't find the specified secret") }) }
    }

    @Test
    fun `stops build if key is missing`() {
        val ok = resolve(resolver, listOf(
            AwsSmParameter("env.X", "AwsProd", AwsSmQuery.parse("prod/db!/nope"))
        ))

        assertFalse(ok)
        verify { build.stopBuild(match { it.contains("does not contain the key 'nope'") }) }
    }

    @Test
    fun `stops build if server provided no credentials`() {
        val ok = resolve(resolver, listOf(
            AwsSmParameter("env.X", "OtherConnection", AwsSmQuery.parse("prod/db"))
        ))

        assertFalse(ok)
        assertTrue(fetchCalls.isEmpty())
        verify { build.stopBuild(match { it.contains("OtherConnection") }) }
    }

    @Test
    fun `masks and clears credentials of all connections even if an earlier one fails`() {
        val other = AwsSmCredentials("AKIAOTHER0000000", "otherSecretKey", null, "us-east-1")
        every { build.sharedConfigParameters } returns mapOf(
            AwsSmConstants.credentialsParameterName("AwsProd") to credentials.encode(),
            AwsSmConstants.credentialsParameterName("AwsOther") to other.encode()
        )

        val ok = resolve(resolver, listOf(
            AwsSmParameter("env.MISSING", "AwsProd", AwsSmQuery.parse("missing")),
            AwsSmParameter("env.OTHER", "AwsOther", AwsSmQuery.parse("api-token"))
        ))

        assertFalse(ok)
        listOf(credentials.encode(), other.encode(), "secretKey", "sessionToken", "otherSecretKey").forEach {
            verify { passwordReplacer.addPassword(it) }
        }
        verify { build.addSharedConfigParameter(AwsSmConstants.credentialsParameterName("AwsProd"), "") }
        verify { build.addSharedConfigParameter(AwsSmConstants.credentialsParameterName("AwsOther"), "") }
    }

    @Test
    fun `masks and clears undecodable credentials`() {
        every { build.sharedConfigParameters } returns mapOf(AwsSmConstants.credentialsParameterName("Broken") to "bm90LWpzb24=")

        val ok = resolve(resolver, listOf(AwsSmParameter("env.X", "Broken", AwsSmQuery.parse("prod/db"))))

        assertFalse(ok)
        verify { passwordReplacer.addPassword("bm90LWpzb24=") }
        verify { build.addSharedConfigParameter(AwsSmConstants.credentialsParameterName("Broken"), "") }
        verify { build.stopBuild(match { it.contains("Failed to decode AWS credentials") }) }
    }
}
