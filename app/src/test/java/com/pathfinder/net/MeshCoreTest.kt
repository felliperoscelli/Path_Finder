package com.pathfinder.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simula vários aparelhos ligados por conexões diretas. O "endpointId" de
 * cada lado é o deviceId do outro aparelho.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeshCoreTest {

    private class Net(val scope: CoroutineScope, val clock: () -> Long) {
        val nodes = mutableMapOf<String, MeshCore>()
        val inbox = mutableMapOf<String, MutableList<Envelope>>()
        val links = mutableSetOf<Pair<String, String>>()
        /** Ordem dos envios: (de, para, tipo). */
        val sends = mutableListOf<Triple<String, String, String>>()

        fun node(id: String): MeshCore {
            val core = MeshCore(id, { endpoint, bytes -> deliver(id, endpoint, bytes) }, InMemoryMessageStore(), scope, clock)
            core.localName = id
            nodes[id] = core
            inbox[id] = mutableListOf()
            scope.launch { core.delivered.collect { inbox.getValue(id) += it } }
            return core
        }

        private suspend fun deliver(from: String, to: String, bytes: ByteArray): Boolean {
            if ((from to to) !in links) return false
            sends += Triple(from, to, Wire.decode(bytes).body::class.simpleName!!)
            nodes.getValue(to).onReceived(from, from, bytes)
            return true
        }

        suspend fun connect(a: String, b: String) {
            links += a to b
            links += b to a
            nodes.getValue(a).onPeerConnected(b, b)
            nodes.getValue(b).onPeerConnected(a, a)
        }

        fun disconnect(a: String, b: String) {
            links -= a to b
            links -= b to a
            nodes.getValue(a).onPeerDisconnected(b)
            nodes.getValue(b).onPeerDisconnected(a)
        }
    }

    private var now = 1_000_000L

    /**
     * Roda o teste com a rede num escopo próprio no relógio do teste
     * (advanceUntilIdle não executa o backgroundScope) e o encerra no fim.
     */
    private fun meshTest(block: suspend TestScope.(Net) -> Unit) = runTest {
        val job = Job()
        val net = Net(CoroutineScope(coroutineContext.minusKey(Job) + job)) { now }
        try {
            block(net)
        } finally {
            job.cancel()
        }
    }

    private fun alert(since: Long = 1L) = DeviationAlert(-17.7, -48.6, now, since, 45.0, "Pista", "Águias", "João", false)
    private fun position(t: Long) = PositionUpdate(-17.7, -48.6, 5f, t, "u", "Pista", "Águias", "João", 1, 5)

    @Test fun relaysAlongALine() = meshTest { n ->
        val a = n.node("A"); n.node("B"); n.node("C")
        n.connect("A", "B"); n.connect("B", "C")
        a.originate(alert(), Priority.ALERT)
        advanceUntilIdle()
        assertEquals(1, n.inbox.getValue("B").size)
        assertEquals(1, n.inbox.getValue("C").size)
        assertEquals(1, n.inbox.getValue("C").single().hops) // repassada uma vez (por B)
        assertTrue(n.inbox.getValue("A").isEmpty())
    }

    @Test fun noLoopsOrDuplicatesInAFullyConnectedGroup() = meshTest { n ->
        val ids = listOf("A", "B", "C", "D")
        ids.forEach { n.node(it) }
        for (i in ids.indices) for (j in i + 1 until ids.size) n.connect(ids[i], ids[j])
        n.nodes.getValue("A").originate(alert(), Priority.ALERT)
        advanceUntilIdle()
        ids.drop(1).forEach { assertEquals("inbox $it", 1, n.inbox.getValue(it).size) }
        // Cada aparelho envia no máximo uma vez para cada vizinho.
        assertTrue("${n.sends.size} envios", n.sends.size <= ids.size * (ids.size - 1))
    }

    @Test fun storeAndForwardAcrossConnectionsThatComeLater() = meshTest { n ->
        val a = n.node("A"); n.node("B"); n.node("C")
        a.originate(alert(), Priority.ALERT) // ninguém conectado ainda
        advanceUntilIdle()
        n.connect("A", "B")
        advanceUntilIdle()
        assertEquals(1, n.inbox.getValue("B").size)
        n.disconnect("A", "B")
        n.connect("B", "C") // C nunca esteve perto de A
        advanceUntilIdle()
        assertEquals(1, n.inbox.getValue("C").size)
        // Reconectar não reenvia o que o outro já tem.
        val before = n.sends.size
        n.disconnect("B", "C"); n.connect("B", "C")
        advanceUntilIdle()
        assertEquals(before, n.sends.size)
    }

    @Test fun onlyTheLatestPositionIsKept() = meshTest { n ->
        val a = n.node("A"); n.node("B")
        a.originate(position(1), Priority.POSITION); now += 30_000
        a.originate(position(2), Priority.POSITION); now += 30_000
        a.originate(position(3), Priority.POSITION)
        advanceUntilIdle()
        n.connect("A", "B")
        advanceUntilIdle()
        val got = n.inbox.getValue("B").map { (it.body as PositionUpdate).time }
        assertEquals(listOf(3L), got)
    }

    @Test fun alertsGoBeforePositionsAndTrails() = meshTest { n ->
        val a = n.node("A"); n.node("B")
        a.originate(position(1), Priority.POSITION)
        now += 1
        a.originate(PointReached(now, 1, "P", 5, "Pista", "Águias", "João"), Priority.NORMAL)
        now += 1
        a.originate(alert(), Priority.ALERT)
        advanceUntilIdle()
        n.connect("A", "B")
        advanceUntilIdle()
        assertEquals(
            listOf("DeviationAlert", "PointReached", "PositionUpdate"),
            n.sends.filter { it.first == "A" }.map { it.third },
        )
    }

    @Test fun expiredMessagesAreNotDelivered() = meshTest { n ->
        val a = n.node("A"); n.node("B")
        a.originate(position(1), Priority.POSITION)
        now += MeshPolicy.ttlMillis(position(1)) + 1
        n.connect("A", "B")
        advanceUntilIdle()
        assertTrue(n.inbox.getValue("B").isEmpty())
    }

    @Test fun hopLimitStopsRelaying() = meshTest { n ->
        val chain = (0..MeshPolicy.MAX_HOPS + 2).map { "N$it" }
        chain.forEach { n.node(it) }
        chain.zipWithNext().forEach { (x, y) -> n.connect(x, y) }
        n.nodes.getValue("N0").originate(alert(), Priority.ALERT)
        advanceUntilIdle()
        // N1 recebe com 0 repasses; o último a receber tem MAX_HOPS repasses.
        assertEquals(1, n.inbox.getValue("N${MeshPolicy.MAX_HOPS + 1}").size)
        assertTrue(n.inbox.getValue("N${MeshPolicy.MAX_HOPS + 2}").isEmpty())
    }

    @Test fun directMessagesAreRelayedButDeliveredOnlyToTheRecipient() = meshTest { n ->
        val a = n.node("A"); n.node("B"); n.node("C")
        n.connect("A", "B"); n.connect("B", "C")
        a.originate(alert(), Priority.ALERT, to = "C")
        advanceUntilIdle()
        assertTrue(n.inbox.getValue("B").isEmpty())
        assertEquals(1, n.inbox.getValue("C").size)
    }

    @Test fun storeIsPruned() = runTest {
        val store = InMemoryMessageStore()
        val core = MeshCore("A", { _, _ -> true }, store, backgroundScope, { now }, maxStored = 3)
        repeat(5) { core.originate(PointReached(now + it, it, "P$it", 9, "Pista", "G", "M"), Priority.NORMAL) }
        core.originate(alert(), Priority.ALERT)
        core.prune()
        assertEquals(3, store.count())
        // O alerta (mais importante) é mantido.
        assertTrue(store.pendingFor("Z", now).any { it.priority == Priority.ALERT })
    }
}
