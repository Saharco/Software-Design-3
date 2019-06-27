import il.ac.technion.cs.softwaredesign.lib.db.Document
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentTests {

    @Test
    fun `Document get returns the value associated with a key`() {
        val doc = Document("id1", "user", mapOf(
                "username" to "user1",
                "password" to 1234
        ))

        Assertions.assertEquals(doc.get("username"), "user1")
        Assertions.assertEquals(doc.get("password"), 1234)
    }

    @Test
    fun `Document getAsString returns a valid string value`() {
        val doc = Document("id1", "user", mapOf(
                "key" to "123"
        ))

        Assertions.assertEquals(doc.getAsString("key"), "123")
    }

    @Test
    fun `Document getAsString returns null if the key is not a string`() {
        val doc = Document("id1", "user", mapOf(
                "key" to 123
        ))

        Assertions.assertEquals(doc.getAsString("key"), null)
    }

    @Test
    fun `Document getAsInt returns null if the key is not an integer`() {
        val doc = Document("id1", "user", mapOf(
                "key" to "123"
        ))

        Assertions.assertEquals(doc.getInteger("key"), null)
    }

}