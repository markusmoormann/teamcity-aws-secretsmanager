package de.moormann.teamcity.awssm

object AwsSmConstants {
    /** Value of the `remoteType` spec argument, analogous to `hashicorp-vault` */
    const val PARAMETER_TYPE = "aws-secrets-manager"
    const val PARAMETER_DESCRIPTION = "AWS Secrets Manager Parameter"

    /** Prefix of the parameter value that stores the query, analogous to `vault:` */
    const val VALUE_PREFIX = "aws-sm:"

    /** Separator between secret id and JSON key, same as in the HashiCorp Vault plugin */
    const val KEY_SEPARATOR = "!/"

    const val REMOTE_TYPE_ARGUMENT = "remoteType"

    const val PARAMETER_PREFIX = "teamcity.awssm"
    const val AGENT_SUPPORTED_PARAMETER = "$PARAMETER_PREFIX.supported"
    const val CREDENTIALS_PARAMETER_PREFIX = "teamcity.internal.awssm.credentials."

    const val PROJECT_ID = "projectId"
    const val BUILD_TYPE_ID = "buildTypeId"

    /** Connection type of the TeamCity AWS Connection (aws-core plugin) */
    const val AWS_CONNECTION_TYPE = "AWS"
    const val AWS_ALLOWED_IN_BUILDS = "awsAllowedInBuilds"

    /** Property names of the credentials returned for a TeamCity AWS Connection */
    object AwsConnectionCredentials {
        const val ACCESS_KEY_ID = "awsAccessKeyId"
        const val SECRET_ACCESS_KEY = "awsSecretAccessKey"
        const val SESSION_TOKEN = "awsSessionToken"
        const val REGION = "awsRegionName"
    }

    object ParameterSettings {
        const val CONNECTION_NOT_SELECTED_VALUE = "connection-not-selected-please-select"
        const val CONNECTION_ID = "teamcity_aws_secretsmanager_connectionId"
        const val QUERY = "teamcity_aws_secretsmanager_query"
    }

    fun credentialsParameterName(connectionId: String) = CREDENTIALS_PARAMETER_PREFIX + connectionId
}
