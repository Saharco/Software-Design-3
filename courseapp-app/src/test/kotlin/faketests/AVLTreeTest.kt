import fakes.library.database.CachedStorage
import fakes.library.mocks.SecureStorageMock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AVLTreeTest {

    lateinit var tree: AVLTree

    @BeforeAll
    internal fun `initialize tree`() {
        val storage = CachedStorage(SecureStorageMock())
        tree = AVLTree(storage)
    }

    @Test
    internal fun `inserting multiple elements and reading them`() {
        for (i in 0..100) {
            tree.insert(("100000$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in 0..100) {
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search(("100000$i/2019").toByteArray(charset))!!))
        }
    }

    @Test
    internal fun `inserting one element and deleting it`() {
        tree.insert("1000000/2019".toByteArray(charset), "".toByteArray(charset))
        tree.delete("1000000/2019".toByteArray(charset))
    }

    @Test
    internal fun `inserting multiple elements and deleting them`() {
        val list: MutableList<Int> = (0..200).toMutableList()
        list.shuffle()
        for (i in list) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in list.shuffled()) {
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search("$i/2019".toByteArray(charset))!!))
            tree.delete("$i/2019".toByteArray(charset))
            assertEquals(null, tree.search("$i/2019".toByteArray(charset)))
        }
    }

    @Test
    internal fun `inserting and deleting one by one`() {
        val list: MutableList<Int> = (0..100).toMutableList()
        list.shuffle()
        for (i in list) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search("$i/2019".toByteArray(charset))!!))
            tree.delete("$i/2019".toByteArray(charset))
            assertEquals(null, tree.search("$i/2019".toByteArray(charset)))
        }
    }

    @Test
    internal fun `top k test`() {
        for (i in 0..9) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in 1..10) {
            for (j in 9 downTo 10 - i) {
                assertTrue(tree.topKTree(i).contains("$j"))
            }
            for (j in 10 - i - 1 downTo 1) {
                assertFalse(tree.topKTree(i).contains("$j"))
            }
        }

    }
}