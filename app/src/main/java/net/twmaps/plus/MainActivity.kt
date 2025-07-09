package net.twmaps.plus

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import net.twmaps.plus.databinding.ActivityMainBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    private val baseName = "TWMaps-base-release"
//    private val downloadUrl = if (BuildConfig.DEBUG) "http://10.10.10.33:8088/apk/${baseName}.zip"
//    else "http://nav.twautomotives.com:8088/apk/${baseName}.zip"
    private val downloadUrl = "http://nav.twautomotives.com:8088/apk/${baseName}.zip"

    //    private val downloadUrl = "https://selected-capital-treefrog.ngrok-free.app/apk/${baseName}.zip"
    private lateinit var downloadFile: File
    private val client = OkHttpClient()
    private val activityLauncher: ActivityLauncher = ActivityLauncher(this)
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadFile = File(getExternalFilesDir(null), "${baseName}.zip")

        binding.progressBar.isIndeterminate = false
        binding.progressBar.max = 100
        binding.progressBar.progress = 0

        binding.buttonDownload.setOnClickListener {
            checkInstallPermission()
        }
        NetworkConnectionLiveData(this)
            .observe(this, Observer { isConnected ->
                if (!isConnected) {
                    binding.buttonDownload.isEnabled = false
                    binding.buttonDownload.setBackgroundResource(R.drawable.button_disabled_bg)
                    binding.buttonDownload.setTextColor(getColor(R.color.grey))
                    binding.tvTextBottom.text = getString(R.string.please_connect_to_internet)
                    binding.tvTextBottom.setTextColor(getColor(R.color.red))
                    return@Observer
                }
                binding.buttonDownload.isEnabled = true
                binding.buttonDownload.setBackgroundResource(R.drawable.button_enabled_bg)
                binding.buttonDownload.setTextColor(Color.WHITE)
                binding.tvTextBottom.text =
                    getString(R.string.please_download_twmaps_latest_application_version)
                binding.tvTextBottom.setTextColor(getColor(R.color.black))
                showResumeButton()
            })

        showResumeButton()
    }

    private fun showResumeButton() {
        val tempFile = File(downloadFile.absolutePath + ".part")
        if (tempFile.exists()) {
            binding.buttonDownload.text = "Resume Downloading"
        }
    }

    private fun downloadWithResume(url: String) {
        val tempFile = File(downloadFile.absolutePath + ".part")
        val downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$downloadedBytes-")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                e.printStackTrace()
                runOnUiThread {
                    showHideButtonText(false)
                    Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = ProgressBar.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val contentLength = response.body?.contentLength() ?: -1
                    val totalLength =
                        if (contentLength > 0) downloadedBytes + contentLength else -1L

                    val input = response.body?.byteStream() ?: return
                    val raf = RandomAccessFile(tempFile, "rw")
                    raf.seek(downloadedBytes)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = downloadedBytes

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress =
                            if (totalLength > 0) (100 * totalRead / totalLength).toInt() else -1
                        runOnUiThread {
                            if (progress != -1) {
                                binding.progressBar.isIndeterminate = false
                                binding.progressBar.progress = progress
                            } else {
                                binding.progressBar.isIndeterminate = true
                            }
                        }
                    }

                    raf.close()
                    input.close()

                    if (tempFile.renameTo(downloadFile)) {
                        val outputDir = File(downloadFile.parentFile, baseName)
                        if (!outputDir.exists()) outputDir.mkdirs()
                        unzipFile(downloadFile, outputDir)

                    } else {
                        runOnUiThread {
                            showHideButtonText(false)
                            Toast.makeText(
                                this@MainActivity,
                                "Download complete, but failed to rename file",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    runOnUiThread {
                        binding.progressBar.visibility = ProgressBar.GONE
                        showHideButtonText(false)
                    }


                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        showHideButtonText(false)
                        Toast.makeText(this@MainActivity, "Error saving file", Toast.LENGTH_SHORT)
                            .show()
                        binding.progressBar.visibility = ProgressBar.GONE
                    }
                }
            }
        })
    }

    private fun unzipFile(zipFile: File, targetDirectory: File) {

        thread {
            runOnUiThread {
                binding.progressBar.visibility = ProgressBar.VISIBLE
                binding.buttonDownload.visibility = ProgressBar.GONE
                binding.tvTextBottom.text = "Unzipping..."
            }
            try {
                ZipInputStream(zipFile.inputStream().buffered()).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDirectory, entry.name)

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zipInputStream.copyTo(out)
                            }
                        }

                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }

                runOnUiThread {
                    zipFile.delete()
                    showHideButtonText(false)
                    installApkFromInternalStorage()


                }
            } catch (e: Exception) {
                e.printStackTrace()
                showHideButtonText(false)
                runOnUiThread {
                    Toast.makeText(this, "Unzip failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApkFromInternalStorage() {
        try {
            // 1. Get the APK file from internal storage
            val apkFile = File(getExternalFilesDir(null), "$baseName/${baseName}.apk")
            if (!apkFile.exists()) {
                showHideButtonText(false)
                Toast.makeText(this, "APK not found", Toast.LENGTH_SHORT).show()
                return
            }

            // 2. Generate a content URI using FileProvider
            val apkUri = FileProvider.getUriForFile(
                this, "${packageName}.provider",  // Must match manifest authorities
                apkFile
            )

            // 3. Create the install intent
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 4. Verify the intent can be handled
            if (installIntent.resolveActivity(packageManager) != null) {
                startActivity(installIntent)
            } else {
                showHideButtonText(false)
                Toast.makeText(this, "No app can handle installation", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            showHideButtonText(false)
            Toast.makeText(this, "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkInstallPermission() {
        if (!packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            activityLauncher.launch(intent) { result ->
                if (result.resultCode == RESULT_OK) {
                    startDownloading()
                }
                Log.i(
                    "TAG",
                    "[requestAccessBackgroundLocationPermission] ACCESS_BACKGROUND_LOCATION ActivityLauncher returned $result."
                )
            }
        } else {
            startDownloading()
        }
    }

    private fun startDownloading() {
        binding.progressBar.progress = 0
        binding.progressBar.visibility = ProgressBar.VISIBLE
        downloadWithResume(downloadUrl)
        showHideButtonText(true)
    }

    private fun showHideButtonText(isProgressing: Boolean) {
        if (isProgressing) {
            Handler(mainLooper).postDelayed({
                binding.tvTextBottom.text = "Downloading..."
                binding.progressBar.visibility = View.VISIBLE
                binding.buttonDownload.visibility = View.GONE
            }, 500)
        } else {
            binding.progressBar.visibility = View.GONE
            binding.tvTextBottom.text =
                getString(R.string.please_download_twmaps_latest_application_version)
            binding.buttonDownload.visibility = View.VISIBLE
        }
    }


}