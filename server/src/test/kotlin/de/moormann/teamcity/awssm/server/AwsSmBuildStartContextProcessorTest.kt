package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmConstants.ParameterSettings
import de.moormann.teamcity.awssm.AwsSmCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.BuildSettings
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.ControlDescription
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.users.SUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AwsSmBuildStartContextProcessorTest {
    private val credentials = AwsSmCredentials("AKIA123", "secretKey", "sessionToken", "eu-central-1")

    private val project = mockk<SProject>(relaxed = true)
    private val buildSettings = mockk<BuildSettings>(relaxed = true)
    private val parentSettings = mockk<jetbrains.buildServer.serverSide.ReadOnlyBuildTypeSettings>(relaxed = true)
    private val promotion = mockk<BuildPromotion>(relaxed = true)
    private val build = mockk<SRunningBuild>(relaxed = true)
    private val context = mockk<BuildStartContext>(relaxed = true)
    private val resolver = mockk<AwsSmConnectionResolver>()
    private val processor = AwsSmBuildStartContextProcessor(resolver)

    @BeforeEach
    fun setUp() {
        val buildType = mockk<SBuildType>()
        every { buildType.project } returns project
        every { build.buildType } returns buildType
        every { build.buildPromotion } returns promotion
        every { build.projectId } returns "project1"
        every { build.buildTypeId } returns "bt1"
        every { promotion.buildSettings } returns buildSettings
        every { promotion.customParameters } returns emptyMap()
        every { buildSettings.parentSettings } returns parentSettings
        every { context.build } returns build
        every { resolver.requestCredentials(project, "AwsProd", true) } returns credentials
    }

    @Test
    fun `passes credentials of used connections to the agent`() {
        every { buildSettings.parametersCollection } returns listOf(
            awsSmParameter("env.A", "AwsProd", "a!/x"),
            awsSmParameter("env.B", "AwsProd", "b"),
            plainParameter("env.PLAIN")
        )

        processor.updateParameters(context)

        verify(exactly = 1) { resolver.requestCredentials(project, "AwsProd", true) }
        verify { context.addSharedParameter(AwsSmConstants.credentialsParameterName("AwsProd"), credentials.encode()) }
        verify(exactly = 0) { build.stop(any(), any()) }
    }

    @Test
    fun `does nothing without AWS Secrets Manager parameters`() {
        every { buildSettings.parametersCollection } returns listOf(plainParameter("env.PLAIN"))

        processor.updateParameters(context)

        verify(exactly = 0) { resolver.requestCredentials(any(), any(), any()) }
        verify(exactly = 0) { context.addSharedParameter(any(), any()) }
    }

    @Test
    fun `stops build if connection is not usable`() {
        every { buildSettings.parametersCollection } returns listOf(awsSmParameter("env.A", "Missing", "a"))
        every { resolver.requestCredentials(project, "Missing", true) } throws AwsSmConnectionException("AWS connection 'Missing' is not available")

        processor.updateParameters(context)

        verify { build.addBuildProblem(any()) }
        verify { build.stop(null, "AWS connection 'Missing' is not available") }
        verify(exactly = 0) { context.addSharedParameter(any(), any()) }
    }

    @Test
    fun `untrusted custom build must not change the parameter spec`() {
        untrustedCustomBuild(mapOf("env.A" to "aws-sm:other!/x"))
        every { buildSettings.parametersCollection } returns listOf(awsSmParameter("env.A", "AwsProd", "other!/x"))
        every { parentSettings.parametersCollection } returns listOf(awsSmParameter("env.A", "AwsProd", "a!/x"))

        processor.updateParameters(context)

        verify { build.stop(null, match { it.contains("env.A") }) }
        verify(exactly = 0) { resolver.requestCredentials(any(), any(), any()) }
    }

    @Test
    fun `untrusted custom build value is replaced by the configured value`() {
        untrustedCustomBuild(mapOf("env.A" to "aws-sm:other!/x"))
        every { buildSettings.parametersCollection } returns listOf(awsSmParameter("env.A", "AwsProd", "a!/x"))
        every { parentSettings.parametersCollection } returns listOf(awsSmParameter("env.A", "AwsProd", "a!/x"))

        processor.updateParameters(context)

        verify { context.addSharedParameter("env.A", "aws-sm:a!/x") }
        verify { context.addSharedParameter(AwsSmConstants.credentialsParameterName("AwsProd"), credentials.encode()) }
        verify(exactly = 0) { build.stop(any(), any()) }
    }

    private fun untrustedCustomBuild(customParameters: Map<String, String>) {
        val user = mockk<SUser>()
        every { user.isPermissionGrantedForProject("project1", Permission.EDIT_PROJECT) } returns false
        every { build.triggeredBy.user } returns user
        every { promotion.customParameters } returns customParameters
    }

    private fun awsSmParameter(name: String, connectionId: String, query: String): Parameter =
        parameter(name, "aws-sm:$query", mapOf(
            AwsSmConstants.REMOTE_TYPE_ARGUMENT to AwsSmConstants.PARAMETER_TYPE,
            ParameterSettings.CONNECTION_ID to connectionId,
            ParameterSettings.QUERY to query
        ))

    private fun plainParameter(name: String): Parameter = parameter(name, "value", null)

    private fun parameter(name: String, value: String, arguments: Map<String, String>?): Parameter {
        val parameter = mockk<Parameter>()
        every { parameter.name } returns name
        every { parameter.value } returns value
        every { parameter.controlDescription } returns arguments?.let { args ->
            mockk<ControlDescription> {
                every { parameterType } returns "remote"
                every { parameterTypeArguments } returns args
            }
        }
        return parameter
    }
}
