
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import il.ac.technion.cs.softwaredesign.lib.utils.BalancedStorageTree
import il.ac.technion.cs.softwaredesign.lib.mock.SecureStorageFactoryFake
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.CompletableFuture

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StorageTreeTests {

    private lateinit var factory: SecureStorageFactory
    private lateinit var storageLayer: CompletableFuture<SecureStorage>

    @BeforeEach
    fun clearDAL() {
        // Clear the mock database
        factory = SecureStorageFactoryFake()
        storageLayer = factory.open("default".toByteArray())
    }

    private fun populateWithRandomStrings(list: ArrayList<String>, amount: Int = 1e6.toInt(),
                                          maxSize: Int = 30, charPool: List<Char>? = null) {
        val pool = charPool ?: ('a'..'z') + ('A'..'Z') + ('0'..'9') + '/'
        for (i in 0 until amount) {
            val randomString = (1..maxSize)
                    .map { kotlin.random.Random.nextInt(0, pool.size) }
                    .map(pool::get)
                    .joinToString("")
            list.add(randomString)
        }
    }

    private fun stressTest(cap: Int) {
        val tree = BalancedStorageTree<String, Long>(storage = storageLayer)

        val strings = ArrayList<String>()
        populateWithRandomStrings(strings, amount = cap)
        val keys = strings.distinct()
        val systemSize = keys.size
        val values = arrayOfNulls<Long>(systemSize)
        try {
            for (i in 0 until systemSize) {
                // Dont care about exact values here: username & password are the same for each user
                val l = kotlin.random.Random.nextLong(0, 5000)
                tree.insert(keys[i], l).join()
                values[i] = l
                //println("Database size: ${database.size} actual members: $i")
            }
        } catch (e: OutOfMemoryError) {
            print("Out Of Memory")
        }

        val sorted = keys.sorted()

        var i = 0
        tree.inorder {
            assertThat(sorted[i], equalTo(it.first))
            i++
        }
    }

    private fun <T> containsElementsInOrder(vararg elements: T): Matcher<Collection<T>> {
        val perElementMatcher = object : Matcher.Primitive<Collection<T>>() {
            override fun invoke(actual: Collection<T>): MatchResult {
                elements.zip(actual).forEach {
                    if (it.first != it.second)
                        return MatchResult.Mismatch("${it.first} does not equal ${it.second}")
                }
                return MatchResult.Match
            }

            override val description = "is ${describe(elements)}"
            override val negatedDescription = "is not ${describe(elements)}"
        }
        return has(Collection<T>::size, equalTo(elements.size)) and perElementMatcher
    }

    @Test
    fun simpleTest() {
        val tree = BalancedStorageTree<Int, Long>(storage = storageLayer)

        val systemSize = 10
        val values = arrayOfNulls<Long>(systemSize)
        try {
            for (i in 0 until systemSize) {
                // Dont care about exact values here: username & password are the same for each user
                val l = kotlin.random.Random.nextLong(0, 5000)
                tree.insert(i, l).join()
                values[i] = l
            }
        } catch (e: OutOfMemoryError) {
            print("Out Of Memory")
        }

        var i = 0
        tree.inorder {
            assertThat(values[i], equalTo(it.second))
            i++
        }.join()

        assertThat(tree.asSequence().join().take(5).map { it.first }.toList(), containsElementsInOrder(0, 1, 2, 3, 4))

        try {
            for (i in 0 until systemSize) {
                // Dont care about exact values here: username & password are the same for each user
                tree.delete(i).join()
            }
        } catch (e: OutOfMemoryError) {
            print("Out Of Memory")
        }

        i = 0
        tree.inorder {
            i++
        }.join()


        assertThat(i, equalTo(0))
    }

    @Test
    fun `Elements are properly inserted to the tree and storage`() {
        val tree = BalancedStorageTree<Int, String>(storage = storageLayer)

        val ret = tree.insert(1, ", ")
                .thenCompose { tree.insert(2, "world") }
                .thenCompose { tree.insert(0, "hello") }
                .join()

        assertThat(ret, equalTo(true))
        assertThat(tree.asSequence().join().toList().joinToString(separator = "") { it.second }, equalTo("hello, world"))

        val other = BalancedStorageTree<Int, String>(storage = storageLayer)
        assertThat(other.asSequence().join().toList().joinToString(separator = "") { it.second }, equalTo("hello, world"))
    }

    // TODO Add tests for search, insert, delete and edge cases

    @Test
    fun `tree stress test #1`() {
        stressTest(1000)
    }

//    @Disabled
    @Test
    fun `tree stress test #2`() {
        stressTest(10000)
    }

}