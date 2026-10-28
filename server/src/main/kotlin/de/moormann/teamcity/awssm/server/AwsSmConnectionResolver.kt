package de.moormann.teamcity.awssm.server

import de.moormann.teamcity.awssm.AwsSmConstants
import de.moormann.teamcity.awssm.AwsSmCredentials
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.connections.ConnectionDescriptor
import jetbrains.buildServer.serverSide.connections.ProjectConnectionsManager
import jetbrains.buildServer.serverSide.connections.credentials.ProjectConnectionCredentialsManager

/**
 * Resolves TeamCity AWS Connections (provided by the bundled aws-core plugin) and requests their credentials.
 */
class AwsSmConnectionResolver(
    private val projectConnectionsManager: ProjectConnectionsManager,
    private val connectionCredentialsManager: ProjectConnectionCredentialsManager
) {
    fun getAwsConnections(project: SProject): List<ConnectionDescriptor> =
        projectConnectionsManager.getAvailableConnectionsOfType(project, AwsSmConstants.AWS_CONNECTION_TYPE)

    /**
     * @param forBuild whether the credentials are passed to a build; then the connection must be available for builds
     */
    @Throws(AwsSmConnectionException::class)
    fun requestCredentials(project: SProject, connectionId: String, forBuild: Boolean): AwsSmCredentials {
        val connection = projectConnectionsManager.findConnectionById(project, connectionId)
            ?: throw AwsSmConnectionException("AWS connection '$connectionId' is not available in project '${project.externalId}'")

        if (connection.connectionProvider.type != AwsSmConstants.AWS_CONNECTION_TYPE) {
            throw AwsSmConnectionException("Connection '$connectionId' is not an AWS connection")
        }
        if (forBuild && connection.parameters[AwsSmConstants.AWS_ALLOWED_IN_BUILDS] == "false") {
            throw AwsSmConnectionException("AWS connection '${connection.displayName}' is not available for builds. Enable 'Available for builds' in the connection settings")
        }

        return try {
            val credentials = connectionCredentialsManager.requestConnectionCredentials(project, connection.id)
            AwsSmCredentials.fromConnectionProperties(credentials.properties)
        } catch (e: Exception) {
            throw AwsSmConnectionException("Failed to get credentials of AWS connection '${connection.displayName}': ${e.message}", e)
        }
    }
}

class AwsSmConnectionException(message: String, cause: Throwable? = null) : Exception(message, cause)
