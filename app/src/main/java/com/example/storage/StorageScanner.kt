package com.example.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.FileCategory
import com.example.data.FileItemEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class StorageScanner(private val context: Context) : HammingDistanceCalculator {

    companion object {
        private const val TAG = "StorageScanner"
    }

    fun scanDeviceStorageFlow(computeHashes: Boolean = false): Flow<List<FileItemEntity>> = flow {
        scanDeviceStorage(computeHashes) { batch ->
            emit(batch)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun scanDeviceStorage(computeHashes: Boolean = false): List<FileItemEntity> {
        val discovered = mutableListOf<FileItemEntity>()
        scanDeviceStorage(computeHashes) { batch ->
            discovered.addAll(batch)
        }
        return discovered
    }

    suspend fun scanDeviceStorage(
        computeHashes: Boolean = false,
        onBatchDiscovered: suspend (List<FileItemEntity>) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val processedPaths = mutableSetOf<String>()
        val currentBatch = mutableListOf<FileItemEntity>()
        var totalDiscovered = 0

        val emitItem: suspend (FileItemEntity) -> Unit = { item ->
            currentCoroutineContext().ensureActive()
            currentBatch.add(item)
            if (currentBatch.size >= 100) {
                onBatchDiscovered(currentBatch.toList())
                totalDiscovered += currentBatch.size
                currentBatch.clear()
            }
        }

        // Primary Source: MediaStore Scan (Scoped Storage compatible)
        try {
            scanMediaStore(processedPaths, computeHashes = computeHashes, onItemDiscovered = emitItem)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning MediaStore: ${e.message}", e)
        }

        // Supplementary Source: Raw Directory Traversal (Only if All Files Access / External storage manager or read permissions are granted)
        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            try {
                Environment.getExternalStorageDirectory()?.canRead() == true
            } catch (_: Exception) {
                false
            }
        }

        if (hasAllFilesAccess) {
            try {
                val externalDir = Environment.getExternalStorageDirectory()
                if (externalDir != null && externalDir.exists() && externalDir.canRead()) {
                    scanDirectoryRecursively(externalDir, processedPaths, depth = 0, maxDepth = 6, computeHashes = computeHashes, onItemDiscovered = emitItem)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning external directory: ${e.message}", e)
            }
        }

        if (currentBatch.isNotEmpty()) {
            totalDiscovered += currentBatch.size
            onBatchDiscovered(currentBatch.toList())
            currentBatch.clear()
        }

        Log.i(TAG, "Storage scan completed. Total real files discovered: $totalDiscovered")
        totalDiscovered
    }

    private suspend fun scanDirectoryRecursively(
        dir: File,
        processedPaths: MutableSet<String>,
        depth: Int,
        maxDepth: Int,
        computeHashes: Boolean,
        onItemDiscovered: suspend (FileItemEntity) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            currentCoroutineContext().ensureActive()
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                if (file.name.equals("Android", ignoreCase = true) && depth == 0) continue
                scanDirectoryRecursively(file, processedPaths, depth + 1, maxDepth, computeHashes, onItemDiscovered)
            } else if (file.isFile && file.length() > 0) {
                val absolutePath = file.absolutePath
                if (processedPaths.add(absolutePath)) {
                    val category = determineCategory(file.name)
                    val hash = if (computeHashes) computeFileHashQuietly(file) else ""
                    val visualHash = if (computeHashes && category == FileCategory.IMAGES) computeDHashQuietly(file) else ""
                    val fileItem = FileItemEntity(
                        name = file.name,
                        path = absolutePath,
                        category = category.name,
                        sizeBytes = file.length(),
                        dateModifiedMs = file.lastModified(),
                        md5Hash = hash,
                        visualSimilarityHash = visualHash
                    )
                    onItemDiscovered(fileItem)
                }
            }
        }
    }

    private suspend fun scanMediaStore(
        processedPaths: MutableSet<String>,
        computeHashes: Boolean,
        onItemDiscovered: suspend (FileItemEntity) -> Unit
    ) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        context.contentResolver.query(
            collectionUri,
            projection,
            null,
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val path = if (dataColumn != -1) cursor.getString(dataColumn) else null
                val name = if (nameColumn != -1) cursor.getString(nameColumn) else null
                val size = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L
                val dateSec = if (dateColumn != -1) cursor.getLong(dateColumn) else 0L

                if (!path.isNullOrBlank() && !name.isNullOrBlank() && size > 0) {
                    if (processedPaths.add(path)) {
                        val category = determineCategory(name)
                        val file = File(path)
                        val hash = if (computeHashes && file.exists() && file.canRead()) computeFileHashQuietly(file) else ""
                        val visualHash = if (computeHashes && category == FileCategory.IMAGES && file.exists() && file.canRead()) computeDHashQuietly(file) else ""
                        
                        val item = FileItemEntity(
                            name = name,
                            path = path,
                            category = category.name,
                            sizeBytes = size,
                            dateModifiedMs = if (dateSec > 0) dateSec * 1000L else System.currentTimeMillis(),
                            md5Hash = hash,
                            visualSimilarityHash = visualHash
                        )
                        onItemDiscovered(item)
                    }
                }
            }
        }
    }

    suspend fun computeFileHash(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead()) return@withContext ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                ensureActive()
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun computeFileHashQuietly(file: File): String {
        return try {
            computeFileHash(file)
        } catch (e: Exception) {
            ""
        }
    }

    fun determineCategory(fileName: String): FileCategory {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> FileCategory.IMAGES
            "pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv" -> FileCategory.DOCUMENTS
            "mp3", "m4a", "wav", "flac", "aac", "ogg" -> FileCategory.AUDIO
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> FileCategory.VIDEO
            "zip", "rar", "7z", "tar", "gz" -> FileCategory.ARCHIVES
            "apk", "xapk", "apks" -> FileCategory.APKS
            else -> FileCategory.OTHER
        }
    }

    fun computeDHashFromBitmap(bitmap: Bitmap): String {
        return try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            var hashBits = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val pixelLeft = scaledBitmap.getPixel(x, y)
                    val pixelRight = scaledBitmap.getPixel(x + 1, y)
                    val grayLeft = (Color.red(pixelLeft) * 299 + Color.green(pixelLeft) * 587 + Color.blue(pixelLeft) * 114) / 1000
                    val grayRight = (Color.red(pixelRight) * 299 + Color.green(pixelRight) * 587 + Color.blue(pixelRight) * 114) / 1000
                    if (grayLeft > grayRight) {
                        hashBits = hashBits or (1L shl (63 - bitIndex))
                    }
                    bitIndex++
                }
            }
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            String.format("%016x", hashBits)
        } catch (e: Exception) {
            Log.e(TAG, "dHash computation from bitmap failed: ${e.message}")
            ""
        }
    }

    fun decodeSampledBitmapFromFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode sampled bitmap: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    suspend fun computeDHash(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || !isImageFile(file.name)) return@withContext ""
        try {
            val bitmap = decodeSampledBitmapFromFile(file, 64, 64) ?: return@withContext ""
            ensureActive()
            val hash = computeDHashFromBitmap(bitmap)
            bitmap.recycle()
            hash
        } catch (e: Exception) {
            Log.e(TAG, "dHash computation failed for ${file.name}: ${e.message}")
            ""
        }
    }

    suspend fun computeVideoDHash(file: File, timeUs: Long = 1_000_000L): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || !isVideoFile(file.name)) return@withContext ""
        ensureActive()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val keyframeBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (keyframeBitmap != null) {
                val hash = computeDHashFromBitmap(keyframeBitmap)
                keyframeBitmap.recycle()
                hash
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video keyframe dHash failed for ${file.name}: ${e.message}")
            ""
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv")
    }

    fun isPdfFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "pdf"
    }

    fun isDocumentFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv")
    }

    suspend fun computeDocumentFingerprint(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || !isDocumentFile(file.name)) return@withContext ""
        try {
            ensureActive()
            val length = file.length()
            if (length == 0L) return@withContext ""
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("DOC_SIZE:$length:".toByteArray())

            file.inputStream().use { input ->
                if (length <= 8192) {
                    val buffer = ByteArray(8192)
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        digest.update(buffer, 0, bytesRead)
                    }
                } else {
                    val header = ByteArray(4096)
                    val headerRead = input.read(header)
                    if (headerRead > 0) {
                        digest.update(header, 0, headerRead)
                    }
                    java.io.RandomAccessFile(file, "r").use { raf ->
                        raf.seek(length - 4096)
                        val tail = ByteArray(4096)
                        val tailRead = raf.read(tail)
                        if (tailRead > 0) {
                            digest.update(tail, 0, tailRead)
                        }
                    }
                }
            }
            ensureActive()
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            Log.w(TAG, "Document fingerprint calculation failed for ${file.name}: ${e.message}")
            ""
        }
    }

    suspend fun computeDHashQuietly(file: File): String {
        return try {
            computeDHash(file)
        } catch (e: Exception) {
            ""
        }
    }

    fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp", "heic", "bmp", "gif")
    }

    override fun calculateHammingDistance(hash1: String, hash2: String): Int {
        if (hash1.length != 16 || hash2.length != 16) return -1
        return try {
            val val1 = hash1.toULong(16)
            val val2 = hash2.toULong(16)
            java.lang.Long.bitCount((val1 xor val2).toLong())
        } catch (e: Exception) {
            -1
        }
    }
}
