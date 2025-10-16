package com.chat.share

import java.io.*
import java.nio.charset.StandardCharsets

/*
 * 패킷의 종류를 정의하는 상수
 * Header의 두 번째 4 Byte 필드에 사용된다.
 */
object PacketType {
    const val REGISTER_NAME = 1     // 이름 등록 요청/응답
    const val CHAT_MESSAGE = 2      // 일반 채팅 메시지
    const val SERVER_INFO = 3       // 서버 공지
    const val DISCONNECT_INFO = 4   // 접속 종료 시 통계 정보
    const val DISCONNECT_REQUEST = 5
}

/*
 * 수신된 패킷을 나타내는 데이터 클래스
 * Header : [length (4B) [type (4B)]
 * Body : [body (가변)]
 */
data class Packet (
    val length: Int,
    val type: Int,
    val body: ByteArray
) {
    fun getBodyAsString(): String {
        return String(body, StandardCharsets.UTF_8)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (length != other.length) return false
        if (type != other.type) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + type
        result = 31 * result + body.contentHashCode()
        return result
    }
}

/*
 * 패킷 종류와 문자열 데이터를 바이트 배열로 직렬화한다.
 * @return 전송 가능한 ByteArray ([Header] + [Body])
 */
fun createPacket(type: Int, bodyData: String): ByteArray {
    val bodyBytes = bodyData.toByteArray(StandardCharsets.UTF_8)
    val bodyLength = bodyBytes.size

    // 메모리 상 패킷 구성
    val byteArrayOutputStream = ByteArrayOutputStream()
    // int를 4 byte Big Endian(네트워크 순서)으로 변환해준다.
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)

    // Header 구성 (8 bytes)
    dataOutputStream.writeInt(bodyLength)
    dataOutputStream.writeInt(type)

    // Body 구성
    dataOutputStream.write(bodyBytes)

    return byteArrayOutputStream.toByteArray()
}

/*
 * InputStream으로부터 정확히 8 byte 헤더를 읽고 Packet 객체를 생성한다.
 * Blocking Socket에서는 DataInputStream.readInt()가 정확히 4 byte를 읽을 때까지 대기한다.
 * @throws IOException 연결이 끊어졌을 경우 발생
 */
fun readPacket(inputStream: InputStream): Packet {
    val dataInputStream = DataInputStream(inputStream)

    // 1. Header 읽기 : Length (4 bytes)
    val length = try {
        dataInputStream.readInt()
    } catch (e: Exception) {
        throw IOException("Connection closed by peer (EOF).")
    }

    // 2. Header 읽기 : Type (4 bytes)
    val type = dataInputStream.readInt()


    // 3. Body 읽기 : Length 필드의 길이만큼 정확히 읽는다.
    val bodyBytes = ByteArray(length)
    dataInputStream.readFully(bodyBytes)

    return Packet(length, type, bodyBytes)
}