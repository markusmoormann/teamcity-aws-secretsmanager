package de.moormann.teamcity.awssm

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

object SecretValueExtractor {
    private val mapper = ObjectMapper()

    /**
     * Returns the whole secret string if [jsonKey] is null, otherwise the value of the top level key [jsonKey].
     * Non-textual values (numbers, objects, ...) are returned as JSON.
     */
    @Throws(IllegalArgumentException::class)
    fun extract(secretString: String, jsonKey: String?): String {
        if (jsonKey == null) return secretString

        val root: JsonNode = try {
            mapper.readTree(secretString)
        } catch (e: Exception) {
            throw IllegalArgumentException("The secret value is not JSON, cannot read key '$jsonKey'")
        }
        require(root != null && root.isObject) { "The secret value is not a JSON object, cannot read key '$jsonKey'" }

        val node = root.get(jsonKey)
        require(node != null && !node.isNull) { "The secret does not contain the key '$jsonKey'" }
        return if (node.isValueNode) node.asText() else mapper.writeValueAsString(node)
    }

    /**
     * Top level keys of a JSON object secret (for the key dropdown in the UI), empty if the secret is no JSON object.
     */
    fun listKeys(secretString: String): List<String> {
        val root = try {
            mapper.readTree(secretString)
        } catch (e: Exception) {
            return emptyList()
        }
        if (root == null || !root.isObject) return emptyList()
        return root.fieldNames().asSequence().toList()
    }
}
