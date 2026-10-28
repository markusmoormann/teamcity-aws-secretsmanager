package de.moormann.teamcity.awssm.agent

import de.moormann.teamcity.awssm.AwsSmQuery

/**
 * A build parameter to be resolved from AWS Secrets Manager.
 *
 * @param name full parameter name, including `env.` or `system.` prefix
 */
data class AwsSmParameter(
    val name: String,
    val connectionId: String,
    val query: AwsSmQuery
)
