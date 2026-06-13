package io.github.samgum.aegisub.domain.undo

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotUndoStackTest {
    @Test fun commit_advances_current() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "add a")
        assertEquals(1, stack.current.events.size)
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }
    @Test fun undo_redo_restores_versions() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "a")
        stack.commit(stack.current.withEvent(AssEvent(text = "b")), "b")
        assertEquals(2, stack.current.events.size)
        stack.undo()
        assertEquals(1, stack.current.events.size)
        assertEquals("a", stack.current.events[0].text)
        assertTrue(stack.canRedo)
        stack.undo()
        assertEquals(0, stack.current.events.size)
        assertFalse(stack.canUndo)
        stack.redo()
        assertEquals(1, stack.current.events.size)
    }
    @Test fun new_commit_after_undo_clears_redo_branch() {
        val stack = SnapshotUndoStack(AssScript.default())
        stack.commit(stack.current.withEvent(AssEvent(text = "a")), "a")
        stack.undo()
        stack.commit(stack.current.withEvent(AssEvent(text = "c")), "c")
        assertFalse(stack.canRedo)
        assertEquals("c", stack.current.events[0].text)
    }
    @Test fun history_limit_evicts_oldest() {
        val stack = SnapshotUndoStack(AssScript.default(), limit = 3)
        repeat(5) { stack.commit(stack.current.withEvent(AssEvent(text = "$it")), "$it") }
        var undos = 0
        while (stack.canUndo) { stack.undo(); undos++ }
        assertEquals(3, undos)
    }
}
