package com.iman.pdf.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ProfilePhoto {

    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val src = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext null
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(
                src,
                (src.width - side) / 2,
                (src.height - side) / 2,
                side,
                side
            )
            val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
            val file = File(context.filesDir, "profile_" + System.currentTimeMillis() + ".jpg")
            FileOutputStream(file).use {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            clearOld(context, file.name)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        clearOld(context, "")
    }

    private fun clearOld(context: Context, keep: String) {
        context.filesDir.listFiles()?.forEach {
            if (it.name.startsWith("profile_") && it.name != keep) {
                it.delete()
            }
        }
    }
}

@Composable
fun Avatar(path: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(path) {
        if (path.isEmpty()) {
            null
        } else {
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Profile",
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}
