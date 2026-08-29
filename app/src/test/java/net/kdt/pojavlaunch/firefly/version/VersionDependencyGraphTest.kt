package net.kdt.pojavlaunch.firefly.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionDependencyGraphTest {
    @Test
    fun findsEveryDependentBeforeAParentCanBeDeleted() {
        val parents = mapOf(
            "1.21.1" to "",
            "fabric" to "1.21.1",
            "fabric-api" to "fabric",
            "standalone" to ""
        )

        assertEquals(listOf("fabric", "fabric-api"), VersionDependencyGraph.descendants("1.21.1", parents))
    }

    @Test
    fun identifiesMissingParentsAndInheritanceCyclesAsNeedingRepair() {
        assertTrue(VersionDependencyGraph.requiresRepair(
            "fabric", mapOf("fabric" to "1.21.1"), emptySet()
        ))
        assertTrue(VersionDependencyGraph.requiresRepair(
            "broken", mapOf("broken" to "broken"), setOf("broken")
        ))
        assertTrue(VersionDependencyGraph.requiresRepair(
            "a", mapOf("a" to "b", "b" to "a"), emptySet()
        ))
    }

    @Test
    fun acceptsAnInheritedInstanceOnlyWhenItsFullParentChainExists() {
        val parents = mapOf("1.21.1" to "", "fabric" to "1.21.1")

        assertFalse(VersionDependencyGraph.requiresRepair("fabric", parents, setOf("1.21.1")))
    }
}
