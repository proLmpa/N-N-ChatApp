package com.chat.server

import com.chat.share.PacketType
import com.chat.share.createPacket
import com.chat.share.readPacket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


// 클라이언트 연결 정보를 저장하는 데이터 클래스
data class ClientData (
    val id: String,
    var name: String? = null,
    val sentCount: AtomicInteger = AtomicInteger(0),
    val receivedCount: AtomicInteger = AtomicInteger(0),
)

// 서버의 모든 클라이언트 정보를 담는 공유 자원 (Map)
val clients = mutableMapOf<String, ClientHandler>()

// 공유자원에 대한 접근을 동기화하기 위한 Lock
val clientMapLock = ReentrantLock()

class ClientHandler(private val clientSocket: Socket, private val clientId: String) : Thread() {
    private val clientData = ClientData(clientId)
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    // 특정 클라이언트의 출력 스트림 접근을 동기화하기 위한 Lock
    private val clientOutputLock = ReentrantLock()

    // 1. 패킷 전송
    fun sendPacket(packetBytes: ByteArray) {
        clientOutputLock.withLock {
            try {
                if (!::outputStream.isInitialized) return

                outputStream.write(packetBytes)
                outputStream.flush()

                // 메시지가 성공적으로 전송됨에 따라 클라이언트의 받은 메시지 수 증가
                clientData.receivedCount.incrementAndGet()

            } catch (e: IOException) {
                println("Error: Failed to send packet to ${clientData.name ?: clientId}. Disconnecting...")
                clientSocket.close()
            }
        }
    }

    // 2. 전체 브로드캐스트 (Lock 사용)
    private fun broadcast(packetBytes: ByteArray, senderId: String? = null) {
        clientMapLock.withLock {
            clients.values.forEach { handler ->
                if (handler.clientData.id != senderId) {
                    handler.sendPacket(packetBytes)
                }
            }
        }
    }

    // 3. 메인 로직
    override fun run() = try {
        inputStream = clientSocket.getInputStream()
        outputStream = clientSocket.getOutputStream()

        // 1. 이름 등록 대기
        handleNameRegistration()

        // 2. 메시지 수신 루프
        listenForMessages()
    } catch (e: Exception) {
        val clientNameOrId = clientData.name ?: clientId
        println("Client $clientNameOrId disconnected.")
    } finally {
        // 3. 접속 종료 처리
        if (!clientSocket.isClosed) {
            handleClientDisconnection()
        }
    }

    // 클라이언트로부터 이름 등록 패킷을 처리하고 ClientInfo를 초기화한다.
    private fun handleNameRegistration() {
        // 맵에 클라이언트 핸들러 추가 (Lock  사용) -> 이 작업은 run() 시작 직후 수행되어야 한다.
        clientMapLock.withLock {
            clients[clientId] = this
        }

        val registerPacket = try {
            readPacket(inputStream)
        } catch (e: IOException) {
            // 이름 등록 전에 연결이 끊긴 경우
            clientSocket.close()
            clientMapLock.withLock { clients.remove(clientId) }
            throw e
        }

        if (registerPacket.type != PacketType.REGISTER_NAME) {
            sendPacket(createPacket(PacketType.SERVER_INFO, "Server: Enter your name first."))
            clientSocket.close()
            throw IOException("Initial packet was not REGISTER_NAME.")
        }

        val clientName = registerPacket.getBodyAsString().trim()
        if (clientName.isEmpty()) {
            sendPacket(createPacket(PacketType.SERVER_INFO, "Server: You must enter a name."))
            clientSocket.close()
            throw IOException("Name not registered.")
        }

        clientData.name = clientName

        val welcomeMsg = "Server: `${clientName}' Entered."
        broadcast(createPacket(PacketType.SERVER_INFO, welcomeMsg), clientData.id)
        println(welcomeMsg)
    }

    // 이름 등록 후, 클라이언트로부터 CHAT_MESSAGE를 계속 수신한다.
    private fun listenForMessages() {
        while (clientSocket.isConnected && !clientSocket.isInputShutdown) {
            val packet = readPacket(inputStream)

            when (packet.type) {
                PacketType.CHAT_MESSAGE -> {
                    val message = packet.getBodyAsString()
                    val chatMsg = "[${clientData.name}]: $message"

                    clientData.sentCount.incrementAndGet()

                    broadcast(createPacket(PacketType.CHAT_MESSAGE, chatMsg), clientData.id)
                    println("Chat from ${clientData.name}: $message")

                }
                PacketType.DISCONNECT_REQUEST -> {
                    println("Client ${clientData.name} sent DISCONNECT_REQUEST.")
                    return
                }
            }
        }
    }

    // 클라이언트 접속 종료 시 맵에서 제거하고 통계 정보를 전송한다.
    private fun handleClientDisconnection() {
        val name = clientData.name ?: clientId
        val sent = clientData.sentCount.get()
        val received = clientData.receivedCount.get()

        clientMapLock.withLock {
            clients.remove(clientId)
        }

        val disconnectMsg = "Server: '$name' disconnected. (Sent Messages: $sent, Received Messages: $received)"

        broadcast(createPacket(PacketType.DISCONNECT_INFO, disconnectMsg))
        println(disconnectMsg)

        clientSocket.close()
    }
}