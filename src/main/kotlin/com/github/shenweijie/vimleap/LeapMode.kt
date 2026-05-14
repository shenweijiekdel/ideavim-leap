package com.github.shenweijie.vimleap

enum class LeapMode(
    val backward: Boolean = false,
    val bidirectional: Boolean = false,
    val crossWindow: Boolean = false,
    val offset: Int = 0,           // 0=land on match, -1=till forward, +1=till backward
    val singleChar: Boolean = false,  // flit-style: accept one char and search immediately
) {
    FORWARD,
    BACKWARD(backward = true),
    ANYWHERE(bidirectional = true),
    FORWARD_TILL(offset = -1),
    BACKWARD_TILL(backward = true, offset = +1),
    FLIT_F(singleChar = true),
    FLIT_F_BACKWARD(backward = true, singleChar = true),
    FLIT_T(offset = -1, singleChar = true),
    FLIT_T_BACKWARD(backward = true, offset = +1, singleChar = true),
    REMOTE,
    TREESITTER;

    fun flipped(): LeapMode = when (this) {
        FORWARD          -> BACKWARD
        BACKWARD         -> FORWARD
        FORWARD_TILL     -> BACKWARD_TILL
        BACKWARD_TILL    -> FORWARD_TILL
        FLIT_F           -> FLIT_F_BACKWARD
        FLIT_F_BACKWARD  -> FLIT_F
        FLIT_T           -> FLIT_T_BACKWARD
        FLIT_T_BACKWARD  -> FLIT_T
        else             -> this   // ANYWHERE, REMOTE, TREESITTER — no flip
    }
}
