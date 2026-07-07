package com.example.service

import org.junit.Assert.*
import org.junit.Test

class EngineTests {

    @Test
    fun testMetadataExtractor_explicitIds() {
        val extractor = MetadataExtractor()
        val flat = listOf(
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_author_username", "robot_volodya", null),
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_caption_component", "Look at this cool reel", null),
            TreeNodeInfo("android.widget.Button", "com.instagram.android:id/music_button", null, "Cool Song")
        )

        val result = extractor.extract(flat)
        assertEquals("robot_volodya", result.username)
        assertEquals("Look at this cool reel", result.caption)
        assertEquals("Cool Song", result.audio)
    }

    @Test
    fun testMetadataExtractor_fallbacks() {
        val extractor = MetadataExtractor()
        val flat = listOf(
            TreeNodeInfo("android.widget.TextView", "N/A", "Reel by robot_volodya", null),
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/some_audio_id", "Original Audio - user", null),
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/some_caption_id", "Look at this cool reel", null)
        )

        val result = extractor.extract(flat)
        assertEquals("robot_volodya", result.username)
        assertEquals("Look at this cool reel", result.caption)
        assertEquals("Original Audio - user", result.audio)
    }

    @Test
    fun testCompositeSignatureGenerator() {
        val generator = CompositeSignatureGenerator()
        val flat = listOf(
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_author_username", "robot_volodya", null),
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_caption_component", "Look at this cool reel", null),
            TreeNodeInfo("android.widget.Button", "com.instagram.android:id/music_button", null, "Cool Song")
        )
        val metadata = ReelMetadata("robot_volodya", "Look at this cool reel", "Cool Song")
        val signature1 = generator.generate(flat, 123, metadata)
        
        // Ensure same inputs give same signature
        val signature2 = generator.generate(flat, 123, metadata)
        assertEquals(signature1.signatureHash, signature2.signatureHash)
        
        // Ensure difference gives diff signature
        val flatDiff = flat + TreeNodeInfo("android.widget.TextView", "N/A", "Some new text", null)
        val signature3 = generator.generate(flatDiff, 123, metadata)
        assertNotEquals(signature1.signatureHash, signature3.signatureHash)
    }

    @Test
    fun testStressCompositeSignatureGenerator() {
        val generator = CompositeSignatureGenerator()
        val metadata = ReelMetadata("robot_volodya", "Look at this cool reel", "Cool Song")
        val flat = listOf(
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_author_username", "robot_volodya", null),
            TreeNodeInfo("android.widget.TextView", "com.instagram.android:id/clips_caption_component", "Look at this cool reel", null),
            TreeNodeInfo("android.widget.Button", "com.instagram.android:id/music_button", null, "Cool Song"),
            TreeNodeInfo("android.widget.FrameLayout", "com.instagram.android:id/root_clips_layout", null, null)
        )

        // 5000 reels stress test
        val start = System.currentTimeMillis()
        var lastSig: ReelSignature? = null
        for (i in 0 until 5000) {
            val m = ReelMetadata("user_$i", "caption_$i", "song_$i")
            val newFlat = flat + TreeNodeInfo("android.widget.TextView", "N/A", "user_$i", null)
            val sig = generator.generate(newFlat, i, m)
            if (lastSig != null) {
                assertNotEquals(lastSig.signatureHash, sig.signatureHash)
            }
            lastSig = sig
        }
        val end = System.currentTimeMillis()
        assertTrue("Stress test should take less than 1 second, took ${end - start}ms", (end - start) < 1000)
    }
}
