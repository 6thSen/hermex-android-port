package com.uzairansar.hermex

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test
    fun sharedAttachmentUrisCollectsAndDeduplicatesEveryAndroidUriSource() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stream = Uri.parse("content://shares/stream")
        val data = Uri.parse("content://shares/data")
        val clipped = Uri.parse("content://shares/clipped")
        val clipData = ClipData.newUri(context.contentResolver, "shares", data).apply {
            addItem(ClipData.Item(clipped))
            addItem(ClipData.Item(stream))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, stream)
            this.data = data
            this.clipData = clipData
        }

        assertEquals(listOf(stream, data, clipped), intent.sharedAttachmentUris())
    }

    @Test
    fun sharedAttachmentUrisSupportsSendMultipleExtrasAndClipData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = Uri.parse("content://shares/first")
        val second = Uri.parse("content://shares/second")
        val third = Uri.parse("content://shares/third")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newUri(context.contentResolver, "shares", third)
        }

        assertEquals(listOf(first, second, third), intent.sharedAttachmentUris())
    }
}
