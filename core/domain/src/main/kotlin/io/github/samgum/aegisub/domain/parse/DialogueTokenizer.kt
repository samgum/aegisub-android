// 伤感咩吖
package io.github.samgum.aegisub.domain.parse

import io.github.samgum.aegisub.domain.parse.DialogueTokenType as TT

/**
 * 对话体 tokenizer：产出 token 流，驱动语法高亮与结构分析。
 * 参考自 libaegisub dialogue_parser.cpp。
 */
object DialogueTokenizer {

    /**
     * 将 ASS 对话体文本解析为 token 列表。
     * 纯文本、覆盖块（{}）、标签（\tagname）、括号参数、绘图指令等均被拆分为独立 token。
     * 所有 token 的 length 之和严格等于输入字符串长度（无间隙、无重叠）。
     */
    fun tokenize(s: String): List<DialogueToken> {
        if (s.isEmpty()) return emptyList()
        val out = mutableListOf<DialogueToken>()
        var i = 0
        var textStart = -1

        fun flushText(end: Int) {
            if (textStart in 0 until end) out += DialogueToken(TT.TEXT, end - textStart)
            textStart = -1
        }

        while (i < s.length) {
            if (s[i] == '{') {
                flushText(i)
                out += DialogueToken(TT.OVR_BEGIN, 1)
                i = parseOverride(s, i + 1, out)
            } else {
                if (textStart < 0) textStart = i
                i++
            }
        }
        flushText(s.length)
        return out
    }

    /**
     * 解析覆盖块内部内容（'{' 之后直到 '}' 的部分）。
     * 返回 '}' 之后的下标。
     */
    private fun parseOverride(s: String, start: Int, out: MutableList<DialogueToken>): Int {
        var i = start
        while (i < s.length) {
            val c = s[i]
            when {
                c == '}' -> {
                    out += DialogueToken(TT.OVR_END, 1)
                    return i + 1
                }
                c == '\\' -> {
                    out += DialogueToken(TT.TAG_START, 1)
                    i++
                    // 消费标签名（字母序列）
                    val nameStart = i
                    while (i < s.length && s[i].isLetter()) i++
                    if (i > nameStart) out += DialogueToken(TT.TAG_NAME, i - nameStart)
                    // 括号参数形式，如 \pos(100,200)
                    if (i < s.length && s[i] == '(') {
                        out += DialogueToken(TT.OPEN_PAREN, 1)
                        i++
                        // 注意：必须同时排除 ')' —— 否则 consumeArg 停在 ')' 不推进，
                        // 外层 while 会无限重复调用 consumeArg，导致死循环（\pos/\move/\clip 等）。
                        while (i < s.length && s[i] != '}' && s[i] != '\\' && s[i] != ')') {
                            i = consumeArg(s, i, out)
                        }
                        if (i < s.length && s[i] == ')') {
                            out += DialogueToken(TT.CLOSE_PAREN, 1)
                            i++
                        }
                    } else {
                        // 无括号附加值（如 \fnArial、\i1、\c&H..&）
                        val a0 = i
                        while (i < s.length && s[i] != '\\' && s[i] != '}') i++
                        if (i > a0) out += DialogueToken(TT.ARG, i - a0)
                    }
                }
                c.isWhitespace() -> {
                    val w0 = i
                    while (i < s.length && s[i].isWhitespace()) i++
                    out += DialogueToken(TT.WHITESPACE, i - w0)
                }
                else -> {
                    out += DialogueToken(TT.ARG, 1)
                    i++
                }
            }
        }
        // 未闭合的覆盖块 —— 不会有 OVR_END，但保证长度守恒
        return i
    }

    /**
     * 消费单个括号参数值，遇到 ',' 或 ')' 或 '\\' 时停止。
     * 如果遇到 ',' 则额外输出一个 ARG_SEP token。
     */
    private fun consumeArg(s: String, start: Int, out: MutableList<DialogueToken>): Int {
        var i = start
        val a0 = i
        while (i < s.length) {
            val c = s[i]
            if (c == ',' || c == ')' || c == '\\') break
            i++
        }
        if (i > a0) out += DialogueToken(TT.ARG, i - a0)
        if (i < s.length && s[i] == ',') {
            out += DialogueToken(TT.ARG_SEP, 1)
            i++
        }
        return i
    }

    /**
     * 标记绘图区域：将 \p（scale>0）之后的 TEXT token 替换为 DRAWING_FULL，
     * 直到遇到 \p0（scale 归零）。
     *
     * @param s   原始对话体文本，用于读取 \p 标签的实际参数值
     * @param tokens 已解析的 token 列表
     * @return 新的 token 列表，绘图区域的 TEXT 已被替换为 DRAWING_FULL
     */
    fun markDrawings(s: String, tokens: List<DialogueToken>): List<DialogueToken> {
        val result = tokens.toMutableList()
        var inDrawing = false
        var pos = 0
        var i = 0
        while (i < result.size) {
            val t = result[i]
            // 检测 \p 标签：TAG_NAME 长度为 1 且在原始字符串中对应 "p"
            if (t.type == TT.TAG_NAME && t.length == 1 && s.regionMatches(pos, "p", 0, 1)) {
                if (i + 1 < result.size && result[i + 1].type == TT.ARG) {
                    val argStart = pos + t.length
                    val argText = s.substring(argStart, argStart + result[i + 1].length)
                    val scale = argText.trimStart('0').firstOrNull()?.digitToIntOrNull() ?: 0
                    inDrawing = scale > 0
                }
            }
            if (t.type == TT.TEXT && inDrawing) {
                result[i] = DialogueToken(TT.DRAWING_FULL, t.length)
            }
            pos += t.length
            i++
        }
        return result
    }
}
