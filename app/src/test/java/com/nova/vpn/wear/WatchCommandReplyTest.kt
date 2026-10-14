package com.nova.vpn.wear

import org.junit.Assert.*
import org.junit.Test

class WatchCommandReplyTest {
    private val reply = """{"requestId":"id","state":"foreground_required"}""".toByteArray()
    @Test fun matchingApplicationReplyIsAccepted() {
        assertEquals("foreground_required", WatchCommandReply.read(reply, "phone", "phone", "id"))
    }
    @Test fun staleAndForeignRepliesAreIgnored() {
        assertNull(WatchCommandReply.read(reply, "other", "phone", "id"))
        assertNull(WatchCommandReply.read(reply, "phone", "phone", "old"))
        assertNull(WatchCommandReply.read(reply, "phone", "phone", null))
    }
    @Test fun malformedOversizedAndUnknownStateAreIgnored() {
        assertNull(WatchCommandReply.read("bad-json".toByteArray(), "phone", "phone", "id"))
        assertNull(WatchCommandReply.read(ByteArray(2049), "phone", "phone", "id"))
        assertNull(WatchCommandReply.read("""{"requestId":"id","state":"secret"}""".toByteArray(), "phone", "phone", "id"))
    }
}
