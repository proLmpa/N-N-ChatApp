package com.chat.server

import java.net.ServerSocket
import java.util.UUID

fun main() {
    val port = 8080

    // 서버 소켓 준비
    val serverSocket = ServerSocket(port)

    println("Chat Server started on port $port")

    try {
        while (true) {
            val clientSocket = serverSocket.accept()
            val clientId = UUID.randomUUID().toString()
            println("New client connected from ${clientSocket.inetAddress.hostAddress}. ID: $clientId")

            ClientHandler(clientSocket, clientId).start()
        }
    } catch (e: Exception) {
        println("Server loop error: ${e.message}")
    } finally {
        serverSocket.close()
    }
}