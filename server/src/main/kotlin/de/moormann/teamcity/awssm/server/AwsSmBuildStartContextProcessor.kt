package de.moormann.teamcity.awssm.server

import com.intellij.openapi.diagnostic.Logger
import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmParameterSettings
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.BuildSettings
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.buildLog.MessageAttrs
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint

/**
 * Hands the credentials of the AWS connections used by AWS Secrets Manager parameters to the agent.
 * The secrets themselves are fetched on the agent, so their values never pass through the server.
 */
class AwsSmBuildStartContextProcessor(
    private val connectionResolver: AwsSmConnectionResolver
) : BuildStartContextProcessor, PositionAware {

    companion object {
        private val LOG = Logger.getInstance(Loggers.SERVER_CATEGORY + "." + AwsSmBuildStartContextProcessor::class.java.name)
        private const val PROBLEM_TYPE = "AwsSecretsManager"

        fun isAwsSmParameter(parameter: Parameter): Boolean =
            parameter.controlDescription?.parameterTypeArguments?.get(AwsSmConstants.REMOTE_TYPE_ARGUMENT) == AwsSmConstants.PARAMETER_TYPE
    }

    override fun updateParameters(context: BuildStartContext) {
        val build = context.build
        val project = build.buildType?.project ?: return
        val buildSettings = build.buildPromotion.buildSettings

        val awsSmParameters = buildSettings.parametersCollection.filter { isAwsSmParameter(it) }
        if (awsSmParameters.isEmpty()) return

        if (build.buildPromotion.customParameters.isNotEmpty() && !isTrustedTrigger(build)) {
            val parentParameters = (buildSettings as BuildSettings).parentSettings.parametersCollection.associateBy { it.name }
            if (stopBuildIfCustomSpecs(build, awsSmParameters, parentParameters)) return
            restoreCustomValues(context, awsSmParameters, parentParameters)
        }

        val connectionIds = awsSmParameters.mapNotNull { parameter ->
            try {
                AwsSmParameterSettings(parameter.controlDescription!!.parameterTypeArguments).connectionId
            } catch (e: IllegalArgumentException) {
                stopWithProblem(build, "Invalid AWS Secrets Manager parameter '${parameter.name}': ${e.message}", "P_${parameter.name}")
                return
            }
        }.toSet()

        for (connectionId in connectionIds) {
            val credentials = try {
                connectionResolver.requestCredentials(project, connectionId, forBuild = true)
            } catch (e: AwsSmConnectionException) {
                LOG.warnAndDebugDetails("Build $build: ${e.message}", e)
                stopWithProblem(build, e.message ?: "Failed to get AWS credentials", "C_$connectionId")
                return
            }
            context.addSharedParameter(AwsSmConstants.credentialsParameterName(connectionId), credentials.encode())
        }
    }

    // Either no user (e.g. VCS trigger), or a user that is allowed to edit the project
    private fun isTrustedTrigger(build: SRunningBuild): Boolean {
        val user = build.triggeredBy.user ?: return true
        val projectId = build.projectId ?: return true
        return user.isPermissionGrantedForProject(projectId, Permission.EDIT_PROJECT)
    }

    // A custom build must not define new AWS Secrets Manager parameters or change their spec (connection, query)
    private fun stopBuildIfCustomSpecs(build: SRunningBuild, parameters: List<Parameter>, parentParameters: Map<String, Parameter>): Boolean {
        val customSpecs = parameters.filter { parameter ->
            val parent = parentParameters[parameter.name]
            parent == null ||
                parent.controlDescription?.parameterType != parameter.controlDescription?.parameterType ||
                parent.controlDescription?.parameterTypeArguments != parameter.controlDescription?.parameterTypeArguments
        }
        if (customSpecs.isEmpty()) return false

        stopWithProblem(build, "Custom AWS Secrets Manager parameters were added or changed: ${customSpecs.joinToString(", ") { it.name }}. " +
            "Only users with permission to edit the project may do this.", "CUSTOM")
        return true
    }

    // A custom build must not override the query of an AWS Secrets Manager parameter
    private fun restoreCustomValues(context: BuildStartContext, parameters: List<Parameter>, parentParameters: Map<String, Parameter>) {
        val customParameters = context.build.buildPromotion.customParameters
        parameters.filter { customParameters.containsKey(it.name) }.forEach { parameter ->
            context.build.buildLog.messageAsync(
                "Custom value of AWS Secrets Manager parameter '${parameter.name}' is ignored: the user triggering the build lacks permission to edit the project",
                Status.WARNING, MessageAttrs.serverMessage()
            )
            context.addSharedParameter(parameter.name, parentParameters[parameter.name]?.value ?: "")
        }
    }

    private fun stopWithProblem(build: SRunningBuild, message: String, identitySuffix: String) {
        build.addBuildProblem(BuildProblemData.createBuildProblem("AWSSM_${build.buildTypeId}_$identitySuffix", PROBLEM_TYPE, message))
        build.stop(null, message)
    }

    override fun getOrderId() = "AwsSecretsManagerBuildStartContextProcessor"

    override fun getConstraint(): PositionConstraint = PositionConstraint.last()
}
