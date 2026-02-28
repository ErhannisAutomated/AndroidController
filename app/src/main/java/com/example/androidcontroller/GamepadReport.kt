package com.example.androidcontroller

/**
 * Mutable gamepad state.  Call [pressButton], [releaseButton], [setDpad],
 * and [setAxes] to update state, then [toBytes] to get the 6-byte HID
 * payload matching the descriptor in [ControllerConfig].
 *
 * Hat-switch encoding used here:
 *   0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 15=centred (null state)
 *
 * Note: [DpadDirection] values are 1-based (per HID spec when Logical Min=1),
 * but our descriptor uses Logical Min=0 with Null=0xF, so we subtract 1
 * when converting.
 */
class GamepadReport {

    // Bitmask of the 12 digital buttons (bits 0–11)
    private var buttons: Int = 0

    // Set of currently pressed D-pad directions (usually 0 or 1, at most 2)
    private val dpadPressed = mutableSetOf<DpadDirection>()

    // Analogue axes in range -127..127
    var leftX:  Int = 0; private set
    var leftY:  Int = 0; private set
    var rightX: Int = 0; private set
    var rightY: Int = 0; private set

    // ── Button press / release ───────────────────────────────────────────────

    fun pressButton(cfg: ButtonConfig) {
        when {
            cfg.dpadDir != null -> {
                dpadPressed += cfg.dpadDir
            }
            cfg.buttonBit >= 0 -> {
                buttons = buttons or (1 shl cfg.buttonBit)
            }
        }
    }

    fun releaseButton(cfg: ButtonConfig) {
        when {
            cfg.dpadDir != null -> {
                dpadPressed -= cfg.dpadDir
            }
            cfg.buttonBit >= 0 -> {
                buttons = buttons and (1 shl cfg.buttonBit).inv()
            }
        }
    }

    fun setAxes(lx: Int = leftX, ly: Int = leftY, rx: Int = rightX, ry: Int = rightY) {
        leftX  = lx.coerceIn(-127, 127)
        leftY  = ly.coerceIn(-127, 127)
        rightX = rx.coerceIn(-127, 127)
        rightY = ry.coerceIn(-127, 127)
    }

    // ── Serialisation ────────────────────────────────────────────────────────

    /**
     * Returns the 6-byte HID input report payload (without Report ID).
     *
     * Byte layout:
     *   [0]     buttons 1–8
     *   [1] lo  buttons 9–12
     *   [1] hi  hat switch (4 bits, 0xF = centred)
     *   [2]     left X
     *   [3]     left Y
     *   [4]     right X
     *   [5]     right Y
     */
    fun toBytes(): ByteArray {
        val hat = resolveHat()          // 0–7 or 0xF
        val byte0 = (buttons and 0xFF).toByte()
        val byte1 = ((buttons shr 8) and 0x0F or (hat shl 4)).toByte()
        return byteArrayOf(byte0, byte1, leftX.toByte(), leftY.toByte(),
                           rightX.toByte(), rightY.toByte())
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolve the set of pressed D-pad directions into a single 4-bit hat
     * value using the 0=N … 7=NW encoding, or 0xF for centred.
     *
     * Diagonal combinations are supported; unsupported combinations
     * (e.g. Up+Down) default to centred.
     */
    private fun resolveHat(): Int {
        val up    = DpadDirection.UP    in dpadPressed
        val right = DpadDirection.RIGHT in dpadPressed
        val down  = DpadDirection.DOWN  in dpadPressed
        val left  = DpadDirection.LEFT  in dpadPressed

        return when {
            up && right  -> 1   // NE
            right && down -> 3  // SE
            down && left -> 5   // SW
            left && up   -> 7   // NW
            up            -> 0  // N
            right         -> 2  // E
            down          -> 4  // S
            left          -> 6  // W
            else          -> 15 // centred (null state)
        }
    }
}
