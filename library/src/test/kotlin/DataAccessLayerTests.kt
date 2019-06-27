import il.ac.technion.cs.softwaredesign.lib.db.dal.DocumentDataAccessLayer
import il.ac.technion.cs.softwaredesign.lib.mock.SecureStorageFactoryFake
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import org.junit.jupiter.api.*
import java.util.concurrent.CompletableFuture

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataAccessLayerTests {

//    private val database: MutableMap<String, ByteArray> = HashMap()
    private lateinit var factory: SecureStorageFactory
    private lateinit var storageLayer: CompletableFuture<SecureStorage>
//    private val key = slot<ByteArray>()
//    private val objValue = slot<ByteArray>()
    private lateinit var dal: DocumentDataAccessLayer

    @BeforeEach
    fun clearDAL() {
        // Clear the mock database
        factory = SecureStorageFactoryFake()
        storageLayer = factory.open("default".toByteArray())
        dal = DocumentDataAccessLayer(storageLayer)
    }

    @Test
    fun `An object is successfully written and read from the database`() {
        val obj: Map<String, Any> = mapOf(
                "string" to "val1",
                "integer" to 123
        )

        dal.writeObject("object", obj).join()
        val newObj = dal.readObject("object", listOf("string", "integer")).join()
        Assertions.assertNotNull(newObj)
        for (entry in obj) {
            Assertions.assertNotEquals(null, newObj?.get(entry.key))
            Assertions.assertEquals(entry.value, newObj?.get(entry.key))
        }
    }

    @Test
    fun `An object with the same id is overwritten upon a second write`() {
        val obj1: Map<String, Any> = mapOf(
                "string" to "val1",
                "integer" to 123
        )
        val obj2: Map<String, Any> = mapOf(
                "hello" to "world"
        )

        val id = "id"
        dal.writeObject(id, obj1).join()
        dal.writeObject(id, obj2).join()

        val res = dal.readObject(id, listOf("string", "integer", "hello")).join() ?: fail("Read object is null")
        Assertions.assertEquals(3, res.size)
        Assertions.assertEquals("world", res["hello"])
    }

    @Test
    fun `An object is successfully deleted from the database`() {
        val obj: Map<String, Any> = mapOf("key" to "val")
        val id = "id"
        dal.writeObject(id, obj).join()

        Assertions.assertEquals("val", dal.readObject(id, listOf("key")).join()?.get("key"))
        val deleted = dal.deleteObject(id, listOf("key")).join()
        Assertions.assertEquals("val", deleted?.get("key"))
        Assertions.assertEquals(null, dal.readObject(id, listOf("key")).join())
    }

    @Test
    fun `A deleted object is overwritten`() {
        val obj1= mapOf("key" to "val")
        val obj2=  mapOf("key" to "newVal")
        val id = "id"

        dal.writeObject(id, obj1).join()
        dal.deleteObject(id, listOf("key")).join()
        dal.writeObject(id, obj2).join()
        Assertions.assertEquals("newVal", dal.readObject(id, listOf("key")).join()?.get("key"))
    }

    @Test
    fun `An object is deleted twice and stays deleted`() {
        val obj= mapOf("key" to "val")
        val id = "id"

        dal.writeObject(id, obj)
                .thenCompose { dal.deleteObject(id, listOf("key")) }
                .thenCompose { dal.deleteObject(id, listOf("key")) }
                .join()
        Assertions.assertEquals(null, dal.readObject(id, listOf("key")).join())
    }

}