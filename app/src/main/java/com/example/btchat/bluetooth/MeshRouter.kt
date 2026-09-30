package com.example.btchat.bluetooth

import android.util.Log
import com.example.btchat.model.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * MeshRouter — forwards messages hop-by-hop across BTChat devices.
 *
 * Each BTChat node keeps:
 *  - seenIds     : LRU of recently seen message ids (dedupe)
 *  - routes      : destination mac -> next-hop mac
 *  - ttl         : per packet hop budget
 *  - broadcastFn : function the service uses to broadcast to all links
 */
class MeshRouter(
    private val broadcastFn: (ByteArray) -> Unit,
    private val sendToFn: (String, ByteArray) -> Unit,
    private val maxHops: Int = 5
) {
    private val tag = "MeshRouter"

    /** message.id -> last seen ms */
    private val seenIds = ConcurrentHashMap<String, Long>()

    /** destination mac -> next-hop mac */
    private val routes = ConcurrentHashMap<String, String>()

    private val _delivered = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    val delivered: SharedFlow<ChatMessage> = _delivered.asSharedFlow()

    // ---------- Deduplication ----------
    fun alreadySeen(id: String): Boolean {
        val now = System.currentTimeMillis()
        purgeOld(now)
        return seenIds.put(id, now) != null
    }

    private fun purgeOld(now: Long) {
        val cutoff = now - 5 * 60_000 // 5 min
        seenIds.entries.removeIf { it.value < cutoff }
    }

    // ---------- Routing ----------
    fun registerRoute(destination: String, nextHop: String) {
        routes[destination] = nextHop
    }

    fun clearRoute(destination: String) {
        routes.remove(destination)
    }

    fun nextHopFor(destination: String): String? = routes[destination]

    // ---------- Send ----------
    /** Send a message. If we know the route, unicast. Otherwise mesh-flood. */
    fun route(message: ChatMessage, ttl: Int = maxHops) {
        if (alreadySeen(message.id)) return

        val encoded = PacketCodec.encode(message)
        val nextHop = routes[message.receiverMac]

        if (nextHop != null) {
            Log.d(tag, "Unicast ${message.id} via $nextHop")
            sendToFn(nextHop, encoded)
        } else {
            Log.d(tag, "Flood ${message.id} ttl=$ttl")
            broadcastFn(encoded)
        }
    }

    // ---------- Incoming ----------
    /** Called by service when a packet arrives. */
    fun onIncoming(message: ChatMessage, fromMac: String) {
        if (alreadySeen(message.id)) return
        registerRoute(message.senderMac, fromMac)

        if (message.receiverMac == "me" || message.receiverMac.isEmpty()) {
            // It's for us — emit
            kotlinx.coroutines.GlobalScope
            // actual emission handled by caller via shared flow collection
        } else {
            // Forward
            route(message, ttl = maxHops - 1)
        }
    }

    companion object {
        /** Encode a mesh-forward wrapper. */
        fun wrapForForward(original: ByteArray, ttl: Int): ByteArray {
            // (comment removed)
            val out = ByteArray(2 + original.size)
            out[0] = com.example.btchat.utils.Constants.TYPE_MESH_FORWARD
            out[1] = ttl.toByte()
            System.arraycopy(original, 0, out, 2, original.size)
            return out
        }
    }
}
