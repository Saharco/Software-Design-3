import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import fakes.library.database.CourseAppDatabaseFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.lang.IllegalArgumentException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class CourseAppDatabaseTest {

    private val injector = Guice.createInjector(LibraryTestModule())
    private var storageFactory = injector.getInstance<SecureStorageFactory>()
    private val dbFactory = CourseAppDatabaseFactory(storageFactory)

    @BeforeEach
    internal fun resetDatabase() {
        storageFactory = injector.getInstance()
    }

    @Test
    internal fun `single field write in a document is properly read after document is written`() {
        val db = dbFactory.open("root").join()
        db.collection("programming languages")
                .document("kotlin")
                .set(Pair("isCool", "true"))
                .write()
                .join()

        val result = db.collection("programming languages")
                .document("kotlin")
                .read("isCool")
                .join()

        assertEquals(result, "true")
    }

    @Test
    internal fun `multiple fields write in a document are properly read after document is written`() {
        val data = hashMapOf("date" to "April 21, 2019",
                "isColored" to "true",
                "isPublic" to "false",
                "takenAt" to "technion")

        val documentRef = dbFactory.open("root").join()
                .collection("users")
                .document("sahar cohen")
                .collection("photos")
                .document("awkward photo")

        documentRef.set(data)
                .write()
                .join()

        assertEquals("April 21, 2019", documentRef.read("date").join())
        assertEquals("true", documentRef.read("isColored").join())
        assertEquals("false", documentRef.read("isPublic").join())
        assertEquals("technion", documentRef.read("takenAt").join())
    }

    @Test
    internal fun `reading fields or documents that do not exist should return null`() {
        dbFactory.open("root").join()
                .collection("users")
                .document("sahar")
                .set(Pair("eye color", "green"))
                .write()
                .join()

        var result = dbFactory.open("root").join()
                .collection("users")
                .document("sahar")
                .read("hair color")
                .join()

        assertNull(result)

        result = dbFactory.open("root").join()
                .collection("users")
                .document("yuval")
                .read("hair color")
                .join()

        assertNull(result)
    }

    @Test
    internal fun `writing to a document that already exists should throw IllegalArgumentException`() {
        val db = dbFactory.open("root").join()

        db.collection("users")
                .document("sahar")
                .set(Pair("eye color", "green"))
                .write()
                .join()

        assertThrows<IllegalArgumentException> {
            db.collection("users")
                    .document("sahar")
                    .set(Pair("surname", "cohen"))
                    .write()
                    .joinException()
        }
    }

    @Test
    internal fun `writing an empty document should throw IllegalStateException`() {
        assertThrows<IllegalStateException> {
            dbFactory.open("root").join()
                    .collection("users")
                    .document("sahar")
                    .write()
                    .joinException()
        }
    }

    @Test
    internal fun `reading document after deletion should return null`() {
        val userRef = dbFactory.open("root").join()
                .collection("users")
                .document("sahar")

        userRef.set(Pair("eye color", "green"))
                .write()
                .join()

        userRef.delete()
                .join()

        val result = userRef.read("eye color")
                .join()
        assertNull(result)

    }

    @Test
    internal fun `deleting some fields in a document should not delete the others`() {
        val data = hashMapOf("date" to "April 21, 2019",
                "isColored" to "true",
                "isPublic" to "false",
                "takenAt" to "technion")

        val documentRef = dbFactory.open("root").join()
                .collection("users")
                .document("sahar cohen")
                .collection("photos")
                .document("awkward photo")

        documentRef.set(data)
                .write()
                .join()

        documentRef.delete(listOf("isColored", "isPublic"))
                .join()

        assertNull(documentRef.read("isColored").join())
        assertEquals(documentRef.read("date").join(), "April 21, 2019")
        assertEquals(documentRef.read("takenAt").join(), "technion")
    }

    @Test
    internal fun `can check if a document exists`() {
        var documentRef = dbFactory.open("root").join()
                .collection("users")
                .document("sahar")

        documentRef.set(Pair("eye color", "green"))
                .write()
                .join()

        assertTrue(documentRef.exists().join())

        documentRef = dbFactory.open("root").join()
                .collection("students")
                .document("sahar")

        assertFalse(documentRef.exists().join())
    }

    @Test
    internal fun `can update existing and non existing fields in a document which may or may not exist`() {
        var documentRef = dbFactory.open("root").join()
                .collection("users")
                .document("sahar")

        documentRef.set(Pair("favorite food", "pizza"))
                .write()
                .join()

        documentRef.set(Pair("favorite food", "ice cream"))
                .set(Pair("favorite animal", "dog"))
                .update()
                .join()

        assertEquals("ice cream", documentRef.read("favorite food").join())
        assertEquals("dog", documentRef.read("favorite animal").join())

        documentRef = dbFactory.open("root").join()
                .collection("users")
                .document("yuval")

        documentRef.set(Pair("favorite food", "pizza"))
                .update()
                .join()

        assertEquals("pizza", documentRef.read("favorite food").join())
    }

    @Test
    internal fun `writing document in one database does not affect another database`() {
        val db1 = dbFactory.open("users").join()
        val db2 = dbFactory.open("items").join()

        db1.collection("root")
                .document("sahar")
                .set(Pair("age", "21"))
                .write()
                .join()

        val result = db2.collection("root")
                .document("sahar")
                .read("age")
                .join()

        assertNotEquals("21", result)
    }

    @Test
    internal fun `can write and read lists as a document's field`() {
        val documentRef = dbFactory.open("users").join()
                .collection("root")
                .document("sahar")

        val list = mutableListOf("ice cream", "pizza", "popcorn")

        documentRef.set("favorite foods", list)
                .write()
                .join()

        val returnedList = documentRef.readList("favorite foods")
                .join()

        assertTrue(returnedList!!.containsAll(list))
        assertTrue(list.containsAll(returnedList))
    }

    @Test
    internal fun `any character can be used as a document's name`() {
        val chars = generateCharactersList()
        val collectionRef = dbFactory.open("root").join()
                .collection("root")
        val data = Pair("key", "value")
        for (str in chars) {
            collectionRef.document(str)
                    .set(data)
                    .write()
                    .join()
        }
    }

    private fun generateCharactersList(): ArrayList<String> {
        val list = ArrayList<String>()
        for (i in 0 until 128) {
            list.add((i.toChar().toString()))
        }
        return list
    }

    /**
     * Perform [CompletableFuture.join], and if an exception is thrown, unwrap the [CompletionException] and throw the
     * causing exception.
     */
    private fun <T> CompletableFuture<T>.joinException(): T {
        try {
            return this.join()
        } catch (e: CompletionException) {
            throw e.cause!!
        }
    }
}


