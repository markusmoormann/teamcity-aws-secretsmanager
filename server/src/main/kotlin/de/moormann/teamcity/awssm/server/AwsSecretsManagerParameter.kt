package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmParameterSettings
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.SimpleView
import jetbrains.buildServer.controllers.parameters.InvalidParametersException
import jetbrains.buildServer.controllers.parameters.ParameterContext
import jetbrains.buildServer.controllers.parameters.ParameterEditContext
import jetbrains.buildServer.controllers.parameters.ParameterRenderContext
import jetbrains.buildServer.controllers.parameters.remote.RemoteParameterControlProvider
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.Parameter
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameter
import jetbrains.buildServer.serverSide.parameters.remote.RemoteParameterProvider
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.util.SessionUser
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest

/**
 * Remote parameter type "AWS Secrets Manager Parameter", modelled after the HashiCorp Vault parameter.
 */
class AwsSecretsManagerParameter(
    private val descriptor: PluginDescriptor,
    private val connectionResolver: AwsSmConnectionResolver
) : RemoteParameterProvider, RemoteParameterControlProvider {

    override fun getRemoteParameterType(): String = AwsSmConstants.PARAMETER_TYPE

    override fun getRemoteParameterDescription(): String = AwsSmConstants.PARAMETER_DESCRIPTION

    override fun createRemoteParameter(build: SBuild, parameter: Parameter): RemoteParameter = object : RemoteParameter {
        override fun getValue(): String = parameter.value

        override fun isSecret(): Boolean = true

        override fun getName(): String = parameter.name
    }

    override fun renderControl(request: HttpServletRequest, context: ParameterRenderContext): ModelAndView {
        val modelAndView = fillModel(request, context) ?: return getErrorModel()
        modelAndView.viewName = descriptor.getPluginResourcesPath("editCustomParameter.jsp")
        modelAndView.model["propertiesBean"] = BasePropertiesBean(context.description.parameterTypeArguments)
        return modelAndView
    }

    override fun validateDefaultParameterValue(context: ParameterContext, value: String?) {}

    override fun validateParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): Collection<InvalidProperty> = emptyList()

    // The value stores a representation of the query
    override fun convertParameterValue(request: HttpServletRequest, context: ParameterRenderContext, value: String?): String? =
        if (StringUtil.isNotEmpty(value)) {
            value
        } else {
            AwsSmConstants.VALUE_PREFIX + AwsSmParameterSettings(context.description.parameterTypeArguments).query
        }

    override fun presentParameterValue(context: ParameterContext, value: String?): String = StringUtil.emptyIfNull(value)

    override fun renderSpecEditor(request: HttpServletRequest, context: ParameterEditContext): ModelAndView {
        val modelAndView = fillModel(request, context) ?: return getErrorModel()
        modelAndView.viewName = descriptor.getPluginResourcesPath("editProjectSpec.jsp")
        return modelAndView
    }

    override fun validateSpecEditorParameters(parameters: Map<String, String>): Collection<InvalidProperty> =
        AwsSmParameterSettings.getInvalidProperties(parameters).map { InvalidProperty(it.key, it.value) }

    override fun convertSpecEditorParameters(parameters: Map<String, String>): Map<String, String> = try {
        AwsSmParameterSettings(parameters)
        parameters
    } catch (e: IllegalArgumentException) {
        throw InvalidParametersException(e.localizedMessage, e)
    }

    private fun fillModel(request: HttpServletRequest, context: ParameterContext): ModelAndView? {
        val project = context.getAdditionalParameter(ParameterContext.PROJECT) ?: return null
        val modelAndView = ModelAndView()
        modelAndView.model[AWS_CONNECTIONS] = connectionResolver.getAwsConnections(project)
            .map { AwsConnectionBean(it.id, it.displayName) }
            .sortedBy { it.displayName.lowercase() }
        modelAndView.model[HAS_EDIT_PERMISSION] = hasEditPermission(request, project)
        return modelAndView
    }

    private fun hasEditPermission(request: HttpServletRequest, project: SProject): Boolean =
        SessionUser.getUser(request)?.isPermissionGrantedForProject(project.projectId, Permission.EDIT_PROJECT) ?: false

    private fun getErrorModel() = SimpleView.createTextView("Could not show AWS connections")

    /** JavaBean for the JSP */
    class AwsConnectionBean(val id: String, displayName: String?) {
        val displayName: String = if (displayName.isNullOrBlank()) id else displayName
    }

    companion object {
        const val AWS_CONNECTIONS = "awsConnections"
        const val HAS_EDIT_PERMISSION = "hasEditPermission"
    }
}
