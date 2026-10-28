package de.moormann.teamcity.awssm

import de.moormann.teamcity.awssm.AwsSmConstants.ParameterSettings

data class AwsSmParameterSettings(
    val connectionId: String,
    val query: String
) {
    val parsedQuery: AwsSmQuery
        get() = AwsSmQuery.parse(query)

    fun toMap(): Map<String, String> = mapOf(
        ParameterSettings.CONNECTION_ID to connectionId,
        ParameterSettings.QUERY to query
    )

    companion object {
        fun getInvalidProperties(arguments: Map<String, String>): Map<String, String> {
            val invalids = mutableMapOf<String, String>()

            val connectionId = arguments[ParameterSettings.CONNECTION_ID]
            if (connectionId.isNullOrBlank() || connectionId == ParameterSettings.CONNECTION_NOT_SELECTED_VALUE) {
                invalids[ParameterSettings.CONNECTION_ID] = "Please choose an AWS connection"
            }

            val query = arguments[ParameterSettings.QUERY]
            if (query.isNullOrBlank()) {
                invalids[ParameterSettings.QUERY] = "The query is required"
            } else {
                try {
                    AwsSmQuery.parse(query)
                } catch (e: IllegalArgumentException) {
                    invalids[ParameterSettings.QUERY] = e.message ?: "Invalid query"
                }
            }

            return invalids
        }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(arguments: Map<String, String>): AwsSmParameterSettings {
            val invalids = getInvalidProperties(arguments)
            require(invalids.isEmpty()) { invalids.values.first() }

            return AwsSmParameterSettings(
                arguments.getValue(ParameterSettings.CONNECTION_ID),
                arguments.getValue(ParameterSettings.QUERY).trim()
            )
        }
    }
}
