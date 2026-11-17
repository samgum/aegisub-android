package io.github.samgum.aegisub.domain.edit

import io.github.samgum.aegisub.domain.model.AssColor
import kotlin.math.roundToInt

/**
 * 可视化打字与行内覆盖标签操作（完整复刻桌面 Aegisub Visual Typesetting 全部 7 大工具与编辑框快捷栏）：
 * - `\pos(x,y)` 定位手柄
 * - `\move(x1,y1,x2,y2[,t1,t2])` 两点动画手柄
 * - `\fr` / `\frz` 平面 Z 轴旋转
 * - `\frx` / `\fry` + `\org(x,y)` 3D 透视旋转与旋转原点手柄
 * - `\fscx` / `\fscy` XY 缩放手柄
 * - `\fad(in,out)` 与 `\fade(a1,a2,a3,t1,t2,t3,t4)` 淡入淡出
 * - `\clip` / `\iclip` 矩形裁剪与矢量多边形裁剪 (`m x y l x y ...`)
 * - `\an1..9` 对齐、`\b/\i/\u/\s` 粗斜下划删除线、`\1c..\4c` 四色、`\fn`、`\fs`、`\bord`、`\shad`、`\blur`
 *
 * 纯函数：不改原串。通过 `session.editEvent` 接入，单撤销点。
 *
 * @author 伤感咩吖
 */
object VisualTags {

    private val POS_ARG_RE = Regex("""\\pos\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\)""")
    private val ORG_ARG_RE = Regex("""\\org\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\)""")
    private val FRZ_ARG_RE = Regex("""\\frz?(?![xy])-?\d+(?:\.\d+)?""")
    private val FRX_ARG_RE = Regex("""\\frx-?\d+(?:\.\d+)?""")
    private val FRY_ARG_RE = Regex("""\\fry-?\d+(?:\.\d+)?""")
    private val FSCX_ARG_RE = Regex("""\\fscx\d+(?:\.\d+)?""")
    private val FSCY_ARG_RE = Regex("""\\fscy\d+(?:\.\d+)?""")
    private val AN_ARG_RE = Regex("""\\an[1-9]""")
    private val MOVE_ARG_RE = Regex("""\\move\([^)]*\)""")
    private val FAD_ARG_RE = Regex("""\\fad\([^)]*\)""")
    private val FADE7_ARG_RE = Regex("""\\fade\([^)]*\)""")
    private val CLIP_RECT_RE = Regex("""\\i?clip\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\)""")
    private val CLIP_VEC_RE = Regex("""\\(i?clip)\(\s*(?:(\d+)\s*,\s*)?(m\s+[^)]+)\)""")
    private val NUM_RE = Regex("-?\\d+(?:\\.\\d+)?")

    // ---------------- 数据类 ----------------

    data class MoveParams(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val t1: Int = 0, val t2: Int = 0)
    data class FadeParams(val fadeIn: Int, val fadeOut: Int)
    data class ComplexFadeParams(
        val a1: Int, val a2: Int, val a3: Int,
        val t1: Int, val t2: Int, val t3: Int, val t4: Int,
    )
    data class ClipRect(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val inverse: Boolean = false)
    data class ScaleParams(val scaleX: Double = 100.0, val scaleY: Double = 100.0)
    data class Rotation3dParams(val frx: Double = 0.0, val fry: Double = 0.0, val frz: Double = 0.0)

    data class VecPoint(val x: Int, val y: Int)
    data class VectorClip(
        val points: List<VecPoint>,
        val inverse: Boolean = false,
        val scale: Int = 1,
    ) {
        fun toCommandString(): String {
            if (points.isEmpty()) return ""
            return buildString {
                append("m ${points.first().x} ${points.first().y}")
                if (points.size > 1) {
                    append(" l")
                    for (i in 1 until points.size) {
                        append(" ${points[i].x} ${points[i].y}")
                    }
                }
            }
        }
    }

    // ---------------- \pos ----------------

    fun getPos(text: String): Pair<Int, Int>? {
        val match = POS_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).toList()
        if (nums.size < 2) return null
        return nums[0].value.toDouble().roundToInt() to nums[1].value.toDouble().roundToInt()
    }

    fun setPos(text: String, x: Int, y: Int): String {
        val tagInner = "\\pos($x,$y)"
        return if (POS_ARG_RE.containsMatchIn(text)) text.replace(POS_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removePos(text: String): String = cleanEmptyBraces(text.replace(POS_ARG_RE) { "" })

    // ---------------- \org（3D 旋转原点）----------------

    fun getOrigin(text: String): Pair<Int, Int>? {
        val match = ORG_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).toList()
        if (nums.size < 2) return null
        return nums[0].value.toDouble().roundToInt() to nums[1].value.toDouble().roundToInt()
    }

    fun setOrigin(text: String, x: Int, y: Int): String {
        val tagInner = "\\org($x,$y)"
        return if (ORG_ARG_RE.containsMatchIn(text)) text.replace(ORG_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removeOrigin(text: String): String = cleanEmptyBraces(text.replace(ORG_ARG_RE) { "" })

    // ---------------- \fr / \frz / \frx / \fry ----------------

    fun getRotation(text: String): Int =
        FRZ_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull()?.roundToInt() } ?: 0

    fun setRotation(text: String, degrees: Int): String {
        val tagInner = "\\fr$degrees"
        return if (FRZ_ARG_RE.containsMatchIn(text)) text.replace(FRZ_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removeRotation(text: String): String = cleanEmptyBraces(text.replace(FRZ_ARG_RE) { "" })

    fun getRotation3d(text: String): Rotation3dParams {
        val frx = FRX_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull() } ?: 0.0
        val fry = FRY_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull() } ?: 0.0
        val frz = FRZ_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull() } ?: 0.0
        return Rotation3dParams(frx, fry, frz)
    }

    fun setRotation3d(text: String, frx: Int, fry: Int, frz: Int? = null): String {
        var cur = text
        val tx = "\\frx$frx"
        cur = if (FRX_ARG_RE.containsMatchIn(cur)) cur.replace(FRX_ARG_RE) { tx } else insertTag(cur, tx)
        val ty = "\\fry$fry"
        cur = if (FRY_ARG_RE.containsMatchIn(cur)) cur.replace(FRY_ARG_RE) { ty } else insertTag(cur, ty)
        if (frz != null) cur = setRotation(cur, frz)
        return cur
    }

    fun removeRotation3d(text: String): String =
        cleanEmptyBraces(text.replace(FRX_ARG_RE) { "" }.replace(FRY_ARG_RE) { "" }.replace(FRZ_ARG_RE) { "" })

    // ---------------- \fscx / \fscy（缩放）----------------

    fun getScale(text: String): ScaleParams {
        val sx = FSCX_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull() } ?: 100.0
        val sy = FSCY_ARG_RE.find(text)?.value?.let { NUM_RE.find(it)?.value?.toDoubleOrNull() } ?: 100.0
        return ScaleParams(sx, sy)
    }

    fun setScale(text: String, scaleX: Int, scaleY: Int): String {
        var cur = text
        val tx = "\\fscx${scaleX.coerceAtLeast(1)}"
        cur = if (FSCX_ARG_RE.containsMatchIn(cur)) cur.replace(FSCX_ARG_RE) { tx } else insertTag(cur, tx)
        val ty = "\\fscy${scaleY.coerceAtLeast(1)}"
        cur = if (FSCY_ARG_RE.containsMatchIn(cur)) cur.replace(FSCY_ARG_RE) { ty } else insertTag(cur, ty)
        return cur
    }

    fun removeScale(text: String): String =
        cleanEmptyBraces(text.replace(FSCX_ARG_RE) { "" }.replace(FSCY_ARG_RE) { "" })

    // ---------------- \an 对齐 ----------------

    fun getAlignment(text: String): Int? =
        AN_ARG_RE.find(text)?.value?.lastOrNull()?.digitToIntOrNull()

    fun setAlignment(text: String, an: Int): String {
        if (an !in 1..9) return text
        val tag = "\\an$an"
        return if (AN_ARG_RE.containsMatchIn(text)) text.replace(AN_ARG_RE) { tag } else insertTag(text, tag)
    }

    fun removeAlignment(text: String): String = cleanEmptyBraces(text.replace(AN_ARG_RE) { "" })

    // ---------------- \move ----------------

    fun getMove(text: String): MoveParams? {
        val match = MOVE_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toDouble().roundToInt() }.toList()
        if (nums.size < 4) return null
        return MoveParams(
            x1 = nums[0], y1 = nums[1], x2 = nums[2], y2 = nums[3],
            t1 = nums.getOrElse(4) { 0 }, t2 = nums.getOrElse(5) { 0 },
        )
    }

    fun setMove(text: String, x1: Int, y1: Int, x2: Int, y2: Int, t1: Int = 0, t2: Int = 0): String {
        val tagInner = if (t1 == 0 && t2 == 0) "\\move($x1,$y1,$x2,$y2)"
        else "\\move($x1,$y1,$x2,$y2,$t1,$t2)"
        return if (MOVE_ARG_RE.containsMatchIn(text)) text.replace(MOVE_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removeMove(text: String): String = cleanEmptyBraces(text.replace(MOVE_ARG_RE) { "" })

    // ---------------- \fad & \fade ----------------

    fun getFade(text: String): FadeParams? {
        val match = FAD_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toDouble().roundToInt() }.toList()
        if (nums.size < 2) return null
        return FadeParams(fadeIn = nums[0], fadeOut = nums[1])
    }

    fun setFade(text: String, fadeIn: Int, fadeOut: Int): String {
        val tagInner = "\\fad($fadeIn,$fadeOut)"
        return if (FAD_ARG_RE.containsMatchIn(text)) text.replace(FAD_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removeFade(text: String): String = cleanEmptyBraces(text.replace(FAD_ARG_RE) { "" })

    fun getComplexFade(text: String): ComplexFadeParams? {
        val match = FADE7_ARG_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toDouble().roundToInt() }.toList()
        if (nums.size < 7) return null
        return ComplexFadeParams(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6])
    }

    fun setComplexFade(text: String, p: ComplexFadeParams): String {
        val tagInner = "\\fade(${p.a1},${p.a2},${p.a3},${p.t1},${p.t2},${p.t3},${p.t4})"
        return if (FADE7_ARG_RE.containsMatchIn(text)) text.replace(FADE7_ARG_RE) { tagInner }
        else insertTag(text, tagInner)
    }

    fun removeComplexFade(text: String): String = cleanEmptyBraces(text.replace(FADE7_ARG_RE) { "" })

    // ---------------- \clip / \iclip（矩形 + 矢量）----------------

    fun getClip(text: String): ClipRect? {
        val match = CLIP_RECT_RE.find(text) ?: return null
        val nums = NUM_RE.findAll(match.value).map { it.value.toDouble().roundToInt() }.toList()
        if (nums.size < 4) return null
        val inverse = match.value.startsWith("\\iclip")
        return ClipRect(nums[0], nums[1], nums[2], nums[3], inverse)
    }

    fun setClip(text: String, x1: Int, y1: Int, x2: Int, y2: Int, inverse: Boolean = false): String {
        val tag = "\\${if (inverse) "iclip" else "clip"}($x1,$y1,$x2,$y2)"
        return if (CLIP_RECT_RE.containsMatchIn(text)) text.replace(CLIP_RECT_RE) { tag }
        else insertTag(text, tag)
    }

    fun removeClip(text: String): String = cleanEmptyBraces(text.replace(CLIP_RECT_RE) { "" })

    fun getVectorClip(text: String): VectorClip? {
        val match = CLIP_VEC_RE.find(text) ?: return null
        val inverse = match.groupValues[1] == "iclip"
        val scale = match.groupValues[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val body = match.groupValues[3]
        val nums = NUM_RE.findAll(body).map { it.value.toDouble().roundToInt() }.toList()
        if (nums.size < 2) return null
        val pts = mutableListOf<VecPoint>()
        var i = 0
        while (i + 1 < nums.size) {
            pts += VecPoint(nums[i], nums[i + 1])
            i += 2
        }
        return VectorClip(pts, inverse, scale)
    }

    fun setVectorClip(text: String, clip: VectorClip): String {
        if (clip.points.isEmpty()) return removeVectorClip(text)
        val cmd = clip.toCommandString()
        val prefix = if (clip.inverse) "iclip" else "clip"
        val tag = if (clip.scale > 1) "\\$prefix(${clip.scale},$cmd)" else "\\$prefix($cmd)"
        return if (CLIP_VEC_RE.containsMatchIn(text)) text.replace(CLIP_VEC_RE) { tag }
        else insertTag(text, tag)
    }

    fun removeVectorClip(text: String): String = cleanEmptyBraces(text.replace(CLIP_VEC_RE) { "" })

    // ---------------- 编辑框快捷格式化 ----------------

    fun setInlineToggle(text: String, flag: String, enabled: Boolean): String {
        val re = Regex("""\\${Regex.escape(flag)}[01]?(?=[\\}])""")
        val tag = "\\$flag${if (enabled) 1 else 0}"
        return if (re.containsMatchIn(text)) text.replace(re) { tag } else insertTag(text, tag)
    }

    fun setColorOverride(text: String, slot: Int, color: AssColor): String {
        val cSlot = slot.coerceIn(1, 4)
        val re = if (cSlot == 1) Regex("""\\1?c&?[Hh]?[0-9A-Fa-f]+&?""")
        else Regex("""\\${cSlot}c&?[Hh]?[0-9A-Fa-f]+&?""")
        val tag = "\\${cSlot}c${color.toBgrTagString()}"
        var updated = if (re.containsMatchIn(text)) text.replace(re) { tag } else insertTag(text, tag)

        val alphaRe = Regex("""\\${cSlot}a&?[Hh]?[0-9A-Fa-f]+&?""")
        if (color.a < 255) {
            val aTag = "\\${cSlot}a${color.toAlphaTagString()}"
            updated = if (alphaRe.containsMatchIn(updated)) updated.replace(alphaRe) { aTag } else insertTag(updated, aTag)
        } else if (alphaRe.containsMatchIn(updated)) {
            updated = cleanEmptyBraces(updated.replace(alphaRe) { "" })
        }
        return updated
    }

    fun setBlur(text: String, strength: Double): String {
        val re = Regex("""\\blur\d+(?:\.\d+)?""")
        val formatted = if (strength % 1.0 == 0.0) strength.toInt().toString() else "%.1f".format(strength)
        val tag = "\\blur$formatted"
        return if (re.containsMatchIn(text)) text.replace(re) { tag } else insertTag(text, tag)
    }

    // ---------------- 工具 ----------------

    private fun cleanEmptyBraces(text: String): String =
        if (text.startsWith("{}")) text.removePrefix("{}") else text

    fun insertTag(text: String, tagInner: String): String {
        val close = if (text.startsWith("{")) text.indexOf("}") else -1
        return if (close > 0) {
            val existingInner = text.substring(1, close)
            "{" + tagInner + existingInner + "}" + text.substring(close + 1)
        } else {
            "{" + tagInner + "}" + text
        }
    }
}
