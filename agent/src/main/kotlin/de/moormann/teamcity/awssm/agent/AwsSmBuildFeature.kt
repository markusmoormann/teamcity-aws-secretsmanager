package de.moormann.teamcity.awssm.agent

import com.intellij.openapi.diagnostic.Logger
import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmParameterSettings
import de.moormann.teamcity.awssm.AwsSmQuery
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.AgentRunningBuild
import jetbrains.buildServer.agent.AgentRunningBuildEx
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.log.Loggers
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.util.positioning.PositionAware
import jetbrains.buildServer.util.positioning.PositionConstraint

class AwsSmBuildFeature(
    dispatcher: EventDispatcher<AgentLifeCycleListener>,
    private val resolver: AwsSmParametersResolver
) : AgentLifeCycleAdapter(), PositionAware {

    companion object {
        private val LOG = Logger.getInstance(Loggers.AGENT_CATEGORY + "." + AwsSmBuildFeature::class.java.name)
    }

    init {
        dispatcher.addListener(this)
        LOG.info("AWS Secrets Manager integration enabled")
    }

    override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
        agent.configuration.addConfigurationParameter(AwsSmConstants.AGENT_SUPPORTED_PARAMETER, "true")
    }

    override fun buildStarted(runningBuild: AgentRunningBuild) {
        val parameters = extractParameters(runningBuild) ?: return
        if (parameters.isEmpty()) return

        val logger = runningBuild.buildLogger
        logger.activityStarted("AWS Secrets Manager", AwsSmConstants.PARAMETER_TYPE)
        try {
            resolver.resolveParameters(runningBuild, parameters)
        } finally {
            logger.activityFinished("AWS Secrets Manager", AwsSmConstants.PARAMETER_TYPE)
        }
    }

    /** @return null if the build was stopped because of an invalid parameter */
    private fun extractParameters(build: AgentRunningBuild): List<AwsSmParameter>? {
        build as AgentRunningBuildEx // required for AgentRunningBuildEx#getParameterControlDescription
        val allParameters = build.sharedConfigParameters + build.sharedBuildParameters.allParameters

        val result = mutableListOf<AwsSmParameter>()
        for ((name, value) in allParameters) {
            val arguments = build.getParameterControlDescription(name)?.parameterTypeArguments ?: continue
            if (arguments[AwsSmConstants.REMOTE_TYPE_ARGUMENT] != AwsSmConstants.PARAMETER_TYPE) continue

            try {
                val settings = AwsSmParameterSettings(arguments)
                // The value carries the query if it was set in the custom build dialog
                val query = if (value.startsWith(AwsSmConstants.VALUE_PREFIX)) AwsSmQuery.parse(value) else settings.parsedQuery
                result += AwsSmParameter(name, settings.connectionId, query)
            } catch (e: IllegalArgumentException) {
                val message = "Failed to parse AWS Secrets Manager parameter settings for parameter '$name': ${e.message}"
                LOG.warnAndDebugDetails(message, e)
                build.buildLogger.internalError(AwsSmConstants.PARAMETER_TYPE, message, e)
                build.stopBuild(message)
                return null
            }
        }
        return result
    }

    override fun getOrderId() = "AwsSecretsManagerParametersResolver"

    override fun getConstraint(): PositionConstraint = PositionConstraint.first()
}
