package de.moormann.teamcity.awssm.server

import com.fasterxml.jackson.databind.ObjectMapper
import de.moormann.teamcity.awssm.AwsSmConstants
import jetbrains.buildServer.controllers.BaseController
import jetbrains.buildServer.serverSide.IOGuard
import jetbrains.buildServer.serverSide.ProjectManager
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.serverSide.auth.Permission
import jetbrains.buildServer.web.openapi.WebControllerManager
import jetbrains.buildServer.web.util.SessionUser
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * JSON endpoint of the parameter spec editor (read only, hence GET):
 * - `?lookup=secrets&projectId=..&connectionId=..&prefix=..` → `{"secrets":["prod/db", ...]}`
 * - `?lookup=keys&projectId=..&connectionId=..&secretId=..` → `{"keys":["user","password"]}`
 *
 * Errors are returned as `{"error":"..."}`. Secret values are never returned.
 */
class AwsSmSecretsLookupController(
    server: SBuildServer,
    wcm: WebControllerManager,
    private val projectManager: ProjectManager,
    private val lookup: AwsSmSecretsLookup
) : BaseController(server) {

    private val mapper = ObjectMapper()

    companion object {
        const val PATH = "/admin/aws-secretsmanager-lookup.html"
    }

    init {
        wcm.registerController(PATH, this)
    }

    override fun doHandle(request: HttpServletRequest, response: HttpServletResponse): ModelAndView? {
        val result: Map<String, Any> = try {
            handle(request)
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: e.javaClass.simpleName))
        }

        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-store")
        mapper.writeValue(response.writer, result)
        return null
    }

    private fun handle(request: HttpServletRequest): Map<String, Any> {
        val projectId = request.getParameter(AwsSmConstants.PROJECT_ID)
        val project = projectId?.let { projectManager.findProjectByExternalId(it) }
            ?: return mapOf("error" to "Project '$projectId' not found")

        val user = SessionUser.getUser(request)
        if (user == null || !user.isPermissionGrantedForProject(project.projectId, Permission.EDIT_PROJECT)) {
            return mapOf("error" to "Authorised user lacks permissions for the project: ${project.externalId}")
        }

        val connectionId = request.getParameter("connectionId")
        if (connectionId.isNullOrBlank() || connectionId == AwsSmConstants.ParameterSettings.CONNECTION_NOT_SELECTED_VALUE) {
            return mapOf("error" to "Please choose an AWS connection")
        }

        return when (request.getParameter("lookup")) {
            "secrets" -> IOGuard.allowNetworkCall<Map<String, Any>, Exception> {
                mapOf("secrets" to lookup.listSecretNames(project, connectionId, request.getParameter("prefix").orEmpty()))
            }
            "keys" -> {
                val secretId = request.getParameter("secretId")
                if (secretId.isNullOrBlank()) return mapOf("error" to "Please enter a secret name or ARN")
                IOGuard.allowNetworkCall<Map<String, Any>, Exception> {
                    mapOf("keys" to lookup.listKeys(project, connectionId, secretId))
                }
            }
            else -> mapOf("error" to "Unknown lookup")
        }
    }
}
