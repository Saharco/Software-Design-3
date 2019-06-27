import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import il.ac.technion.cs.softwaredesign.lib.db.*
import il.ac.technion.cs.softwaredesign.lib.db.dal.DocumentDataAccessLayer
import il.ac.technion.cs.softwaredesign.lib.mock.SecureStorageFactoryFake
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import org.junit.jupiter.api.*
import java.util.*
import java.util.concurrent.CompletableFuture


val isNull: Matcher<Any?> = Matcher(Objects::isNull)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryTests {

    private lateinit var factory: SecureStorageFactory
    private lateinit var storageLayer: CompletableFuture<SecureStorage>
    private lateinit var dal: DocumentDataAccessLayer

    @BeforeEach
    fun clearDAL() {
        // Clear the mock database
        factory = SecureStorageFactoryFake()
        storageLayer = factory.open("default".toByteArray())
        dal = DocumentDataAccessLayer(storageLayer)
    }

    @Nested
    inner class WriteQueryTests {

        private lateinit var writeQuery: WriteQuery
        @BeforeEach
        fun init(){
            writeQuery = WriteQuery("", dal, "id")
        }

        @Test
        fun `create for a document with no fields returns null`(){
            val doc = writeQuery.execute().join()
            assertThat(doc, isNull)
        }

        @Test
        fun `set adds non existing fields`(){
            val doc = writeQuery.set("field", "val").execute().join()
            assertThat(doc?.getAsString("field"), equalTo("val"))
        }

        @Test
        fun `set changes values of existing fields`(){
            val doc = writeQuery
                    .set("field", "val")
                    .set("field", "newVal")
                    .execute().join()

           assertThat(doc?.getAsString("field"), equalTo("newVal"))
        }

        @Test
        fun `create creates a document in the database with all the provided fields`(){
            val doc = writeQuery.set(
                    "field1" to "val1",
                    "field2" to 123
            ).execute().join()

            assertThat(doc?.getAsString("field1"), equalTo("val1"))
            assertThat(doc?.getInteger("field2"), equalTo(123))
        }

        @Test
        fun `set applies to the written document even if the call was after create`(){
            val doc = writeQuery
                    .set("field1" to "val1")
                    .set("field2", 123)
                    .execute().join()

            Assertions.assertEquals("val1", doc?.getAsString("field1"))
            Assertions.assertEquals(123, doc?.getInteger("field2"))
        }
        
        @Test
        fun `create without a call to execute does nothing`(){
            
            writeQuery.set("field", "val")
            val doc = ReadQuery("", dal, "id", listOf("field")).execute().join()
            assertThat(doc,isNull)
        } 

    }

    @Nested
    inner class ReadQueryTests {

        private lateinit var readQuery: ReadQuery
        @BeforeEach
        fun init(){
            readQuery = ReadQuery("", dal, "id", listOf("field"))
        }

        @Test
        fun `find for a non exist document returns null`() {
            val doc = readQuery.execute().join()
            assertThat(doc, isNull)
        }

        // TODO: Remove
//        @Test
//        fun `find overrides the document in the query`() {
//            WriteQuery("", dal, "id").set("field", "val").execute().join()
//            val doc1 = readQuery.find("id").execute().join()
//            val doc2 = readQuery.find("id1" "id").execute().join()
//            val doc3 = readQuery.find("id1").execute().join()
//
//            Assertions.assertEquals("val", doc1?.getAsString("field"))
//            Assertions.assertEquals("val", doc2?.getAsString("field"))
//            Assertions.assertNull(doc3)
//        }

//        @Test
//        fun `repeated calls to find returns the last document found`(){
//            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
//            WriteQuery("", dal, "id2").set("field", "val2").execute().join()
//            WriteQuery("", dal, "id3").set("field", "val3").execute().join()
//
//            val doc = readQuery.find("id3" "id1" "id2").execute().join()
//
//            Assertions.assertEquals("val2", doc?.getAsString("field"))
//
//        }

//        @Test
//        fun `repeated calls to find when the last call to find is for a non exist documents returns null`(){
//            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
//
//            val doc = readQuery.find("id3" "id1" "id2").execute().join()
//
//            assertThat(doc, isNull)
//        }

    }

    @Nested
    inner class UpdateQueryTests {

        private lateinit var updateQuery: UpdateQuery

        @BeforeEach
        fun init(){
            updateQuery= UpdateQuery("", dal, "id")
        }

        @Test
        fun `a call to update without a call to find in a query returns null`(){
            assertThat(updateQuery.set().execute().join(), isNull)
        }

        @Test
        fun `attempt to update a non exist file returns null`(){
            val doc2 = updateQuery.execute().join()
            assertThat(doc2, isNull)
        }

        @Test
        fun `update changes the document fields`(){
            WriteQuery("", dal, "id").set("field", "val1").execute().join()
            val doc = updateQuery.set("field","val2").execute().join()

            assertThat(doc?.getAsString("field"), equalTo("val2"))
        }


        @Test
        fun `update without a call to execute does nothing`(){
            WriteQuery("", dal, "id").set("field", "val1").execute().join()
            updateQuery.set("field","val2")

            val doc = ReadQuery("", dal, "id", listOf("field")).execute().join()

            assertThat(doc?.getAsString("field"), equalTo("val1"))

        }

    }

    @Nested
    inner class DeleteQueryTests {

        private lateinit var deleteQuery: DeleteQuery

        @BeforeEach
        fun init(){
            deleteQuery = DeleteQuery("", dal, "id", listOf("field"))
        }

        @Test
        fun `delete on a non exist document returns null`() {
            val doc = deleteQuery.execute().join()
            assertThat(doc,isNull)
        }

        @Test
        fun `delete on a deleted file keeps the file deleted`(){
            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
            DeleteQuery("", dal, "id1", listOf("field")).execute().join();
            var doc = ReadQuery("", dal, "id1", listOf("field")).execute().join()

            assertThat(doc,isNull)
            DeleteQuery("", dal, "id1", listOf("field")).execute().join();
            doc = ReadQuery("", dal, "id1", listOf("field")).execute().join()

            assertThat(doc,isNull)

        }

        @Test
        fun `delete only marks one document as deleted`(){
            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
            WriteQuery("", dal, "id2").set("field", "val2").execute().join()
            DeleteQuery("", dal, "id1", listOf("field")).execute().join()
            val doc1 = ReadQuery("", dal, "id1", listOf("field")).execute().join()
            val doc2 = ReadQuery("", dal, "id2", listOf("field")).execute().join()

            assertThat(doc1, isNull)
            assertThat(doc2?.getAsString("field"), equalTo("val2"))
        }

        // TODO Remove
//        @Test
//        fun `repeated calls to delete deletes only the last document`(){
//            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
//            WriteQuery("", dal, "id2").set("field", "val2").execute().join()
//
//            deleteQuery.delete("id1").delete("id2").execute().join()
//
//            val doc1 = ReadQuery("", dal, "id1").execute().join()
//            val doc2 = ReadQuery("", dal, "id2").execute().join()
//
//            assertThat(doc1?.getAsString("field"), equalTo("val1"))
//            assertThat(doc2, isNull)
//
//        }

//        @Test
//        fun `repeated calls to delete when the last file non exist returns null and deletes nothing`(){
//            WriteQuery("", dal, "id1").set("field", "val1").execute().join()
//            WriteQuery("", dal, "id2").set("field", "val2").execute().join()
//
//            val doc3 = deleteQuery.delete("id1").delete("id3").execute().join()
//
//            val doc1 = ReadQuery("", dal,  "id1").execute().join()
//            val doc2 = ReadQuery("", dal,  "id2").execute().join()
//
//            assertThat(doc1?.getAsString("field"), equalTo("val1"))
//            assertThat(doc2?.getAsString("field"), equalTo("val2"))
//            assertThat(doc3, isNull)
//        }

        @Test
        fun `delete query without a call to execute does nothing`(){
            WriteQuery("", dal, "id1").set("field", "val1").execute().join()

            DeleteQuery("", dal, "id1", listOf("field"))

            val doc = ReadQuery("", dal, "id1", listOf("field")).execute().join()

            assertThat(doc?.getAsString("field"), equalTo("val1"))

        }
    }
}