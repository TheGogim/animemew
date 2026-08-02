package com.mew.animemew.data.version

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// =========================================================
//  ApkDownloader — Descarga e instala APKs dentro de la app.
//
//  FLUJO:
//  1. Verificar si tenemos permiso para instalar apps desconocidas
//  2. Si no, abrir Settings para que el usuario lo conceda
//  3. Descargar el APK con DownloadManager del sistema
//  4. Cuando termine, abrir el instalador nativo de Android
//
//  PERMISOS NECESARIOS:
//  - REQUEST_INSTALL_PACKAGES (AndroidManifest)
//  - WRITE_EXTERNAL_STORAGE (solo < Android 10)
// =========================================================

class ApkDownloader(private val context: Context) {

    private val TAG = "ApkDownloader"

    // Estado de la descarga
    sealed class DownloadState {
        object Idle : DownloadState()
        object RequestingPermission : DownloadState()
        object Downloading : DownloadState()
        data class Progress(val percent: Int) : DownloadState()
        object Installing : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var downloadId: Long = -1L
    private var apkFile: File? = null

    // Receiver para detectar cuando la descarga termina
    private var downloadReceiver: BroadcastReceiver? = null

    /**
     * Verifica si la app tiene permiso para instalar paquetes.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true  // < Android 8 no necesita permiso
        }
    }

    /**
     * Abre la pantalla de Settings para que el usuario conceda el permiso
     * de instalar apps de origen desconocido.
     */
    fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            _state.value = DownloadState.RequestingPermission
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Inicia la descarga del APK.
     * @param url URL directa al archivo .apk
     * @param fileName Nombre del archivo (ej: "animemew-v9.0.apk")
     */
    fun downloadApk(url: String, fileName: String = "animemew-update.apk") {
        Log.i(TAG, "Iniciando descarga: $url → $fileName")

        // Verificar permiso primero
        if (!canInstallPackages()) {
            Log.w(TAG, "Sin permiso para instalar, solicitando...")
            requestInstallPermission()
            return
        }

        _state.value = DownloadState.Downloading

        try {
            // Limpiar descarga anterior si existe
            apkFile?.let { if (it.exists()) it.delete() }

            // Crear request de descarga
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("AnimeMew — Actualización")
                setDescription("Descargando nueva versión...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // Guardar en carpeta de descargas
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            }

            // Iniciar descarga
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            // Registrar receiver para detectar fin de descarga
            registerDownloadReceiver()

            Log.i(TAG, "Descarga iniciada, ID=$downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando descarga: ${e.message}")
            _state.value = DownloadState.Error("No se pudo iniciar la descarga: ${e.message}")
        }
    }

    /**
     * Registra un BroadcastReceiver para detectar cuando la descarga termina.
     */
    private fun registerDownloadReceiver() {
        // Desregistrar anterior si existe
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId) {
                    Log.i(TAG, "Descarga completada, ID=$id")
                    onDownloadComplete()
                }
            }
        }

        context.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    /**
     * Se llama cuando la descarga termina.
     * Verifica que sea exitosa y abre el instalador.
     */
    private fun onDownloadComplete() {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Log.i(TAG, "✅ Descarga exitosa, abriendo instalador")
                    installApk()
                } else {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Log.e(TAG, "❌ Descarga fallida, status=$status reason=$reason")
                    _state.value = DownloadState.Error("La descarga fallió (código: $reason)")
                }
            }
            cursor?.close()

            // Desregistrar receiver
            downloadReceiver?.let {
                try { context.unregisterReceiver(it) } catch (_: Exception) {}
                downloadReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando descarga completada: ${e.message}")
            _state.value = DownloadState.Error("Error procesando descarga: ${e.message}")
        }
    }

    /**
     * Abre el instalador nativo de Android con el APK descargado.
     */
    private fun installApk() {
        _state.value = DownloadState.Installing

        try {
            // Buscar el archivo descargado
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            apkFile = downloadDir?.listFiles()?.find { it.name.endsWith(".apk") }

            val file = apkFile
            if (file == null || !file.exists()) {
                _state.value = DownloadState.Error("No se encontró el APK descargado")
                return
            }

            Log.i(TAG, "Instalando: ${file.absolutePath}")

            // Usar FileProvider para Android 7+
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            Log.i(TAG, "✅ Instalador abierto")

        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo instalador: ${e.message}")
            _state.value = DownloadState.Error("No se pudo abrir el instalador: ${e.message}")
        }
    }

    /**
     * Resetea el estado (para reintentar).
     */
    fun reset() {
        _state.value = DownloadState.Idle
        downloadId = -1L
        apkFile = null
    }
}
