package com.mew.animemew.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// =========================================================
//  CastServer v2 — Servidor embebido para Cast a PC.
//
//  Mejoras v2:
//  - Comando "stop" para detener el video al cerrar
//  - Posición inicial (startPosition)
//  - Tracking de posición del navegador
//  - Comandos: play, pause, resume, seek, stop
//  - Solo 1 cliente a la vez
//  - Detección de desconexión
// =========================================================

class CastServer(
    private val context: Context,
    private val serverPort: Int = 8080
) : NanoHTTPD(serverPort) {

    private val TAG = "CastServer"

    @Volatile var streamUrl: String? = null
    @Volatile var streamReferer: String? = null
    @Volatile var isHls: Boolean = false
    @Volatile var startPosition: Double = 0.0

    @Volatile private var pendingCommand: String? = null

    @Volatile private var clientConnected: Boolean = false
    @Volatile private var lastClientPing: Long = 0

    // Estado de reproducción del navegador
    @Volatile var browserCurrentTime: Double = 0.0
    @Volatile var browserDuration: Double = 0.0
    @Volatile var browserIsPlaying: Boolean = false

    var onPositionUpdate: ((Double, Double) -> Unit)? = null
    var onVideoEnded: (() -> Unit)? = null
    var onClientConnected: (() -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null

    private var nsdManager: NsdManager? = null

    companion object {
        @Volatile
        var instance: CastServer? = null
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "✅ CastServer iniciado en puerto $serverPort")
            registerNsd()

            Thread {
                while (true) {
                    Thread.sleep(3000)
                    if (clientConnected && System.currentTimeMillis() - lastClientPing > 8000) {
                        Log.i(TAG, "❌ Cliente desconectado (timeout)")
                        clientConnected = false
                        onClientDisconnected?.invoke()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando: ${e.message}")
        }
    }

    fun stopServer() {
        try {
            sendStopCommand()
            Thread.sleep(500)
            unregisterNsd()
            stop()
            Log.i(TAG, "✅ CastServer detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo: ${e.message}")
        }
    }

    // =====================================================
    //  Comandos
    // =====================================================

    fun sendPlayCommand(url: String, referer: String?, hls: Boolean, startPos: Double = 0.0) {
        streamUrl = url
        streamReferer = referer
        isHls = hls
        startPosition = startPos

        val proxyUrl = "/proxy?url=${URLEncoder.encode(url, "UTF-8")}"
        val json = JSONObject().apply {
            put("type", "play")
            put("url", proxyUrl)
            put("isHls", hls)
            put("startPosition", startPos)
        }.toString()

        pendingCommand = json
        Log.i(TAG, "📺 Comando play: startPos=${startPos}s")
    }

    fun sendPauseCommand() {
        pendingCommand = JSONObject().put("type", "pause").toString()
    }

    fun sendResumeCommand() {
        pendingCommand = JSONObject().put("type", "resume").toString()
    }

    fun sendSeekCommand(position: Double) {
        pendingCommand = JSONObject().apply {
            put("type", "seek")
            put("position", position)
        }.toString()
    }

    fun sendStopCommand() {
        pendingCommand = JSONObject().put("type", "stop").toString()
        Log.i(TAG, "⏹️ Comando stop enviado")
    }

    fun isClientConnected(): Boolean = clientConnected

    // =====================================================
    //  mDNS
    // =====================================================

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Log.i(TAG, "✅ mDNS: AnimeMew registrado")
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e(TAG, "❌ mDNS: registro fallido ($errorCode)")
        }
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    }

    private fun registerNsd() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "AnimeMew"
                serviceType = "_http._tcp."
                port = serverPort
            }
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error mDNS: ${e.message}")
        }
    }

    private fun unregisterNsd() {
        try {
            nsdManager?.unregisterService(registrationListener)
            nsdManager = null
        } catch (_: Exception) {}
    }

    // =====================================================
    //  HTTP routes
    // =====================================================

    override fun serve(session: NanoHTTPD.IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/" || uri == "/index.html" -> {
                newFixedResponse(Response.Status.OK, "text/html", WEB_PAGE)
            }
            uri.startsWith("/proxy") -> handleProxy(session)
            uri == "/command" -> {
                lastClientPing = System.currentTimeMillis()
                if (!clientConnected) {
                    clientConnected = true
                    Log.i(TAG, "✅ Cliente conectado")
                    onClientConnected?.invoke()
                }
                val cmd = pendingCommand
                pendingCommand = null
                newFixedResponse(Response.Status.OK, "application/json", cmd ?: "{\"type\":\"none\"}")
            }
            uri == "/position" && session.method == Method.POST -> handlePosition(session)
            uri == "/ended" -> {
                Log.i(TAG, "🎬 Video terminado")
                onVideoEnded?.invoke()
                newFixedResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
            }
            else -> newFixedResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun newFixedResponse(status: Response.Status, mime: String, text: String): Response {
        val response = newFixedLengthResponse(status, mime, text)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "*")
        return response
    }

    private fun handlePosition(session: NanoHTTPD.IHTTPSession): Response {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: ""
            val data = JSONObject(body)

            browserCurrentTime = data.optDouble("currentTime", 0.0)
            browserDuration = data.optDouble("duration", 0.0)
            browserIsPlaying = data.optBoolean("isPlaying", false)

            onPositionUpdate?.invoke(browserCurrentTime, browserDuration)
            newFixedResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
        } catch (e: Exception) {
            newFixedResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error")
        }
    }

    private fun handleProxy(session: NanoHTTPD.IHTTPSession): Response {
        val params = session.parameters
        val url = params["url"]?.firstOrNull()
            ?: return newFixedResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing url")

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Referer", streamReferer ?: "https://jkanime.net/")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            val range = session.headers["range"]
            if (range != null) conn.setRequestProperty("Range", range)

            val code = conn.responseCode
            if (code >= 400) {
                return newFixedResponse(Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR, "text/plain", "Error $code")
            }

            val contentType = conn.contentType ?: "application/octet-stream"
            val response = newChunkedResponse(Response.Status.lookup(code) ?: Response.Status.OK, contentType, conn.inputStream)

            conn.getHeaderField("Content-Length")?.let { response.addHeader("Content-Length", it) }
            conn.getHeaderField("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
            conn.getHeaderField("Content-Range")?.let { response.addHeader("Content-Range", it) }
            response.addHeader("Access-Control-Allow-Origin", "*")
            response
        } catch (e: Exception) {
            newFixedResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy error: ${e.message}")
        }
    }

    // =====================================================
    //  Página web con controles custom
    // =====================================================

    private val WEB_PAGE = """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AnimeMew Cast</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
html,body{background:#0D0518;color:#fff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;overflow:hidden;height:100%}
body{display:flex;flex-direction:column}
#header{background:linear-gradient(180deg,rgba(179,136,255,0.15) 0%,transparent 100%);padding:10px 24px;display:flex;align-items:center;justify-content:space-between;z-index:10;transition:opacity 0.3s ease;flex-shrink:0}
#header.hidden{opacity:0;pointer-events:none}
#logo{font-size:16px;font-weight:800;background:linear-gradient(135deg,#B388FF,#E040FB);-webkit-background-clip:text;-webkit-text-fill-color:transparent;letter-spacing:0.5px}
#status{font-size:12px;color:#888;display:flex;align-items:center;gap:6px}
#status .dot{width:8px;height:8px;border-radius:50%;background:#444;transition:background 0.3s}
#status.connected .dot{background:#4CAF50}
#status.connecting .dot{background:#FFC107;animation:pulse 1.5s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:0.3}}
#player-container{flex:1;display:flex;align-items:center;justify-content:center;position:relative;background:#000;min-height:0}
video{width:100%;height:100%;object-fit:contain;background:#000;display:block}
#loading{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);text-align:center;display:none}
#loading .spinner{width:48px;height:48px;border:3px solid rgba(179,136,255,0.2);border-top-color:#B388FF;border-radius:50%;animation:spin 1s linear infinite;margin:0 auto 16px}
@keyframes spin{to{transform:rotate(360deg)}}
#loading .text{color:#888;font-size:14px}
#error,#stopped{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);text-align:center;display:none;max-width:400px}
#error{color:#f44336}
#stopped .icon{font-size:56px;margin-bottom:12px;opacity:0.5}
#stopped .text{color:#888;font-size:15px}
#controls{position:absolute;bottom:0;left:0;right:0;background:linear-gradient(0deg,rgba(13,5,24,0.95) 0%,transparent 100%);padding:16px 24px 12px;z-index:20;transition:opacity 0.4s ease,transform 0.4s ease}
#controls.hidden{opacity:0;transform:translateY(10px);pointer-events:none}
#progress-bar{width:100%;height:20px;cursor:pointer;position:relative;display:flex;align-items:center}
#progress-track{width:100%;height:5px;background:rgba(255,255,255,0.15);border-radius:3px;position:relative}
#progress-fill{height:100%;background:linear-gradient(90deg,#B388FF,#06B6D4);border-radius:3px;width:0%;pointer-events:none}
#progress-buffer{position:absolute;top:0;left:0;height:100%;background:rgba(255,255,255,0.1);border-radius:3px;width:0%;pointer-events:none}
#progress-thumb{position:absolute;top:50%;width:14px;height:14px;border-radius:50%;background:#fff;transform:translate(-50%,-50%);pointer-events:none;opacity:0;transition:opacity 0.2s;box-shadow:0 0 8px rgba(179,136,255,0.6)}
#progress-bar:hover #progress-thumb,#progress-bar.dragging #progress-thumb{opacity:1}
#time-display{display:flex;justify-content:space-between;margin-top:6px;font-size:12px;color:#999;font-variant-numeric:tabular-nums}
#buttons{display:flex;align-items:center;justify-content:center;gap:28px;margin-top:4px}
#buttons button{background:none;border:none;color:#fff;font-size:26px;cursor:pointer;padding:8px;transition:color 0.15s,transform 0.1s;user-select:none}
#buttons button:hover{color:#B388FF}
#buttons button:active{transform:scale(0.88)}
#buttons #play-pause{width:56px;height:56px;border-radius:50%;background:linear-gradient(135deg,#8B5CF6,#6D28D9);display:flex;align-items:center;justify-content:center;font-size:28px;color:#fff}
#buttons #play-pause:hover{color:#fff;filter:brightness(1.15)}
#buttons #fullscreen{font-size:20px}
/* Fullscreen: solo el player-container se hace fullscreen, el header queda fuera */
:fullscreen #header,:fullscreen #status{display:none!important}
:fullscreen #player-container{height:100vh}
:fullscreen #controls{padding-bottom:24px}
:-webkit-full-screen #header,:-webkit-full-screen #status{display:none!important}
:-webkit-full-screen #player-container{height:100vh}
:-webkit-full-screen #controls{padding-bottom:24px}
</style>
<script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
</head>
<body>
<div id="header">
    <div id="logo">AnimeMew Cast</div>
    <div id="status" class="connecting"><div class="dot"></div><span class="text">Conectando...</span></div>
</div>
<div id="player-container">
    <video id="player" playsinline></video>
    <div id="loading"><div class="spinner"></div><div class="text">Cargando video...</div></div>
    <div id="error"></div>
    <div id="stopped"><div class="icon">&#9209;</div><div class="text">Cast detenido desde la app</div></div>
</div>
<div id="controls" class="hidden">
    <div id="progress-bar"><div id="progress-track"><div id="progress-buffer"></div><div id="progress-fill"></div><div id="progress-thumb"></div></div></div>
    <div id="time-display"><span id="current-time">00:00</span><span id="duration">00:00</span></div>
    <div id="buttons">
        <button id="rewind" title="-10s (←)">&#9194;</button>
        <button id="play-pause" title="Play/Pause (Espacio)">&#9654;</button>
        <button id="skip-op" title="+10s (→)">&#9193;</button>
        <button id="fullscreen" title="Pantalla completa (F)">&#9974;</button>
    </div>
</div>
<script>
const player=document.getElementById('player'),header=document.getElementById('header'),status=document.getElementById('status'),statusText=status.querySelector('.text'),loading=document.getElementById('loading'),errorDiv=document.getElementById('error'),stoppedDiv=document.getElementById('stopped'),controls=document.getElementById('controls'),progressBar=document.getElementById('progress-bar'),progressFill=document.getElementById('progress-fill'),progressBuffer=document.getElementById('progress-buffer'),progressThumb=document.getElementById('progress-thumb'),currentTimeEl=document.getElementById('current-time'),durationEl=document.getElementById('duration'),playPauseBtn=document.getElementById('play-pause'),fullscreenBtn=document.getElementById('fullscreen');
let hls=null,connected=false,hasVideo=false,hideTimer=null,isDragging=false;
function fmt(s){if(!s||isNaN(s))return'00:00';const h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sec=Math.floor(s%60);if(h>0)return h+':'+String(m).padStart(2,'0')+':'+String(sec).padStart(2,'0');return String(m).padStart(2,'0')+':'+String(sec).padStart(2,'0');}
function showControls(){controls.classList.remove('hidden');clearTimeout(hideTimer);if(player&&!player.paused&&hasVideo){hideTimer=setTimeout(hideControls,3000);}}
function hideControls(){if(player&&!player.paused){controls.classList.add('hidden');}}
// Listeners en document Y en player-container para que funcionen también en fullscreen
document.addEventListener('mousemove',showControls);
document.addEventListener('touchstart',showControls,{passive:true});
var playerContainer=document.getElementById('player-container');
playerContainer.addEventListener('mousemove',showControls);
playerContainer.addEventListener('click',showControls);
player.addEventListener('play',showControls);
player.addEventListener('pause',showControls);
async function poll(){try{const r=await fetch('/command');const d=await r.json();if(!connected){connected=true;status.className='connected';statusText.textContent='Conectado';errorDiv.style.display='none';}
if(d.type==='play'){playVideo(d.url,d.isHls,d.startPosition||0);}
else if(d.type==='pause'){player.pause();}
else if(d.type==='resume'){player.play().catch(function(){});}
else if(d.type==='seek'){var pos=parseFloat(d.position);if(!isNaN(pos)&&pos>=0){try{player.currentTime=pos;console.log('[Cast] Seek a',pos,'s');}catch(e){console.error('[Cast] Seek error',e);}}}
else if(d.type==='stop'){player.pause();hasVideo=false;loading.style.display='none';controls.classList.add('hidden');stoppedDiv.style.display='block';statusText.textContent='Detenido';}
}catch(e){if(connected){connected=false;status.className='connecting';statusText.textContent='Reconectando...';}}}
setInterval(poll,1000);poll();
function playVideo(url,isHls,startPos){loading.style.display='block';errorDiv.style.display='none';stoppedDiv.style.display='none';if(hls){hls.destroy();hls=null;}player.removeAttribute('src');player.load();
if(isHls&&window.Hls&&Hls.isSupported()){hls=new Hls();hls.loadSource(url);hls.attachMedia(player);hls.on(Hls.Events.MANIFEST_PARSED,function(){if(startPos>0)player.currentTime=startPos;player.play().catch(function(){});loading.style.display='none';hasVideo=true;showControls();});hls.on(Hls.Events.ERROR,function(e,d){if(d.fatal){loading.style.display='none';errorDiv.style.display='block';errorDiv.textContent='Error al cargar el video.';}});
}else{player.src=url;player.addEventListener('loadedmetadata',function(){if(startPos>0)player.currentTime=startPos;player.play().catch(function(){});loading.style.display='none';hasVideo=true;showControls();},{once:true});player.addEventListener('error',function(){loading.style.display='none';errorDiv.style.display='block';errorDiv.textContent='No se pudo cargar el video.';},{once:true});}}
player.addEventListener('timeupdate',function(){if(player.duration>0){var pct=player.currentTime/player.duration*100;progressFill.style.width=pct+'%';progressThumb.style.left=pct+'%';currentTimeEl.textContent=fmt(player.currentTime);}});
player.addEventListener('loadedmetadata',function(){durationEl.textContent=fmt(player.duration);});
player.addEventListener('progress',function(){if(player.buffered.length>0&&player.duration>0)progressBuffer.style.width=(player.buffered.end(player.buffered.length-1)/player.duration*100)+'%';});
player.addEventListener('play',function(){playPauseBtn.innerHTML='&#10074;&#10074;';});
player.addEventListener('pause',function(){playPauseBtn.innerHTML='&#9654;';});
player.addEventListener('ended',function(){fetch('/ended').catch(function(){});});
setInterval(function(){if(player.duration>0){fetch('/position',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({currentTime:player.currentTime,duration:player.duration,isPlaying:!player.paused})}).catch(function(){});}},3000);
playPauseBtn.addEventListener('click',function(){if(player.paused)player.play().catch(function(){});else player.pause();});
document.getElementById('rewind').addEventListener('click',function(){player.currentTime=Math.max(0,player.currentTime-10);});
document.getElementById('skip-op').addEventListener('click',function(){player.currentTime=Math.min(player.duration||9999,player.currentTime+10);});
fullscreenBtn.addEventListener('click',function(){var c=document.getElementById('player-container');if(document.fullscreenElement)document.exitFullscreen();else if(document.webkitFullscreenElement)document.webkitExitFullscreen();else{if(c.requestFullscreen)c.requestFullscreen();else if(c.webkitRequestFullscreen)c.webkitRequestFullscreen();}});
function seekToPos(clientX){if(!player.duration)return;var r=progressBar.getBoundingClientRect();var f=Math.max(0,Math.min(1,(clientX-r.left)/r.width));player.currentTime=player.duration*f;var pct=f*100;progressFill.style.width=pct+'%';progressThumb.style.left=pct+'%';}
progressBar.addEventListener('mousedown',function(e){isDragging=true;progressBar.classList.add('dragging');seekToPos(e.clientX);});
document.addEventListener('mousemove',function(e){if(isDragging)seekToPos(e.clientX);});
document.addEventListener('mouseup',function(){if(isDragging){isDragging=false;progressBar.classList.remove('dragging');}});
progressBar.addEventListener('touchstart',function(e){isDragging=true;progressBar.classList.add('dragging');seekToPos(e.touches[0].clientX);},{passive:true});
document.addEventListener('touchmove',function(e){if(isDragging)seekToPos(e.touches[0].clientX);},{passive:true});
document.addEventListener('touchend',function(){if(isDragging){isDragging=false;progressBar.classList.remove('dragging');}});
// ── Atajos de teclado ──
document.addEventListener('keydown',function(e){
  // Ignorar si el foco está en un input
  if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA')return;
  switch(e.code){
    case'Space':
      e.preventDefault();
      if(player.paused)player.play().catch(function(){});else player.pause();
      showControls();
      break;
    case'KeyF':
      e.preventDefault();
      fullscreenBtn.click();
      showControls();
      break;
    case'ArrowLeft':
      e.preventDefault();
      player.currentTime=Math.max(0,player.currentTime-10);
      showControls();
      break;
    case'ArrowRight':
      e.preventDefault();
      player.currentTime=Math.min(player.duration||9999,player.currentTime+10);
      showControls();
      break;
    case'ArrowUp':
      e.preventDefault();
      player.volume=Math.min(1,player.volume+0.1);
      showControls();
      break;
    case'ArrowDown':
      e.preventDefault();
      player.volume=Math.max(0,player.volume-0.1);
      showControls();
      break;
  }
});
</script>
</body>
</html>
""".trimIndent()
}
