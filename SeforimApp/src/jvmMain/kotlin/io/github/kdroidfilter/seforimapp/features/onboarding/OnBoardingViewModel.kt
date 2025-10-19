package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.platformtools.releasefetcher.github.GitHubReleaseFetcher
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import com.github.luben.zstd.ZstdInputStream
import io.github.kdroidfilter.seforimapp.logger.debugln

class OnBoardingViewModel(
    private val settings: AppSettings,
    private val gitHubReleaseFetcher: GitHubReleaseFetcher
) : ViewModel() {

    private val okHttp = OkHttpClient()

    private fun isDatabaseAvailable(): Boolean {
        val path = settings.getDatabasePath()
        return path != null && File(path).exists()
    }

    init {
        if (!isDatabaseAvailable()) {
            viewModelScope.launch(Dispatchers.Default) {
                runCatching { performSetup() }
                    .onFailure { e ->
                        _downloadingInProgress.value = false
                        _extractingInProgress.value = false
                        _downloadSpeedBytesPerSec.value = 0L
                        _errorMessage.value = e.message ?: e.toString()
                    }
            }
        }
    }

    private val _isDatabaseLoaded = MutableStateFlow(isDatabaseAvailable())
    val isDatabaseLoaded = _isDatabaseLoaded.asStateFlow()

    private val _downloadingInProgress = MutableStateFlow(false)
    val downloadingInProgress = _downloadingInProgress.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _extractingInProgress = MutableStateFlow(false)
    val extractingInProgress = _extractingInProgress.asStateFlow()

    private val _extractProgress = MutableStateFlow(0f)
    val extractProgress = _extractProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    // Byte-level download metrics
    private val _downloadedBytes = MutableStateFlow(0L)
    private val _downloadTotalBytes = MutableStateFlow<Long?>(null)
    private val _downloadSpeedBytesPerSec = MutableStateFlow(0L)

    // Combined onboarding state
    val state: StateFlow<OnBoardingState> = combine(
        isDatabaseLoaded,
        downloadingInProgress,
        downloadProgress,
        extractingInProgress,
        extractProgress,
        _errorMessage,
        _downloadedBytes,
        _downloadTotalBytes,
        _downloadSpeedBytesPerSec
    ) { values: Array<Any?> ->
        val isDbLoaded = values[0] as Boolean
        val isDownloading = values[1] as Boolean
        val dlProgress = values[2] as Float
        val isExtracting = values[3] as Boolean
        val exProgress = values[4] as Float
        val error = values[5] as String?
        val bytesRead = values[6] as Long
        val totalBytes = values[7] as Long?
        val speed = values[8] as Long
        OnBoardingState(
            isDatabaseLoaded = isDbLoaded,
            downloadingInProgress = isDownloading,
            downloadProgress = dlProgress,
            errorMessage = error,
            extractingInProgress = isExtracting,
            extractProgress = exProgress,
            downloadedBytes = bytesRead,
            downloadTotalBytes = totalBytes,
            downloadSpeedBytesPerSec = speed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OnBoardingState(
            isDatabaseLoaded = _isDatabaseLoaded.value,
            downloadingInProgress = _downloadingInProgress.value,
            downloadProgress = _downloadProgress.value,
            errorMessage = _errorMessage.value,
            extractingInProgress = _extractingInProgress.value,
            extractProgress = _extractProgress.value,
            downloadedBytes = _downloadedBytes.value,
            downloadTotalBytes = _downloadTotalBytes.value,
            downloadSpeedBytesPerSec = _downloadSpeedBytesPerSec.value
        )
    )

    fun onEvent(event: OnBoardingEvents) {
        when (event) {
            OnBoardingEvents.onFinish -> {
                // No-op for now
            }
        }
    }

    private suspend fun performSetup() {
        // Prepare UI state early so progress UI appears immediately
        _errorMessage.value = null
        _downloadingInProgress.value = true
        _downloadProgress.value = 0f
        _downloadedBytes.value = 0L
        _downloadTotalBytes.value = null
        _downloadSpeedBytesPerSec.value = 0L

        // 1) Fetch latest release and find .zst asset
        val latestRelease = withContext(Dispatchers.IO) { gitHubReleaseFetcher.getLatestRelease() }
            ?: error("No release found")

        val asset = latestRelease.assets.firstOrNull { it.name.endsWith(".zst", ignoreCase = true) }
            ?: error("No .zst database asset found in latest release")

        debugln { asset.toString() }
        val downloadUrl = asset.browser_download_url
        val homeDir = System.getProperty("user.home") ?: error("Cannot resolve user.home")
        val dbDir = File(homeDir, "SeforimApp/Db").apply { mkdirs() }

        val zstFile = File(dbDir, asset.name)
        val dbFile = File(dbDir, asset.name.removeSuffix(".zst").let { name -> if (name.endsWith(".db")) name else "$name.db" })

        // 2) Download with progress
        var lastBytes = 0L
        var lastTimeNs = System.nanoTime()
        downloadFile(downloadUrl, zstFile) { read, total ->
            _downloadedBytes.value = read
            _downloadTotalBytes.value = total
            val now = System.nanoTime()
            val dt = now - lastTimeNs
            if (dt >= 200_000_000L) { // update speed ~5 times per second
                val delta = read - lastBytes
                if (delta >= 0) {
                    val bps = (delta.toDouble() * 1_000_000_000.0 / dt.toDouble()).toLong()
                    _downloadSpeedBytesPerSec.value = bps
                    lastBytes = read
                    lastTimeNs = now
                }
            }
            if (total != null && total > 0) {
                _downloadProgress.value = (read.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
            }
        }
        _downloadingInProgress.value = false
        _downloadProgress.value = 1f
        val finalSize = zstFile.length()
        _downloadedBytes.value = finalSize
        if (_downloadTotalBytes.value == null) {
            _downloadTotalBytes.value = finalSize
        }
        _downloadSpeedBytesPerSec.value = 0L

        // 3) Extract with progress using zstd-jni
        _extractingInProgress.value = true
        _extractProgress.value = 0f
        withContext(Dispatchers.IO) {
            extractZst(zstFile, dbFile) { p ->
                _extractProgress.value = p.coerceIn(0f, 1f)
            }
        }
        _extractingInProgress.value = false
        _extractProgress.value = 1f

        // 4) Clean up and persist path
        runCatching { zstFile.delete() }
        settings.setDatabasePath(dbFile.absolutePath)
        _isDatabaseLoaded.value = true
    }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onBytes: (readSoFar: Long, totalBytes: Long?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/octet-stream")
                .addHeader("User-Agent", "SeforimApp/1.0 (+https://github.com/kdroidFilter/SeforimApp)")
                .build()

            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: ${response.code}")
                val body = response.body
                val length = body.contentLength().takeIf { it > 0 }
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            total += read
                            onBytes(total, length)
                        }
                        out.flush()
                    }
                }
            }
        }
        // Final callback to ensure UI shows completed values
        onBytes(dest.length(), dest.length().takeIf { it > 0L })
    }

    private fun extractZst(sourceZst: File, targetDb: File, onProgress: (Float) -> Unit) {
        class CountingInputStream(`in`: FileInputStream) : FilterInputStream(`in`) {
            var count: Long = 0
                private set
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val r = super.read(b, off, len)
                if (r > 0) count += r
                return r
            }
            override fun read(): Int {
                val r = super.read()
                if (r >= 0) count += 1
                return r
            }
        }

        val totalCompressed = sourceZst.length().coerceAtLeast(1L)
        FileInputStream(sourceZst).use { fis ->
            val cis = CountingInputStream(fis)
            ZstdInputStream(cis).use { zin ->
                FileOutputStream(targetDb).use { out ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = zin.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        onProgress(cis.count.toFloat() / totalCompressed.toFloat())
                    }
                    out.fd.sync()
                }
            }
        }
        onProgress(1f)
    }
}
