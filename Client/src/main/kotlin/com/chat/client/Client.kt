package com.chat.client

import com.chat.share.PacketType
import com.chat.share.createPacket
import com.chat.share.readPacket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

fun main() {
    val host = "localhost"
    val port = 8080

    print("Enter your name: ")
    val name = readLine()

    if (name.isNullOrBlank()) {
        println("Name is required. Program terminated.")
        return
    }

    val clientSocket = try {
        Socket(host, port)
    } catch (e: IOException) {
        println("Error: Server connection failed. ${e.message}")
        return
    }

    println("'$name' entered. (type '/exit' to escape.")

    try {
        val outputStream = clientSocket.getOutputStream()
        val inputStream = clientSocket.getInputStream()

        // 1. 이름 등록 패킷 전송
        sendPacket(outputStream, PacketType.REGISTER_NAME, name)

        // 2. 서버 메시지 수신 전용 스레드 시작
        val receiveThread = thread(isDaemon = true) {
            receiveMessages(inputStream, clientSocket)
        }

        // 3. 메시지 송신 루프 (메인 스레드)
        sendMessageLoop(outputStream)

        receiveThread.join()
    } catch (e: Exception) {
        println("Client Error: ${e.message}")
    } finally {
        if (!clientSocket.isClosed) {
            clientSocket.close()
        }
        println("Chat disconnected.")
    }
}

// 서버로 패킷을 전송하는 유틸리티 함수
private fun sendPacket(outputStream: OutputStream, type: Int, data: String) {
    val packetBytes = createPacket(type, data)

    try {
        outputStream.write(packetBytes)
        outputStream.flush()
    } catch (e: IOException) {
        println("Error: Message could not be send. Server disconnected.")
    }
}

// 사용자 입력을 받아 서버에 메시지를 전송하는 루프
private fun sendMessageLoop(outputStream: OutputStream) {
    while(true) {
        val input = readlnOrNull() ?: continue

        if (input.equals("/exit", ignoreCase = true)) {
            break
        }

        if (input.isNotBlank()) {
            sendPacket(outputStream, PacketType.CHAT_MESSAGE, input)
        }
    }
}

// 서버로부터 메시지를 수신하는 별도의 thread 로직
private fun receiveMessages(inputStream: InputStream, clientSocket: Socket) {
    try {
        while (clientSocket.isConnected && !clientSocket.isInputShutdown) {
            val packet = readPacket(inputStream)
            val message = packet.getBodyAsString()

            when (packet.type) {
                PacketType.CHAT_MESSAGE -> println(message)
                PacketType.SERVER_INFO -> println("[INFO] $message")
                PacketType.DISCONNECT_INFO -> println("[LEAVE] $message")
                else -> println("[UNKNOWN] $message")
            }
        }
    } catch (e: IOException) {
        // 연결 끊김
    } catch (e: Exception) {
        println("Received thread - Error : ${e.message}")
    }
}