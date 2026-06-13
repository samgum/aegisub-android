// 伤感咩吖
package io.github.samgum.aegisub.domain.parse

object DialogueTokenType {
    const val TEXT = 1000
    const val WORD = 1001
    const val LINE_BREAK = 1002
    const val OVR_BEGIN = 1003
    const val OVR_END = 1004
    const val TAG_START = 1005
    const val TAG_NAME = 1006
    const val OPEN_PAREN = 1007
    const val CLOSE_PAREN = 1008
    const val ARG_SEP = 1009
    const val ARG = 1010
    const val ERROR = 1011
    const val COMMENT = 1012
    const val WHITESPACE = 1013
    const val DRAWING_FULL = 1014
    const val DRAWING_CMD = 1015
    const val DRAWING_X = 1016
    const val DRAWING_Y = 1017
}

data class DialogueToken(val type: Int, val length: Int)
