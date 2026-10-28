package de.moormann.teamcity.awssm

import de.moormann.teamcity.awssm.AwsSmConstants.ParameterSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AwsSmParameterSettingsTest {

    @Test
    fun `valid settings`() {
        val settings = AwsSmParameterSettings(mapOf(
            ParameterSettings.CONNECTION_ID to "AwsProd",
            ParameterSettings.QUERY to " prod/db!/password ",
            AwsSmConstants.REMOTE_TYPE_ARGUMENT to AwsSmConstants.PARAMETER_TYPE
        ))
        assertEquals("AwsProd", settings.connectionId)
        assertEquals("prod/db!/password", settings.query)
        assertEquals(AwsSmQuery("prod/db", "password"), settings.parsedQuery)
    }

    @Test
    fun `missing connection and query`() {
        val invalids = AwsSmParameterSettings.getInvalidProperties(mapOf(
            ParameterSettings.CONNECTION_ID to ParameterSettings.CONNECTION_NOT_SELECTED_VALUE
        ))
        assertEquals(setOf(ParameterSettings.CONNECTION_ID, ParameterSettings.QUERY), invalids.keys)
        assertThrows<IllegalArgumentException> { AwsSmParameterSettings(emptyMap()) }
    }

    @Test
    fun `malformed query`() {
        val invalids = AwsSmParameterSettings.getInvalidProperties(mapOf(
            ParameterSettings.CONNECTION_ID to "AwsProd",
            ParameterSettings.QUERY to "prod/db!/"
        ))
        assertTrue(invalids.containsKey(ParameterSettings.QUERY))
    }
}
