package com.mew.animemew.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// =========================================================
//  GlobalErrorHandler — captura crashes y los expone como StateFlow.
//
//  Cómo funciona:
//   - En AnimeApp.onCreate() instalamos un UncaughtExceptionHandler
//     que captura cualquier crash no manejado.
//   - El handler almacena el throwable en `currentCrash` (StateFlow).
//   - En MainActivity observamos este flow y, si no es null,
//     mostramos un overlay Compose a pantalla completa con el
//     stack trace y un botón "Copiar".
//
//  Por qué NO crashamos la app:
//   - El usuario puede copiar el error y mandarlo por Telegram.
//   - La app se puede "reiniciar" matando el proceso o cerrando
//     el overlay y volviendo al estado anterior.
//
//  Adicionalmente, cualquier parte de la app puede llamar a
//  `reportError(throwable)` o `reportError(message)` para mostrar
//   errores no fatales en el mismo overlay (ej: falló una migración
//   pero la app sigue funcional).
// =========================================================

data class CrashInfo(
    val timestamp: Long = System.currentTimeMillis(),
    val threadName: String,
    val message: String,
    val stackTrace: String,
    val isFatal: Boolean = true
)

object GlobalErrorHandler {

    private const val TAG = "GlobalErrorHandler"

    private val _currentCrash = MutableStateFlow<CrashInfo?>(null)
    val currentCrash: StateFlow<CrashInfo?> = _currentCrash.asStateFlow()

    /**
     * Instala el handler global de uncaught exceptions.
     * Llamar UNA sola vez en Application.onCreate().
     *
     * IMPORTANTE: NO delegamos al handler default (que cerraría la app).
     * En su lugar, almacenamos el error y dejamos que la app siga viva
     * para que MainActivity pueda renderizar el overlay con el error.
     *
     * Casos:
     *  - Crash en background thread: app sigue viva, overlay se muestra. ✅
     *  - Crash en main thread: el Looper puede quedar roto y el overlay no
     *    se renderiza, pero al menos guardamos el error en logcat.
     *  - Errores no fatales (vía reportError): app sigue viva, overlay se muestra. ✅
     */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)

            _currentCrash.value = CrashInfo(
                threadName = thread.name,
                message = throwable.message ?: throwable.javaClass.simpleName,
                stackTrace = buildStackTrace(throwable),
                isFatal = true
            )

            // NO delegar al handler default. La app sigue "viva" para que
            // el overlay se pueda renderizar. Si el crash fue en el main thread
            // y el Looper está roto, el usuario debe forzar el cierre desde
            // ajustes del sistema. Es raro que pase gracias a los try/catch
            // que tenemos en los ViewModels.
        }

        Log.i(TAG, "Global crash handler installed")
    }

    /**
     * Reporta un error NO fatal. Muestra el overlay pero la app sigue corriendo.
     * Útil para errores de migración, fallos de red, etc.
     */
    fun reportError(message: String, throwable: Throwable? = null) {
        Log.w(TAG, "Non-fatal error: $message", throwable)
        _currentCrash.value = CrashInfo(
            threadName = Thread.currentThread().name,
            message = message,
            stackTrace = throwable?.let { buildStackTrace(it) } ?: "(sin stacktrace)",
            isFatal = false
        )
    }

    /**
     * Descarta el error actual (cierra el overlay).
     */
    fun dismiss() {
        _currentCrash.value = null
    }

    /**
     * Mata el proceso actual (reinicia la app).
     */
    fun killProcess() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun buildStackTrace(throwable: Throwable): String {
        val sb = StringBuilder()
        sb.append(throwable.javaClass.name)
        sb.append(": ")
        sb.append(throwable.message)
        sb.append("\n")
        for (element in throwable.stackTrace) {
            sb.append("    at ")
            sb.append(element.toString())
            sb.append("\n")
        }
        var cause = throwable.cause
        while (cause != null) {
            sb.append("Caused by: ")
            sb.append(cause.javaClass.name)
            sb.append(": ")
            sb.append(cause.message)
            sb.append("\n")
            for (element in cause.stackTrace.take(15)) {
                sb.append("    at ")
                sb.append(element.toString())
                sb.append("\n")
            }
            cause = cause.cause
        }
        return sb.toString()
    }

    /**
     * Formatea el CrashInfo para copiar al portapapeles.
     */
    fun toClipboardText(crash: CrashInfo): String {
        return buildString {
            appendLine("=== AnimeMew Error Report ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(crash.timestamp))}")
            appendLine("Thread: ${crash.threadName}")
            appendLine("Fatal: ${crash.isFatal}")
            appendLine("App version: 9.1 (versionCode 14)")
            appendLine()
            appendLine("Message: ${crash.message}")
            appendLine()
            appendLine("Stack trace:")
            appendLine(crash.stackTrace)
            appendLine("=== End of report ===")
        }
    }
}
