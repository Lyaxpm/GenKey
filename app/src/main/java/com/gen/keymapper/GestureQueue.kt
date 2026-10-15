package com.gen.keymapper

/** Ordered edges, shared frames: releasing one finger must not cancel other fingers. */
internal class GestureQueue {
    data class Contact(val token: Int, var x: Float, var y: Float, var ending: Boolean = false)
    private data class Event(val token: Int, val action: Int, val x: Float, val y: Float)
    private val events = java.util.ArrayDeque<Event>()
    private val contacts = linkedMapOf<Int, Contact>()
    fun offer(token: Int, action: Int, x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || events.size >= 512) return false
        // Only coalesce adjacent moves, never across a DOWN/UP edge.
        if (action == 2 && events.peekLast()?.let { it.action == 2 && it.token == token } == true) events.removeLast()
        events.addLast(Event(token, action, x, y))
        return true
    }
    fun next(): List<Contact> {
        while (events.isNotEmpty()) {
            val e = events.peekFirst()
            // A second press of the same token needs a new stroke after its old UP.
            if (e.action == 0 && contacts.containsKey(e.token)) {
                if (contacts[e.token]!!.ending) break
                events.removeFirst() // duplicate DOWN while held is not a new press
                continue
            }
            events.removeFirst()
            when (e.action) {
                0 -> contacts[e.token] = Contact(e.token, e.x, e.y)
                2 -> contacts[e.token]?.takeUnless { it.ending }?.let { it.x = e.x; it.y = e.y }
                1, 3 -> contacts[e.token]?.let { it.x = e.x; it.y = e.y; it.ending = true }
            }
        }
        return contacts.values.map { it.copy() }
    }
    fun completed(frame: List<Contact>) { frame.filter { it.ending }.forEach { contacts.remove(it.token) } }
    fun releaseAll() { events.clear(); contacts.values.forEach { it.ending = true } }
    fun clear() { events.clear(); contacts.clear() }
}
