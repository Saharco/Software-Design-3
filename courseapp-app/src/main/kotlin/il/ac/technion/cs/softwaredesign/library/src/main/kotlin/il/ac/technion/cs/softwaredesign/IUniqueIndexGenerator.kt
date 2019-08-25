package il.ac.technion.cs.softwaredesign

interface IUniqueIndexGenerator {

    fun getUniqueIndex(): Int

    fun takeUniqueIndex(index: Int)
}