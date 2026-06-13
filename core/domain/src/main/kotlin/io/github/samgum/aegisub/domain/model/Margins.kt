package io.github.samgum.aegisub.domain.model

/** ASS 三段式边距：左 / 右 / 纵向（对齐 C++ std::array<int,3> Margin）。 */
data class Margins(val left: Int = 0, val right: Int = 0, val vertical: Int = 0) {
    companion object { val ZERO = Margins() }
}
