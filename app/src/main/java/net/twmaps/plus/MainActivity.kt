package net.twmaps.plus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import okhttp3.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private val baseName="TWMaps-base-release"
    private val downloadUrl = "https://selected-capital-treefrog.ngrok-free.app/apk/${baseName}.zip"
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadFile: File
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val downloadButton = findViewById<Button>(R.id.buttonDownload)
        progressBar = findViewById(R.id.progressBar)

        downloadFile = File(getExternalFilesDir(null), "${baseName}.zip")

        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 0

        downloadButton.setOnClickListener {
            checkInstallPermission()
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
                    Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = ProgressBar.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val contentLength = response.body?.contentLength() ?: -1
                    val totalLength = if (contentLength > 0) downloadedBytes + contentLength else -1L

                    val input = response.body?.byteStream() ?: return
                    val raf = RandomAccessFile(tempFile, "rw")
                    raf.seek(downloadedBytes)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = downloadedBytes

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress = if (totalLength > 0) (100 * totalRead / totalLength).toInt() else -1
                        runOnUiThread {
                            if (progress != -1) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = progress
                            } else {
                                progressBar.isIndeterminate = true
                            }
                        }
                    }

                    raf.close()
                    input.close()

                    if (tempFile.renameTo(downloadFile)) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Download complete", Toast.LENGTH_SHORT).show()
                        }

                        val outputDir = File(downloadFile.parentFile, baseName)
                        if (!outputDir.exists()) outputDir.mkdirs()

                        unzipFile(downloadFile, outputDir)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Download complete, but failed to rename file", Toast.LENGTH_LONG).show()
                        }
                    }

                    runOnUiThread {
                        progressBar.visibility = ProgressBar.GONE
                    }


                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error saving file", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = ProgressBar.GONE
                    }
                }
            }
        })
    }
    private fun unzipFile(zipFile: File, targetDirectory: File) {
        thread {
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
                    Toast.makeText(this, "Unzip complete", Toast.LENGTH_SHORT).show()
                    installApkFromInternalStorage()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                Toast.makeText(this, "No app can handle installation", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Installation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkInstallPermission() {
        if (!packageManager.canRequestPackageInstalls()) {
            // Request permission
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, 1111)
        } else {

            progressBar.progress = 0
            progressBar.visibility = ProgressBar.VISIBLE
            downloadWithResume(downloadUrl)
        }
    }
}