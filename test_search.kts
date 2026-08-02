import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

fun main() {
    val q = "shingeki-no-kyojin"
    val url = URL("https://jkanime.net/buscar/$q/")
    val conn = url.openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = false
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    println("Response Code: ${conn.responseCode}")
    println("Location: ${conn.getHeaderField("Location")}")
    
    val html = if (conn.responseCode in 200..299) {
        val scanner = Scanner(conn.inputStream).useDelimiter("\\A")
        if (scanner.hasNext()) scanner.next() else ""
    } else {
        ""
    }
    println("HTML Length: ${html.length}")
    if (html.length > 500) {
        println(html.substring(0, 500))
    }
}
