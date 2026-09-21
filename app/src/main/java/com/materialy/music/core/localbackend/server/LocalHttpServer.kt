package com.materialy.music.core.localbackend.server

import com.materialy.music.core.localbackend.downloader.LocalDownloadEngine
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.download.DownloadRequest
import com.materialy.music.data.download.DownloadResponse
import com.materialy.music.data.download.SearchResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHttpServer @Inject constructor(
    private val extractor: InnertubeExtractor,
    private val downloadEngine: LocalDownloadEngine
) {
    private var serverSocket: ServerSocket? = null
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var isRunning = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val port = 8080
    val baseUrl = "http://127.0.0.1:$port"

    /**
     * Start the on-device embedded HTTP server on 127.0.0.1:8080
     */
    @Synchronized
    fun start() {
        if (isRunning && serverSocket != null) return

        try {
            val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            server.reuseAddress = true
            serverSocket = server
            isRunning = true

            acceptJob = serverScope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        launch {
                            handleClientSocket(clientSocket)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            println("LocalHttpServer started on $baseUrl")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stop the embedded server
     */
    @Synchronized
    fun stop() {
        try {
            isRunning = false
            acceptJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            println("LocalHttpServer stopped")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isServerRunning(): Boolean = isRunning

    private suspend fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val output = BufferedOutputStream(client.getOutputStream())

                // 1. Read Request Line: GET /path?query HTTP/1.1
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]

                // 2. Read Headers
                val headers = mutableMapOf<String, String>()
                var headerLine: String?
                while (reader.readLine().also { headerLine = it } != null) {
                    if (headerLine.isNullOrBlank()) break
                    val colonIdx = headerLine!!.indexOf(":")
                    if (colonIdx > 0) {
                        val key = headerLine!!.substring(0, colonIdx).trim().lowercase()
                        val value = headerLine!!.substring(colonIdx + 1).trim()
                        headers[key] = value
                    }
                }

                // Handle CORS Preflight
                if (method == "OPTIONS") {
                    sendCorsResponse(output)
                    return
                }

                // 3. Read Body (if POST)
                var bodyText = ""
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val bodyBuffer = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(bodyBuffer, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    bodyText = String(bodyBuffer, 0, readTotal)
                }

                val pathOnly = fullPath.substringBefore("?")
                val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""
                val queryParams = parseQueryParams(queryString)

                // 4. Route Requests
                when {
                    pathOnly == "/health" -> {
                        val res = """{"status":"ok","mode":"on_device","jobs":0,"yt_dlp":"embedded-innertube"}"""
                        sendJsonResponse(output, 200, res)
                    }

                    pathOnly == "/search" -> {
                        val query = queryParams["q"] ?: ""
                        val limit = queryParams["limit"]?.toIntOrNull() ?: 12
                        try {
                            val results = extractor.search(query, limit)
                            val resp = SearchResponse(query = query, results = results)
                            sendJsonResponse(output, 200, json.encodeToString(resp))
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, """{"error":"${escapeJson(e.message ?: "Ошибка поиска")}"}""")
                        }
                    }

                    pathOnly == "/info" -> {
                        val url = queryParams["url"] ?: ""
                        try {
                            val info = extractor.getInfo(url)
                            sendJsonResponse(output, 200, json.encodeToString(info))
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, """{"error":"${escapeJson(e.message ?: "Ошибка извлечения данных")}"}""")
                        }
                    }

                    pathOnly == "/resolve_online" -> {
                        val url = queryParams["url"] ?: ""
                        try {
                            val resolved = extractor.resolveOnline(url)
                            sendJsonResponse(output, 200, json.encodeToString(resolved))
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, """{"error":"${escapeJson(e.message ?: "Ошибка резолвинга")}"}""")
                        }
                    }

                    pathOnly == "/download" && method == "POST" -> {
                        try {
                            val req = json.decodeFromString<DownloadRequest>(bodyText)
                            val jobId = downloadEngine.startDownload(req)
                            val res = DownloadResponse(
                                jobId = jobId,
                                status = "queued",
                                message = "Скачивание начато на устройстве"
                            )
                            sendJsonResponse(output, 200, json.encodeToString(res))
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, """{"error":"${escapeJson(e.message ?: "Ошибка создания задачи")}"}""")
                        }
                    }

                    pathOnly.startsWith("/status") -> {
                        val jobId = pathOnly.removePrefix("/status/").removePrefix("/status").trim()
                        val status = downloadEngine.getStatus(jobId)
                        sendJsonResponse(output, 200, json.encodeToString(status))
                    }

                    pathOnly.startsWith("/file") -> {
                        val jobId = pathOnly.removePrefix("/file/").removePrefix("/file").trim()
                        val file = downloadEngine.getDownloadedFile(jobId)
                        if (file != null && file.exists()) {
                            sendFileResponse(output, file)
                        } else {
                            sendJsonResponse(output, 404, """{"error":"Файл не найден"}""")
                        }
                    }

                    pathOnly == "/stream" -> {
                        val url = queryParams["url"] ?: ""
                        try {
                            val directUrl = extractor.resolveDirectStreamUrl(url)
                            sendRedirectResponse(output, directUrl)
                        } catch (e: Exception) {
                            sendJsonResponse(output, 500, """{"error":"${escapeJson(e.message ?: "Ошибка получения стрима")}"}""")
                        }
                    }

                    else -> {
                        sendJsonResponse(output, 404, """{"error":"Эндпоинт $pathOnly не найден"}""")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun sendJsonResponse(output: BufferedOutputStream, statusCode: Int, jsonString: String) {
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        val statusMsg = if (statusCode == 200) "OK" else if (statusCode == 404) "Not Found" else "Internal Server Error"
        val header = "HTTP/1.1 $statusCode $statusMsg\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCorsResponse(output: BufferedOutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendRedirectResponse(output: BufferedOutputStream, location: String) {
        val header = "HTTP/1.1 302 Found\r\n" +
                "Location: $location\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private fun sendFileResponse(output: BufferedOutputStream, file: File) {
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))
        val fileInputStream = FileInputStream(file)
        val buffer = ByteArray(32 * 1024)
        var bytesRead: Int
        while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        fileInputStream.close()
        output.flush()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                try {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    result[key] = value
                } catch (_: Exception) {}
            }
        }
        return result
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
