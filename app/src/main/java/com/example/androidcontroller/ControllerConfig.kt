package com.example.androidcontroller

/**
 * Describes one on-screen button and its mapping to the HID report.
 *
 * [label]       Text shown on the button.
 * [buttonBit]   Which bit in [GamepadReport.buttons] this button controls
 *               (0-based). -1 means the button drives an axis instead.
 * [dpadDir]     If non-null this button sends a D-pad hat-switch value
 *               rather than a regular button bit.
 */
data class ButtonConfig(
    val label: String,
    val buttonBit: Int = -1,
    val dpadDir: DpadDirection? = null,
)

/**
 * D-pad hat-switch values (HID standard: 1=N … 8=NW, 0=centred / null).
 */
enum class DpadDirection(val hatValue: Byte) {
    UP(1),
    UP_RIGHT(2),
    RIGHT(3),
    DOWN_RIGHT(4),
    DOWN(5),
    DOWN_LEFT(6),
    LEFT(7),
    UP_LEFT(8),
}

/**
 * Top-level controller configuration.
 *
 * Changing values here (or loading from JSON/SharedPreferences in future)
 * will automatically update labels and HID mappings without touching the
 * activity or HID logic.
 */
object ControllerConfig {

    // ── HID descriptor ─────────────────────────────────────────────────────
    //
    // Report layout (6 bytes, Report ID = 1):
    //   byte 0       buttons  1–8   (A B X Y LB RB LT RT)
    //   byte 1 [3:0] buttons  9–12  (Back Start LS RS)
    //   byte 1 [7:4] hat switch (0=centred, 1=N … 8=NW)
    //   byte 2       left  stick X  (-127 … 127)
    //   byte 3       left  stick Y  (-127 … 127)
    //   byte 4       right stick X  (-127 … 127)
    //   byte 5       right stick Y  (-127 … 127)

    val hidDescriptor: ByteArray = byteArrayOf(
        0x05, 0x01,              // Usage Page (Generic Desktop)
        0x09, 0x05,              // Usage (Game Pad)
        0xA1.b, 0x01,            // Collection (Application)
        0xA1.b, 0x00,            //   Collection (Physical)
        0x85.b, 0x01,            //     Report ID (1)
        // 12 digital buttons
        0x05, 0x09,              //     Usage Page (Button)
        0x19, 0x01,              //     Usage Minimum (1)
        0x29, 0x0C,              //     Usage Maximum (12)
        0x15, 0x00,              //     Logical Minimum (0)
        0x25, 0x01,              //     Logical Maximum (1)
        0x75, 0x01,              //     Report Size (1)
        0x95.b, 0x0C,            //     Report Count (12)
        0x81.b, 0x02,            //     Input (Data, Variable, Absolute)
        // 4-bit hat switch
        0x05, 0x01,              //     Usage Page (Generic Desktop)
        0x09, 0x39,              //     Usage (Hat Switch)
        0x15, 0x00,              //     Logical Minimum (0)  — 0 = centred
        0x25, 0x07,              //     Logical Maximum (7)  — 0=N,1=NE,…,7=NW
        0x35, 0x00,              //     Physical Minimum (0)
        0x46.b, 0x3B, 0x01,      //     Physical Maximum (315)
        0x65, 0x14,              //     Unit (Eng Rot: Angular Pos)
        0x75, 0x04,              //     Report Size (4)
        0x95.b, 0x01,            //     Report Count (1)
        0x81.b, 0x42,            //     Input (Data, Variable, Absolute, Null)
        // 4 analogue axes (left X/Y, right X/Y)
        0x05, 0x01,              //     Usage Page (Generic Desktop)
        0x09, 0x30,              //     Usage (X)
        0x09, 0x31,              //     Usage (Y)
        0x09, 0x32,              //     Usage (Z)
        0x09, 0x35,              //     Usage (Rz)
        0x15.b, 0x81.b,          //     Logical Minimum (-127)
        0x25, 0x7F,              //     Logical Maximum (127)
        0x75, 0x08,              //     Report Size (8)
        0x95.b, 0x04,            //     Report Count (4)
        0x81.b, 0x02,            //     Input (Data, Variable, Absolute)
        0xC0.b,                  //   End Collection
        0xC0.b                   // End Collection
    )

    // ── Button configs ──────────────────────────────────────────────────────

    // Face buttons (right cluster, diamond layout)
    val btnA      = ButtonConfig("A",    buttonBit = 0)
    val btnB      = ButtonConfig("B",    buttonBit = 1)
    val btnX      = ButtonConfig("X",    buttonBit = 2)
    val btnY      = ButtonConfig("Y",    buttonBit = 3)

    // Shoulder / trigger buttons
    val btnLB     = ButtonConfig("LB",   buttonBit = 4)
    val btnRB     = ButtonConfig("RB",   buttonBit = 5)
    val btnLT     = ButtonConfig("LT",   buttonBit = 6)
    val btnRT     = ButtonConfig("RT",   buttonBit = 7)

    // Centre buttons
    val btnBack   = ButtonConfig("☰",    buttonBit = 8)
    val btnStart  = ButtonConfig("▶",    buttonBit = 9)

    // Stick clicks (not shown on screen by default — easy to add)
    val btnLS     = ButtonConfig("L3",   buttonBit = 10)
    val btnRS     = ButtonConfig("R3",   buttonBit = 11)

    // D-pad (hat switch)
    val btnDUp    = ButtonConfig("▲",    dpadDir = DpadDirection.UP)
    val btnDRight = ButtonConfig("▶",    dpadDir = DpadDirection.RIGHT)
    val btnDDown  = ButtonConfig("▼",    dpadDir = DpadDirection.DOWN)
    val btnDLeft  = ButtonConfig("◀",    dpadDir = DpadDirection.LEFT)

    // ── BT device metadata ──────────────────────────────────────────────────
    const val deviceName        = "Android Gamepad"
    const val deviceDescription = "Game Controller"
    const val deviceProvider    = "Android"

    // Subclass bytes: subclass1=0x00 (generic), subclass2=0x02 (gamepad)
    const val subclass1: Byte = 0x00
    const val subclass2: Byte = 0x02

    // HID Report ID used for the gamepad report
    const val reportId: Int = 1
}

// Helper to make the descriptor array more readable
private val Int.b get() = toByte()
