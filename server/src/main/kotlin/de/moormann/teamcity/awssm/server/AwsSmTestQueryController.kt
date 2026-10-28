package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSdkSecretsManagerFetcher
import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmParameterSettings
import de.moormann.teamcity.awssm.SecretValueExtractor
import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.controllers.BaseFormXmlController
import jetbrains.buildServer.controllers.BasePropertiesBean
import jetbrains.buildServer.controllers.XmlResponseUtil
import jetbrains.buildServer.controllers.admin.projects.EditVcsRootsController
import jetbrains.buildServer.controllers.admin.projects.PluginPropertiesUtil
import jetbrains.buildServer.serverSide.IOGuard
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.SessionUser
import org.jdom.Element
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * "Test Query" button of the parameter spec editor. Reports only success or failure, never the secret value.
 */
class AwsSmTestQueryController(
    server: SBuildServer,
    wcm: WebControllerManager,
    private val projectManager: ProjectManager,
    private val connectionResolver: AwsSmConnectionResolver
) : BaseFormXmlController(server) {

    companion object {
        const val PATH = "/admin/aws-secretsmanager-test-query.html"
    }

    init {
        wcm.registerController(PATH, this)
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? = null

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse, xmlResponse: Element) {
        val properties = getRequestProperties(request)

        val projectId = properties[AwsSmConstants.PROJECT_ID]
        val project = projectId?.let { projectManager.findProjectByExternalId(it) }
            ?: return fail(xmlResponse, "Project '$projectId' not found")

        val user = SessionUser.getUser(request)
        if (user == null || !user.isPermissionGrantedForProject(project.projectId, Permission.EDIT_PROJECT)) {
            return fail(xmlResponse, "Authorised user lacks permissions for the project: ${project.externalId}")
        }

        val invalids = AwsSmParameterSettings.getInvalidProperties(properties)
        if (invalids.isNotEmpty()) {
            return fail(xmlResponse, "Errors found in the parameter definition: ${invalids.values.joinToString(", ")}")
        }

        try {
            val settings = AwsSmParameterSettings(properties)
            val query = settings.parsedQuery
            IOGuard.allowNetworkCall<Exception> {
                val credentials = connectionResolver.requestCredentials(project, settings.connectionId, forBuild = false)
                AwsSdkSecretsManagerFetcher(credentials, AwsSdkSecretsManagerFetcher.regionFor(query, credentials)).use { fetcher ->
                    SecretValueExtractor.extract(fetcher.getSecret(query.secretId).secretString, query.jsonKey)
                }
            }
            XmlResponseUtil.writeTestResult(xmlResponse, "Success!")
        } catch (e: Exception) {
            fail(xmlResponse, "Error while fetching parameter: ${e.message}")
        }
    }

    private fun fail(xmlResponse: Element, message: String) {
        val errors = ActionErrors()
        errors.addError(EditVcsRootsController.FAILED_TEST_CONNECTION_ERR, message)
        errors.serialize(xmlResponse)
    }

    private fun getRequestProperties(request: HttpServletRequest): Map<String, String> {
        val propertiesBean = BasePropertiesBean(emptyMap())
        PluginPropertiesUtil.bindPropertiesFromRequest(request, propertiesBean)
        return propertiesBean.properties
    }
}
