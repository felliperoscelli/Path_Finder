package com.pathfinder.net

import com.pathfinder.data.settings.Pin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventIsolationTest {
    @Test fun eventCodeNormalization() {
        assertEquals("AGUIA27", EventCode.normalize(" Águia 27 "))
        assertEquals("ACAMP-SUL", EventCode.normalize("acamp-sul!"))
        assertEquals("com.pathfinder.mesh", EventCode.serviceId(""))
        assertEquals("com.pathfinder.mesh.AGUIA27", EventCode.serviceId("águia 27"))
    }

    @Test fun messagesFromAnotherEventAreNeitherDeliveredNorRelayed() = runTest {
        val job = Job()
        val scope = CoroutineScope(coroutineContext.minusKey(Job) + job)
        val cores = mutableMapOf<String, MeshCore>()
        val inbox = mutableMapOf<String, MutableList<Envelope>>()
        fun node(id: String, event: String) = MeshCore(id, { to, bytes ->
            cores.getValue(to).onReceived(id, id, bytes); true
        }, InMemoryMessageStore(), scope).also { core ->
            core.eventCode = event
            cores[id] = core
            inbox[id] = mutableListOf()
            scope.launch { core.delivered.collect { inbox.getValue(id) += it } }
        }
        val a = node("A", "AGUIA")
        node("B", "LOBO")   // outro evento, no meio do caminho
        node("C", "AGUIA")
        cores.getValue("A").onPeerConnected("B", "B"); cores.getValue("B").onPeerConnected("A", "A")
        cores.getValue("B").onPeerConnected("C", "C"); cores.getValue("C").onPeerConnected("B", "B")
        a.originate(TextMessage("oi", "COORDINATOR"), Priority.NORMAL)
        advanceUntilIdle()
        assertTrue(inbox.getValue("B").isEmpty())
        assertTrue(inbox.getValue("C").isEmpty()) // B não repassa mensagens de outro evento
        job.cancel()
    }

    @Test fun pin() {
        val h = Pin.hash("1234")
        assertTrue(Pin.matches("1234", h))
        assertFalse(Pin.matches("4321", h))
        assertTrue(Pin.matches("qualquer", "")) // sem PIN definido
        assertTrue(Pin.isValid("1234"))
        assertFalse(Pin.isValid("12"))
        assertFalse(Pin.isValid("12a4"))
    }
}
