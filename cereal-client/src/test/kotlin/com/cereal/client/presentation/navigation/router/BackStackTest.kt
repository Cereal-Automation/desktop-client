package com.cereal.client.presentation.navigation.router

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackStackTest {
    @Test
    fun `starts with the initial element`() {
        val backStack = BackStack("a") {}

        assertEquals(listOf("a"), backStack.elements)
        assertEquals("a", backStack.last())
        assertEquals(0, backStack.lastIndex)
        assertEquals(1, backStack.size)
    }

    @Test
    fun `push appends without removing anything`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }

        backStack.push("b")

        assertEquals(listOf("a", "b"), backStack.elements)
        assertEquals("b", backStack.last())
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `pop removes the last element and notifies its index`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }
        backStack.push("b")

        val popped = backStack.pop()

        assertTrue(popped)
        assertEquals(listOf("a"), backStack.elements)
        assertEquals(listOf(1), removed)
    }

    @Test
    fun `pop refuses to remove the last remaining element`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }

        val popped = backStack.pop()

        assertFalse(popped)
        assertEquals(listOf("a"), backStack.elements)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun `pushAndDropNested notifies the current top then pushes`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }
        backStack.push("b")

        backStack.pushAndDropNested("c")

        assertEquals(listOf("a", "b", "c"), backStack.elements)
        assertEquals(listOf(1), removed)
    }

    @Test
    fun `replace swaps the top element`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }
        backStack.push("b")

        backStack.replace("c")

        assertEquals(listOf("a", "c"), backStack.elements)
        assertEquals(listOf(1), removed)
    }

    @Test
    fun `newRoot clears the stack and notifies every index in reverse`() {
        val removed = mutableListOf<Int>()
        val backStack = BackStack("a") { removed.add(it) }
        backStack.push("b")
        backStack.push("c")
        removed.clear()

        backStack.newRoot("z")

        assertEquals(listOf("z"), backStack.elements)
        assertEquals(listOf(2, 1, 0), removed)
    }
}
