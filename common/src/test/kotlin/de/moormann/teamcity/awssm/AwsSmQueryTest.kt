package de.moormann.teamcity.awssm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AwsSmQueryTest {

    @Test
    fun `secret name without key`() {
        val query = AwsSmQuery.parse("prod/db")
        assertEquals("prod/db", query.secretId)
        assertNull(query.jsonKey)
        assertNull(query.arnRegion)
    }

    @Test
    fun `secret name with key`() {
        val query = AwsSmQuery.parse("prod/db!/password")
        assertEquals("prod/db", query.secretId)
        assertEquals("password", query.jsonKey)
        assertEquals("prod/db!/password", query.toString())
    }

    @Test
    fun `ARN with key`() {
        val query = AwsSmQuery.parse("arn:aws:secretsmanager:eu-central-1:123456789012:secret:prod/db-AbCdEf!/user")
        assertEquals("arn:aws:secretsmanager:eu-central-1:123456789012:secret:prod/db-AbCdEf", query.secretId)
        assertEquals("user", query.jsonKey)
        assertEquals("eu-central-1", query.arnRegion)
    }

    @Test
    fun `value prefix is stripped`() {
        assertEquals(AwsSmQuery("prod/db", "password"), AwsSmQuery.parse("aws-sm:prod/db!/password"))
    }

    @Test
    fun `invalid queries`() {
        assertThrows<IllegalArgumentException> { AwsSmQuery.parse("") }
        assertThrows<IllegalArgumentException> { AwsSmQuery.parse("aws-sm:") }
        assertThrows<IllegalArgumentException> { AwsSmQuery.parse("!/key") }
        assertThrows<IllegalArgumentException> { AwsSmQuery.parse("prod/db!/") }
    }
}
