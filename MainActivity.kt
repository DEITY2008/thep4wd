package com.thep.controller

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

private val CYAN = Color.parseColor("#00E5FF")
private val MAGENTA = Color.parseColor("#FF2BD6")
private val LIME = Color.parseColor("#39FF14")
private val RED = Color.parseColor("#FF1744")

private const val ICON_UP = 0
private const val ICON_DOWN = 1
private const val ICON_LEFT = 2
private const val ICON_RIGHT = 3
private const val ICON_STOP = 4

private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

// ---------------------------------------------------------------
// พื้นหลังกริดนีออนเคลื่อนไหว
// ---------------------------------------------------------------
class GridBackground(ctx: Context) : View(ctx) {
    private val bg = Paint()
    private val g1 = Paint()
    private val g2 = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bg.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#14002E"), Color.parseColor("#05060F"), Color.parseColor("#00202B")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        g1.shader = RadialGradient(w * 0.12f, h * 0.95f, w * 0.5f, withAlpha(MAGENTA, 80), 0, Shader.TileMode.CLAMP)
        g2.shader = RadialGradient(w * 0.92f, h * 0.05f, w * 0.5f, withAlpha(CYAN, 70), 0, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        c.drawRect(0f, 0f, w, h, g1)
        c.drawRect(0f, 0f, w, h, g2)
        val horizon = h * 0.5f
        // เส้นนอน (เลื่อนเข้าหาผู้ใช้)
        for (i in 0 until 9) {
            val f = ((i + t) % 9f) / 9f
            val y = horizon + (h - horizon) * f * f
            line.color = withAlpha(CYAN, (20 + 70 * f).toInt())
            c.drawLine(0f, y, w, y, line)
        }
        // เส้นตั้งแบบเพอร์สเปกทีฟ
        for (k in -12..12) {
            line.color = withAlpha(MAGENTA, 38)
            c.drawLine(w / 2f + k * w * 0.02f, horizon, w / 2f + k * w * 0.10f, h, line)
        }
    }
}

// ---------------------------------------------------------------
// ปุ่มนีออนเรืองแสง
// ---------------------------------------------------------------
class NeonButton(
    ctx: Context,
    private val label: String,
    private val neon: Int,
    private val icon: Int = -1,
    private val round: Boolean = false,
    private val textSizeSp: Float = 14f
) : View(ctx) {
    var onDown: (() -> Unit)? = null
    var onUp: (() -> Unit)? = null
    var onClick: (() -> Unit)? = null
    private var down = false
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        color = neon
    }
    private val ico = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = neon }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = 12f * d
        rect.set(m, m, w - m, h - m)
        val r = if (round) minOf(rect.width(), rect.height()) / 2f else 22f * d

        fill.color = withAlpha(neon, if (down) 130 else 28)
        c.drawRoundRect(rect, r, r, fill)
        stroke.setShadowLayer(if (down) 28f * d else 12f * d, 0f, 0f, neon)
        c.drawRoundRect(rect, r, r, stroke)

        val cx = w / 2f
        val cy = h / 2f
        if (icon < 0) {
            txt.textSize = textSizeSp * d
            c.drawText(label, cx, cy + txt.textSize / 3f, txt)
            return
        }
        val s = minOf(w, h) * 0.16f
        val iy = cy - h * 0.07f
        ico.setShadowLayer(if (down) 22f * d else 12f * d, 0f, 0f, neon)

        if (icon == ICON_STOP) {
            c.drawRoundRect(cx - s, iy - s, cx + s, iy + s, 6f * d, 6f * d, ico)
        } else {
            val angle = when (icon) {
                ICON_UP -> 0f
                ICON_RIGHT -> 90f
                ICON_DOWN -> 180f
                else -> 270f
            }
            path.reset()
            path.moveTo(cx, iy - s * 1.3f)
            path.lineTo(cx + s * 1.2f, iy + s * 0.9f)
            path.lineTo(cx, iy + s * 0.35f)
            path.lineTo(cx - s * 1.2f, iy + s * 0.9f)
            path.close()
            c.save()
            c.rotate(angle, cx, iy)
            c.drawPath(path, ico)
            c.restore()
        }
        txt.textSize = 13f * d
        c.drawText(label, cx, cy + h * 0.30f, txt)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                parent?.requestDisallowInterceptTouchEvent(true)
                onDown?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                if (down && (e.x < 0 || e.y < 0 || e.x > width || e.y > height)) {
                    down = false
                    invalidate()
                    onUp?.invoke()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (down) {
                    down = false
                    invalidate()
                    onUp?.invoke()
                    onClick?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (down) {
                    down = false
                    invalidate()
                    onUp?.invoke()
                }
            }
        }
        return true
    }
}

// ---------------------------------------------------------------
// หน้าจอหลัก
// ---------------------------------------------------------------
@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity() {

    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()   // ใช้ "เขียน" อย่างเดียว

    private var adapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    @Volatile private var connected = false
    private var deviceName = "-"

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private var statusBar: TextView? = null
    private var listBox: LinearLayout? = null
    private var pulse: ObjectAnimator? = null

    private var currentCmd = 'S'
    private val sender = object : Runnable {
        override fun run() {
            if (currentCmd != 'S') {
                send(currentCmd)
                ui.postDelayed(this, 100)
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glowText(text: String, size: Float, color: Int, glow: Int = color) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setShadowLayer(18f, 0f, 0f, glow)
    }

    private fun title(text: String, size: Float) = glowText(text, size, Color.WHITE, CYAN).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        letterSpacing = 0.1f
        val wpx = paint.measureText(text) * 1.3f
        paint.shader = LinearGradient(0f, 0f, wpx, 0f, CYAN, MAGENTA, Shader.TileMode.CLAMP)
    }

    private fun btn(text: String, size: Float, c: Int): Button = Button(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = GradientDrawable().apply {
            setColor(withAlpha(c, 40))
            setStroke(dp(2), c)
            cornerRadius = dp(14).toFloat()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        root = FrameLayout(this)
        root.addView(GridBackground(this), FrameLayout.LayoutParams(-1, -1))
        content = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        showStart()
    }

    // ---------- หน้าเริ่มต้น ----------
    private fun showStart() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(title("THEP 4WD", 52f))
        col.addView(glowText("B L U E T O O T H   R C   C O N T R O L L E R", 11f, CYAN))
        val c = NeonButton(this, "CONNECT BLUETOOTH", CYAN, textSizeSp = 17f)
        c.onClick = { ensurePermissions { showDevices() } }
        col.addView(c, LinearLayout.LayoutParams(dp(320), dp(96)).apply { topMargin = dp(18) })
        col.addView(glowText("จับคู่ THEP_4WD ใน Settings > Bluetooth ก่อนใช้งาน", 11f, Color.LTGRAY, MAGENTA))
        setScreen(col)
    }

    // ---------- หน้าเลือกอุปกรณ์ ----------
    private val found = LinkedHashMap<String, BluetoothDevice>()
    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == BluetoothDevice.ACTION_FOUND) {
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else
                    i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (d != null && !found.containsKey(d.address)) {
                    found[d.address] = d
                    refreshList()
                }
            }
        }
    }
    private var receiverOn = false

    private fun showDevices() {
        val a = adapter
        if (a == null) { toast("เครื่องนี้ไม่มี Bluetooth"); return }
        if (!a.isEnabled) { toast("กรุณาเปิด Bluetooth ก่อน"); return }
        found.clear()
        a.bondedDevices.forEach { found[it.address] = it }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(glowText("เลือกอุปกรณ์  →  THEP_4WD", 20f, CYAN))
        val lb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listBox = lb
        col.addView(ScrollView(this).apply { addView(lb) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this)
        val scan = btn("ค้นหาอุปกรณ์", 16f, CYAN)
        val back = btn("กลับ", 16f, Color.GRAY)
        scan.setOnClickListener { startScan() }
        back.setOnClickListener { stopScan(); showStart() }
        row.addView(scan, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        row.addView(back, LinearLayout.LayoutParams(0, dp(56), 1f))
        col.addView(row)
        setScreen(col)
        refreshList()
    }

    private fun refreshList() {
        val lb = listBox ?: return
        lb.removeAllViews()
        val sorted = found.values.sortedByDescending { it.name == "THEP_4WD" }
        for (d in sorted) {
            val name = d.name ?: "(ไม่มีชื่อ)"
            val isCar = name == "THEP_4WD"
            val b = btn("$name\n${d.address}", 16f, if (isCar) MAGENTA else Color.GRAY)
            b.setOnClickListener { stopScan(); connect(d) }
            lb.addView(b, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(6) })
        }
    }

    private fun startScan() {
        if (!receiverOn) {
            registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            receiverOn = true
        }
        adapter?.cancelDiscovery()
        adapter?.startDiscovery()
        toast("กำลังค้นหา...")
    }

    private fun stopScan() {
        adapter?.cancelDiscovery()
        if (receiverOn) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            receiverOn = false
        }
    }

    // ---------- เชื่อมต่อ ----------
    private fun connect(d: BluetoothDevice) {
        toast("กำลังเชื่อมต่อ ${d.name}...")
        Thread {
            try {
                try { socket?.close() } catch (_: Exception) {}
                val s = d.createRfcommSocketToServiceRecord(SPP)
                adapter?.cancelDiscovery()
                s.connect()
                socket = s
                out = s.outputStream
                connected = true
                deviceName = d.name ?: d.address
                ui.post { showControl() }
                readLoop(s.inputStream)       // เธรดแยกของตัวเอง ไม่ขวางการส่งคำสั่ง
            } catch (e: Exception) {
                connected = false
                ui.post { toast("เชื่อมต่อไม่สำเร็จ: ${e.message}") }
            }
        }.start()
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(128)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                val msg = String(buf, 0, n).trim()
                ui.post { statusBar?.text = statusText("ESP32: $msg") }
            }
        } catch (_: Exception) {}
        if (connected) {
            connected = false
            ui.post { onLost() }
        }
    }

    private fun onLost() {
        toast("Bluetooth หลุด")
        currentCmd = 'S'
        ui.removeCallbacks(sender)
        closeSocket()
        showStart()
    }

    private fun send(c: Char) {
        io.execute {
            try {
                out?.write(c.code)
                out?.flush()
            } catch (e: Exception) {
                if (connected) {
                    connected = false
                    ui.post { onLost() }
                }
            }
        }
    }

    private fun disconnect() {
        currentCmd = 'S'
        ui.removeCallbacks(sender)
        send('S')
        io.execute { Thread.sleep(150); closeSocket() }
        connected = false
        showStart()
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
        connected = false
    }

    // ---------- หน้าควบคุมรถ ----------
    private fun statusText(extra: String = "ส่งคำสั่ง: $currentCmd") =
        (if (connected) "Bluetooth Connected" else "Disconnected") + "  |  $deviceName  |  $extra"

    private fun showControl() {
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // แถบสถานะ
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (connected) LIME else RED)
            }
        }
        pulse = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        top.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) })
        val sb = glowText(statusText(), 12f, CYAN).apply { gravity = Gravity.START }
        statusBar = sb
        top.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        val dc = btn("DISCONNECT", 12f, RED)
        dc.setOnClickListener { disconnect() }
        top.addView(dc, LinearLayout.LayoutParams(dp(130), dp(40)))
        main.addView(top, LinearLayout.LayoutParams(-1, dp(48)))

        fun drive(label: String, icon: Int, cmd: Char, color: Int) =
            NeonButton(this, label, color, icon).apply {
                onDown = { press(cmd) }
                onUp = { release() }
            }

        val body = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

        // ซ้าย: เลี้ยวซ้าย / ขวา
        val steer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        steer.addView(drive("เลี้ยวซ้าย", ICON_LEFT, 'L', MAGENTA), LinearLayout.LayoutParams(0, dp(170), 1f))
        steer.addView(drive("เลี้ยวขวา", ICON_RIGHT, 'R', MAGENTA), LinearLayout.LayoutParams(0, dp(170), 1f))

        // กลาง: ชื่อรถ + STOP
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        mid.addView(title("THEP 4WD", 22f))
        val stop = NeonButton(this, "หยุด", RED, ICON_STOP, round = true)
        stop.onDown = { release() }
        mid.addView(stop, LinearLayout.LayoutParams(dp(150), dp(150)))
        mid.addView(glowText("กดค้าง = วิ่ง  |  ปล่อย = หยุด", 10f, Color.LTGRAY, CYAN))

        // ขวา: เดินหน้า / ถอยหลัง
        val thr = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        thr.addView(drive("เดินหน้า", ICON_UP, 'F', CYAN), LinearLayout.LayoutParams(-1, 0, 1f))
        thr.addView(drive("ถอยหลัง", ICON_DOWN, 'B', CYAN), LinearLayout.LayoutParams(-1, 0, 1f))

        body.addView(steer, LinearLayout.LayoutParams(0, -1, 1.6f))
        body.addView(mid, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(thr, LinearLayout.LayoutParams(0, -1, 0.9f))
        main.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setScreen(main)
    }

    private fun press(c: Char) {
        currentCmd = c
        send(c)
        ui.removeCallbacks(sender)
        ui.postDelayed(sender, 100)
        statusBar?.text = statusText()
    }

    private fun release() {
        currentCmd = 'S'
        ui.removeCallbacks(sender)
        send('S')
        statusBar?.text = statusText()
    }

    // ---------- Permission ----------
    private var afterPerm: (() -> Unit)? = null

    private fun ensurePermissions(then: () -> Unit) {
        val need = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) then() else {
            afterPerm = then
            requestPermissions(missing.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) afterPerm?.invoke()
        else toast("ต้องอนุญาต Permission Bluetooth ก่อนใช้งาน")
        afterPerm = null
    }

    // ---------- ความปลอดภัย: แอปออกจากหน้าจอ -> หยุดรถ ----------
    override fun onPause() {
        super.onPause()
        if (connected) release()
    }

    override fun onDestroy() {
        stopScan()
        if (connected) {
            try { out?.write('S'.code); out?.flush() } catch (_: Exception) {}
        }
        closeSocket()
        super.onDestroy()
    }

    private fun setScreen(v: View) {
        pulse?.cancel()
        pulse = null
        content.removeAllViews()
        content.addView(v, FrameLayout.LayoutParams(-1, -1))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
