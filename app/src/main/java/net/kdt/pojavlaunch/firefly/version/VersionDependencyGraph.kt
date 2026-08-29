/*
 * Pojav Glow-Worm
 * Copyright (C) 2026 Pojav Glow-Worm contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.firefly.version

/** Pure dependency rules shared by local scanning and destructive version operations. */
object VersionDependencyGraph {
    fun descendants(versionId: String, parentByVersion: Map<String, String?>): List<String> {
        val result = LinkedHashSet<String>()
        fun collect(parentId: String) {
            parentByVersion.forEach { (candidate, parent) ->
                if (parent == parentId && result.add(candidate)) collect(candidate)
            }
        }
        collect(versionId)
        return result.toList()
    }

    fun requiresRepair(
        versionId: String,
        parentByVersion: Map<String, String?>,
        clientJarVersions: Set<String>
    ): Boolean {
        fun visit(currentId: String, visiting: MutableSet<String>): Boolean {
            if (!visiting.add(currentId)) return true
            val parentId = parentByVersion[currentId] ?: return currentId !in clientJarVersions
            val result = when {
                parentId.isBlank() -> currentId !in clientJarVersions
                parentId == currentId -> true
                parentId !in parentByVersion -> true
                else -> visit(parentId, visiting)
            }
            visiting.remove(currentId)
            return result
        }
        return visit(versionId, LinkedHashSet())
    }
}
