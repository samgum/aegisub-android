package io.github.samgum.aegisub.domain.model

import kotlin.math.min

/**
 * ASS 内嵌附件（完整复刻桌面 Aegisub `AssAttachment` 与 `libaegisub/ass/uuencode.cpp`）：
 * 支持 `[Fonts]`（`fontname:`）与 `[Graphics]`（`filename:`）段的非标准 ASS 6-bit +33 UUEncode 编解码与无损往返。
 *
 * @author 伤感咩吖
 */
enum class AttachmentGroup {
    FONT,
    GRAPHIC,
}

data class AssAttachment(
    val fileName: String,
    val group: AttachmentGroup,
    val encodedData: String,
) {
    /** 提取用于显示的真实文件名（去除 SSA `.ttf` 编码后缀 `_0`）。 */
    val displayFileName: String
        get() {
            if (group != AttachmentGroup.FONT || !fileName.endsWith(".ttf", ignoreCase = true)) return fileName
            val base = fileName.dropLast(4)
            val lastUnder = base.lastIndexOf('_')
            return if (lastUnder > 0) base.substring(0, lastUnder) + fileName.takeLast(4) else fileName
        }

    /** 估算解码后原始字节大小。 */
    val approximateByteSize: Int
        get() {
            val cleanChars = encodedData.count { it > ' ' }
            return (cleanChars * 3) / 4
        }

    fun decodeBytes(): ByteArray = AssUuCodec.decode(encodedData)

    fun toAssLines(): String = buildString {
        val prefix = if (group == AttachmentGroup.FONT) "fontname: " else "filename: "
        append(prefix).appendLine(fileName)
        append(encodedData.trimEnd('\r', '\n'))
    }

    companion object {
        fun fromBytes(rawFileName: String, group: AttachmentGroup, bytes: ByteArray): AssAttachment {
            val storedName = if (group == AttachmentGroup.FONT &&
                rawFileName.endsWith(".ttf", ignoreCase = true) &&
                !rawFileName.dropLast(4).contains('_')
            ) {
                rawFileName.dropLast(4) + "_0" + rawFileName.takeLast(4)
            } else {
                rawFileName
            }
            return AssAttachment(
                fileName = storedName,
                group = group,
                encodedData = AssUuCodec.encode(bytes),
            )
        }
    }
}

/**
 * 1:1 移植自桌面 Aegisub `libaegisub/ass/uuencode.cpp`：
 * 每 3 字节拆为 4 个 6-bit 值，每个值 `+ 33` 映射为可视 ASCII，每 80 字符换行。
 */
object AssUuCodec {

    fun encode(bytes: ByteArray, insertLineBreaks: Boolean = true): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder((bytes.size * 4 + 2) / 3 + bytes.size / 80 * 2)
        var written = 0
        var pos = 0
        while (pos < bytes.size) {
            val rem = bytes.size - pos
            val b0 = bytes[pos].toInt() and 0xFF
            val b1 = if (rem > 1) bytes[pos + 1].toInt() and 0xFF else 0
            val b2 = if (rem > 2) bytes[pos + 2].toInt() and 0xFF else 0

            val dst0 = b0 ushr 2
            val dst1 = ((b0 and 0x03) shl 4) or ((b1 and 0xF0) ushr 4)
            val dst2 = ((b1 and 0x0F) shl 2) or ((b2 and 0xC0) ushr 6)
            val dst3 = b2 and 0x3F

            val outCount = min(rem + 1, 4)
            val arr = intArrayOf(dst0, dst1, dst2, dst3)
            for (i in 0 until outCount) {
                sb.append((arr[i] + 33).toChar())
                written++
                if (insertLineBreaks && written == 80 && (pos + 3 < bytes.size || i + 1 < outCount)) {
                    written = 0
                    sb.append("\n")
                }
            }
            pos += 3
        }
        return sb.toString()
    }

    fun decode(encoded: String): ByteArray {
        if (encoded.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream((encoded.length * 3) / 4 + 4)
        var pos = 0
        val len = encoded.length
        val src = IntArray(4)
        while (pos + 1 < len) {
            var count = 0
            src.fill(0)
            while (count < 4 && pos < len) {
                val c = encoded[pos++]
                if (c > '\u0000' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                    src[count++] = (c.code - 33) and 0x3F
                }
            }
            if (count > 1) out.write(((src[0] shl 2) or (src[1] ushr 4)) and 0xFF)
            if (count > 2) out.write((((src[1] and 0x0F) shl 4) or (src[2] ushr 2)) and 0xFF)
            if (count > 3) out.write((((src[2] and 0x03) shl 6) or src[3]) and 0xFF)
        }
        return out.toByteArray()
    }
}
