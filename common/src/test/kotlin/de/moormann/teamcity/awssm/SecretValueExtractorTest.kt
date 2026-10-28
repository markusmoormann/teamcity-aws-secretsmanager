package de.moormann.teamcity.awssm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecretValueExtractorTest {
    private val json = """{"user":"admin","password":"s3cr3t","port":5432,"nested":{"a":1}}"""

    @Test
    fun `whole secret without key`() {
        assertEquals("plain-token", SecretValueExtractor.extract("plain-token", null))
        assertEquals(json, SecretValueExtractor.extract(json, null))
    }

    @Test
    fun `values by key`() {
        assertEquals("s3cr3t", SecretValueExtractor.extract(json, "password"))
        assertEquals("5432", SecretValueExtractor.extract(json, "port"))
        assertEquals("""{"a":1}""", SecretValueExtractor.extract(json, "nested"))
    }

    @Test
    fun `missing key`() {
        val e = assertThrows<IllegalArgumentException> { SecretValueExtractor.extract(json, "missing") }
        assertEquals("The secret does not contain the key 'missing'", e.message)
    }

    @Test
    fun `list keys`() {
        assertEquals(listOf("user", "password", "port", "nested"), SecretValueExtractor.listKeys(json))
        assertEquals(emptyList<String>(), SecretValueExtractor.listKeys("plain-token"))
        assertEquals(emptyList<String>(), SecretValueExtractor.listKeys("[1,2]"))
        assertEquals(emptyList<String>(), SecretValueExtractor.listKeys("42"))
    }

    @Test
    fun `key on non JSON secret`() {
        assertThrows<IllegalArgumentException> { SecretValueExtractor.extract("plain-token", "password") }
        assertThrows<IllegalArgumentException> { SecretValueExtractor.extract("[1,2]", "password") }
    }
}
