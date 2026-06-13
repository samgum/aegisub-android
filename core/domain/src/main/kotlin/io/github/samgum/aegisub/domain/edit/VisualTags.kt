package io.github.samgum.aegisub.domain.edit

/**
 * 可视化打字覆盖标签操作（复刻桌面 Aegisub Visual Typesetting 对 {\pos}/{\fr} 的注入）。
 *
 * 标签可在 `{...}` 覆盖块内成组出现，故匹配 `\pos(...)` / `\fr<deg>`（不带花括号），
 * 替换时仅改参数、保留同组其余标签；缺失时插入：若已有前导覆盖块则并入其首，
 * 否则新建前导覆盖块。
 *
 * 注意：替换用 `replace(Regex) { ... }` lambda 重载——字符串重载会把 `\p` 当转义吞掉。
 *
 * 纯函数：不改原串。通过 `session.editEvent` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object VisualTags {

    // 匹配 \pos(x,y)（允许小数/负数/空格），不带花括号，故能在标签组内替换。
    private val POS_ARG_RE = Regex("""\\pos\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\)""")
    // 匹配 \fr<deg>，但不匹配 \frx / \fry（负向先行断言）。
    private val FR_ARG_RE = Regex("""\\fr(?![xy])-?\d+(?:\.\d+)?""")
    private val NUM_RE = Regex("-?\\d+(?:\\.\\d+)?")

    // ---------------- \pos ----------------

    /** 解析 {\pos(x,y)}；不存在返回 null。 */
    fun getPos(text: String): Pair<Int, Int>? {
        val match = POS_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).toList()
        if (nums.size < 2) return null
        return nums[0].value.toInt() to nums[1].value.toInt()
    }

    /** 设置 {\pos(x,y)}：已存在则替换参数，否则插入（并入前导覆盖块或新建）。 */
    fun setPos(text: String, x: Int, y: Int): String {
        val tagInner = "\\pos($x,$y)"
        return if (POS_ARG_RE.containsMatchIn(text)) text.replace(POS_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    /** 移除所有 \pos(...)；其余标签保留。 */
    fun removePos(text: String): String = text.replace(POS_ARG_RE) { "" }

    // ---------------- \fr ----------------

    /** 解析 {\fr<deg>}；不存在返回 0。 */
    fun getRotation(text: String): Int =
        FR_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toInt() } ?: 0

    /** 设置 {\fr<deg>}：已存在则替换，否则插入。\frx/\fry 不受影响。 */
    fun setRotation(text: String, degrees: Int): String {
        val tagInner = "\\fr$degrees"
        return if (FR_ARG_RE.containsMatchIn(text)) text.replace(FR_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    /** 移除所有 \fr<deg>（不动 \frx/\fry）。 */
    fun removeRotation(text: String): String = text.replace(FR_ARG_RE) { "" }

    // ---------------- 工具 ----------------

    /**
     * 把标签（不含花括号，如 `\pos(1,2)`）插入文本：若已有前导 `{...}` 块则并入其首，
     * 否则在开头新建 `{tag}`。
     */
    private fun insertTag(text: String, tagInner: String): String {
        val close = if (text.startsWith("{")) text.indexOf("}") else -1
        return if (close > 0) {
            val existingInner = text.substring(1, close)
            "{" + tagInner + existingInner + "}" + text.substring(close + 1)
        } else {
            "{" + tagInner + "}" + text
        }
    }
}
