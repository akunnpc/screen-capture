package app.kirimlayar.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun urlRtmpValid() {
        assertTrue(Presets.isValidRtmpUrl("rtmp://127.0.0.1:1935/live/test"))
        assertTrue(Presets.isValidRtmpUrl("rtmps://a.example.com/app/key123"))
        assertTrue(Presets.isValidRtmpUrl("  RTMP://host/app/key  "))
    }

    @Test
    fun urlRtmpTidakValid() {
        assertFalse(Presets.isValidRtmpUrl(""))
        assertFalse(Presets.isValidRtmpUrl("rtmp://host"))
        assertFalse(Presets.isValidRtmpUrl("rtmp://host/"))
        assertFalse(Presets.isValidRtmpUrl("http://host/app/key"))
        assertFalse(Presets.isValidRtmpUrl("rtmp://host/app key"))
    }

    @Test
    fun rotasiMengikutiOrientasi() {
        assertEquals(90, Presets.rotationFor(true))
        assertEquals(0, Presets.rotationFor(false))
    }

    @Test
    fun bitrateDibatasi() {
        assertEquals(300_000, Presets.bitrateBps(1))
        assertEquals(3_000_000, Presets.bitrateBps(3000))
        assertEquals(20_000_000, Presets.bitrateBps(999_999))
    }

    @Test
    fun ukuranPresetAmanUntukEncoder() {
        for (q in Presets.qualities) {
            assertTrue("${q.label}: lebar harus lebih besar dari tinggi", q.width > q.height)
            assertEquals("${q.label}: lebar harus genap", 0, q.width % 2)
            assertEquals("${q.label}: tinggi harus genap", 0, q.height % 2)
            assertTrue(q.defaultBitrateKbps in Presets.MIN_BITRATE_KBPS..Presets.MAX_BITRATE_KBPS)
        }
    }
}
