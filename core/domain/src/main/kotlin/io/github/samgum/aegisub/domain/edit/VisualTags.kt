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
    // 匹配 \move(...) 与 \fad(...)（无嵌套括号）
    private val MOVE_ARG_RE = Regex("""\\move\([^)]*\)""")
    private val FAD_ARG_RE = Regex("""\\fad\([^)]*\)""")
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

    // ---------------- \move（两点动画）----------------

    /** {\move(x1,y1,x2,y2[,t1,t2])} 解析；t1/t2 缺省为 0。不存在返回 null。 */
    fun getMove(text: String): MoveParams? {
        val match = MOVE_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toInt() }.toList()
        if (nums.size < 4) return null
        return MoveParams(
            x1 = nums[0], y1 = nums[1], x2 = nums[2], y2 = nums[3],
            t1 = nums.getOrElse(4) { 0 }, t2 = nums.getOrElse(5) { 0 },
        )
    }

    /**
     * 设置 {\move}：t1=t2=0 时用 4 参数形式（整段时长移动），否则 6 参数（窗口移动）。
     * 已存在则替换，否则插入。
     */
    fun setMove(text: String, x1: Int, y1: Int, x2: Int, y2: Int, t1: Int = 0, t2: Int = 0): String {
        val tagInner = if (t1 == 0 && t2 == 0) "\\move($x1,$y1,$x2,$y2)"
        else "\\move($x1,$y1,$x2,$y2,$t1,$t2)"
        return if (MOVE_ARG_RE.containsMatchIn(text)) text.replace(MOVE_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    /** 移除所有 \move(...)。 */
    fun removeMove(text: String): String = text.replace(MOVE_ARG_RE) { "" }

    // ---------------- \fad（淡入淡出）----------------

    /** {\fad(fadeIn,fadeOut)} 解析；不存在返回 null。单位毫秒。 */
    fun getFade(text: String): FadeParams? {
        val match = FAD_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toInt() }.toList()
        if (nums.size < 2) return null
        return FadeParams(fadeIn = nums[0], fadeOut = nums[1])
    }

    /** 设置 {\fad(fadeIn,fadeOut)}（毫秒）。已存在则替换，否则插入。 */
    fun setFade(text: String, fadeIn: Int, fadeOut: Int): String {
        val tagInner = "\\fad($fadeIn,$fadeOut)"
        return if (FAD_ARG_RE.containsMatchIn(text)) text.replace(FAD_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    /** 移除所有 \fad(...)。 */
    fun removeFade(text: String): String = text.replace(FAD_ARG_RE) { "" }

    // ---------------- 数据类 ----------------

    /** {\move} 参数。 */
    data class MoveParams(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val t1: Int = 0, val t2: Int = 0)

    /** {\fad} 参数（毫秒）。 */
    data class FadeParams(val fadeIn: Int, val fadeOut: Int)

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
