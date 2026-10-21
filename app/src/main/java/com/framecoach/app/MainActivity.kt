package com.framecoach.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.framecoach.core.*
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private val ink = Color.rgb(16, 19, 16)
    private val panel = Color.rgb(28, 33, 28)
    private val lime = Color.rgb(214, 245, 134)
    private val muted = Color.rgb(170, 182, 165)
    private lateinit var preview: PreviewView
    private lateinit var demoView: DemoView
    private lateinit var overlay: GuideOverlay
    private lateinit var badge: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tipList: LinearLayout
    private lateinit var shutter: Button
    private lateinit var cameraButton: Button
    private lateinit var modePortrait: Button
    private lateinit var modeLandscape: Button
    private lateinit var layoutButton: Button
    private lateinit var moodButton: Button
    private lateinit var dwellButton: Button
    private var mode = SceneMode.PORTRAIT
    private var composition = Layout.THIRDS
    private var mood = Mood.NATURAL
    private var dwell = 2000L
    private var gate = SceneStability(dwell)
    private var camera: CameraSession? = null
    private var demo = true
    private var observation = Observation(Light(.5f, .1f, .02f))
    private var horizon: Float? = null
    private var ready = false
    private var lastTips = 0L
    private var started = false
    private var capturing = false
    private val handler = Handler(Looper.getMainLooper())
    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private var gyroSpeed = 0f
    private var gyroAt = 0L

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            toast("未开启相机权限，仍可体验演示。可在系统设置中授权后重试。")
            showDemo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (savedInstanceState != null) {
            mode = SceneMode.valueOf(savedInstanceState.getString("mode") ?: SceneMode.PORTRAIT.name)
            composition = Layout.valueOf(savedInstanceState.getString("layout") ?: Layout.THIRDS.name)
            mood = Mood.valueOf(savedInstanceState.getString("mood") ?: Mood.NATURAL.name)
            dwell = savedInstanceState.getLong("dwell", 2000L)
            gate = SceneStability(dwell)
        }
        buildScreen()
        showDemo()
        if (savedInstanceState?.getBoolean("camera") == true && hasPermission()) startCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("mode", mode.name); outState.putString("layout", composition.name)
        outState.putString("mood", mood.name); outState.putLong("dwell", dwell); outState.putBoolean("camera", !demo)
        super.onSaveInstanceState(outState)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), 0, dp(12), 0) }
        header.addView(label("⌗  观景", 23f, lime).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(button("知识与说明") { showKnowledge() })
        root.addView(header)

        val modes = row().apply { setPadding(dp(12), 0, dp(12), dp(5)) }
        modePortrait = button("人像") { changeMode(SceneMode.PORTRAIT) }
        modeLandscape = button("风光") { changeMode(SceneMode.LANDSCAPE) }
        modes.addView(modePortrait, LinearLayout.LayoutParams(0, dp(42), 1f))
        modes.addView(modeLandscape, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(modes)

        val stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); minimumHeight = dp(100) }
        preview = PreviewView(this)
        demoView = DemoView(this)
        overlay = GuideOverlay(this).apply { onHorizon = { y -> horizon = y; if (ready) renderTips(); toast("已手动标记水平线") } }
        stage.addView(preview, match())
        stage.addView(demoView, match())
        stage.addView(overlay, match())
        badge = label("演示场景 · 非实时识别", 11f, lime).apply { setPadding(dp(10), dp(5), dp(10), dp(5)); background = rounded(Color.argb(210, 16, 19, 16)) }
        stage.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), dp(12), 0) })
        status = label("保持取景，等待 2 秒", 12f, Color.WHITE).apply { gravity = Gravity.CENTER; setBackgroundColor(Color.argb(200, 16, 19, 16)) }
        stage.addView(status, FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM))

        val advice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel) }
        val titleRow = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(8), 0) }
        titleRow.addView(label("拍摄提示", 13f, lime), LinearLayout.LayoutParams(0, dp(32), 1f))
        titleRow.addView(button("重新观察") { resetAnalysis() }, LinearLayout.LayoutParams(dp(90), dp(36)))
        advice.addView(titleRow)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progressTintList = android.content.res.ColorStateList.valueOf(lime) }
        advice.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        tipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)) }
        scroll.addView(tipList)
        advice.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val middle = row()
            middle.addView(stage, LinearLayout.LayoutParams(0, -1, 1f))
            middle.addView(advice, LinearLayout.LayoutParams(dp(300), -1))
            root.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(advice, LinearLayout.LayoutParams(-1, dp(168)))
        }

        val controls = row().apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(2), dp(6), 0) }
        layoutButton = button(composition.label) { chooseLayout() }
        moodButton = button(moodLabel()) { chooseMood() }
        dwellButton = button("停留 ${dwell / 1000}s") {
            dwell = when (dwell) { 2000L -> 3000L; 3000L -> 5000L; else -> 2000L }
            dwellButton.text = "停留 ${dwell / 1000}s"; gate = SceneStability(dwell); resetAnalysis()
        }
        val guides = button("辅助线 开") { view ->
            overlay.showGuides = !overlay.showGuides
            (view as Button).text = "辅助线 ${if (overlay.showGuides) "开" else "关"}"; overlay.invalidate()
        }
        listOf(layoutButton, moodButton, dwellButton, guides).forEach { controls.addView(it, LinearLayout.LayoutParams(0, dp(42), 1f)) }
        root.addView(controls)

        val footer = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(2), dp(8), dp(8)) }
        cameraButton = button("开启相机") { if (demo) requestCamera() else showDemo() }
        footer.addView(cameraButton, LinearLayout.LayoutParams(0, dp(58), 1f))
        shutter = button("●  拍照") { capture() }.apply { background = rounded(lime); setTextColor(ink); textSize = 18f }
        footer.addView(shutter, LinearLayout.LayoutParams(dp(116), dp(58)).apply { setMargins(dp(8), 0, dp(8), 0) })
        footer.addView(button("翻转镜头") {
            if (demo) toast("开启相机后可切换前后镜头") else { camera?.flip(); resetAnalysis() }
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(footer)
        updateModeUi()
    }

    private fun requestCamera() {
        if (hasPermission()) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (isFinishing || isDestroyed) return
        camera?.close()
        demo = false; demoView.visibility = View.GONE; cameraButton.text = "返回演示"
        badge.text = "本机实时分析 · 原片不上传"
        resetAnalysis()
        camera = CameraSession(this, this, preview,
            onFrame = { reading ->
                if (!demo && started) {
                    observation = observation.copy(light = reading.light)
                    val gyro = if (SystemClock.elapsedRealtime() - gyroAt < 500) gyroSpeed else 0f
                    acceptStability(gate.update(reading.sample, gyro, reading.atMs))
                }
            },
            onPeople = { faces, body, error ->
                if (!demo && started) {
                    observation = observation.copy(faces = faces, body = body, detectorError = error)
                    overlay.body = if (mode == SceneMode.PORTRAIT) body else null
                    overlay.invalidate()
                    if (ready && SystemClock.elapsedRealtime() - lastTips > 1200) renderTips()
                }
            },
            onError = { message -> if (!demo) { status.text = message; toast(message) } }
        ).apply { portrait = mode == SceneMode.PORTRAIT; start() }
    }

    private fun showDemo() {
        camera?.close(); camera = null
        demo = true; demoView.visibility = View.VISIBLE; demoView.portrait = mode == SceneMode.PORTRAIT; demoView.invalidate()
        badge.text = "演示场景 · 非实时识别"; cameraButton.text = "开启相机"
        observation = demoObservation()
        resetAnalysis()
    }

    private val demoTick = object : Runnable {
        override fun run() {
            if (!started) return
            if (demo) {
                observation = demoObservation()
                acceptStability(gate.update(FloatArray(48) { .25f + (it % 9) * .06f }, 0f, SystemClock.elapsedRealtime()))
                overlay.body = if (mode == SceneMode.PORTRAIT) observation.body else null
                overlay.invalidate()
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun acceptStability(state: Stability) {
        progress.progress = (state.progress * 100).toInt()
        if (state.moving) {
            horizon = null
            if (ready) { overlay.guidance = null; overlay.invalidate(); showWaiting() }
            ready = false
            status.text = "镜头正在移动 · 稳定后重新观察"
        } else {
            ready = state.ready
            status.text = if (ready) if (demo) "演示建议已就绪 · 开启相机试试" else "取景稳定 · 可以尝试下方建议" else "保持取景 ${(state.progress * dwell / 1000).toInt()} / ${dwell / 1000} 秒"
        }
        if (state.triggered || (ready && SystemClock.elapsedRealtime() - lastTips > 2000)) renderTips()
    }

    private fun renderTips() {
        if (!ready) return
        val guidance = Coach.advise(mode, composition, mood, observation, horizon)
        overlay.guidance = guidance; overlay.invalidate()
        lastTips = SystemClock.elapsedRealtime()
        tipList.removeAllViews()
        if (demo) tipList.addView(label("以下基于合成演示数据，点击提示查看依据", 10f, muted))
        guidance.tips.forEach { tip ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, dp(5), 0, dp(7))
                isClickable = true; isFocusable = true
                contentDescription = "${tip.category}：${tip.title}。${tip.instruction}。点击查看依据"
                setOnClickListener { showTip(tip) }
            }
            item.addView(label("${tip.category}  /  ${tip.title}  ↗", 13f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD })
            item.addView(label(tip.instruction, 12f, muted))
            tipList.addView(item)
        }
    }

    private fun resetAnalysis() {
        gate.reset(); ready = false; horizon = null; progress.progress = 0
        overlay.guidance = null; overlay.body = null; overlay.invalidate()
        observation = if (demo) demoObservation() else Observation(observation.light)
        status.text = "保持取景，等待 ${dwell / 1000} 秒"
        showWaiting()
    }
    private fun showWaiting() {
        tipList.removeAllViews()
        tipList.addView(label("慢一点，先看见画面。", 17f, Color.WHITE))
        tipList.addView(label(if (mode == SceneMode.PORTRAIT) "保持背景稳定，建议会在停留后出现。\n人物可在画面中间活动。" else "保持取景稳定。建议出现后，点击画面可标记水平线。", 12f, muted).apply { setPadding(0, dp(9), 0, 0) })
    }
    private fun changeMode(value: SceneMode) {
        mode = value; camera?.portrait = mode == SceneMode.PORTRAIT
        demoView.portrait = mode == SceneMode.PORTRAIT; demoView.invalidate()
        updateModeUi(); resetAnalysis()
    }
    private fun updateModeUi() {
        modePortrait.setTextColor(if (mode == SceneMode.PORTRAIT) ink else muted)
        modePortrait.background = rounded(if (mode == SceneMode.PORTRAIT) lime else panel)
        modeLandscape.setTextColor(if (mode == SceneMode.LANDSCAPE) ink else muted)
        modeLandscape.background = rounded(if (mode == SceneMode.LANDSCAPE) lime else panel)
        moodButton.visibility = if (mode == SceneMode.PORTRAIT) View.VISIBLE else View.GONE
        overlay.landscape = mode == SceneMode.LANDSCAPE; overlay.layout = composition
    }
    private fun chooseLayout() {
        AlertDialog.Builder(this).setTitle("选择你想尝试的构图")
            .setSingleChoiceItems(Layout.entries.map { it.label }.toTypedArray(), composition.ordinal) { dialog, which ->
                composition = Layout.entries[which]; layoutButton.text = composition.label
                overlay.layout = composition; overlay.invalidate(); if (ready) renderTips(); dialog.dismiss()
            }.show()
    }
    private fun moodLabel() = when (mood) { Mood.NATURAL -> "自然"; Mood.QUIET -> "安静"; Mood.LIVELY -> "轻快" }
    private fun chooseMood() {
        AlertDialog.Builder(this).setTitle("想拍出什么感觉？")
            .setSingleChoiceItems(arrayOf("自然", "安静", "轻快"), mood.ordinal) { dialog, which ->
                mood = Mood.entries[which]; moodButton.text = moodLabel(); if (ready) renderTips(); dialog.dismiss()
            }.show()
    }
    private fun showTip(tip: Tip) {
        AlertDialog.Builder(this).setTitle(tip.title)
            .setMessage("${tip.instruction}\n\n依据与限制\n${tip.evidence}\n\n${tip.sourceIds.mapNotNull { Knowledge.find(it)?.title }.joinToString("\n")}")
            .setPositiveButton("了解了", null)
            .setNeutralButton("查看来源") { _, _ -> chooseSource(tip.sourceIds.mapNotNull(Knowledge::find)) }.show()
    }
    private fun chooseSource(sources: List<Source>) {
        AlertDialog.Builder(this).setTitle("知识来源")
            .setItems(sources.map { it.title }.toTypedArray()) { _, i ->
                val source = sources[i]
                AlertDialog.Builder(this).setTitle(source.title).setMessage(source.note)
                    .setPositiveButton("打开原文") { _, _ ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }.onFailure { toast("设备没有可打开链接的浏览器") }
                    }.setNegativeButton("返回", null).show()
            }.setNegativeButton("关闭", null).show()
    }
    private fun showKnowledge() {
        AlertDialog.Builder(this).setTitle("观景 · 早期试用版")
            .setMessage("稳定取景 → 观察画面 → 尝试建议 → 保存照片\n\n实时帧在本机处理，应用代码不上传照片。不识别身份，不对容貌或情绪打分。只保存你主动拍摄的照片，辅助线不会写进原片。ML Kit 自身的数据实践请见项目隐私说明。\n\n风光水平线由你手动标记；单帧亮度无法确定太阳方向。复杂人群、暗光、遮挡和动态背景会影响检测。\n\n当前支持手机相机，不支持连接单反或微单的实时取景。公开课程和书目用于知识整理，并未读完你提到的所有书籍，也未用获奖作品训练模型。")
            .setPositiveButton("查看知识来源") { _, _ -> chooseSource(Knowledge.sources) }
            .setNegativeButton("关闭", null).show()
    }

    private fun capture() {
        if (demo) { toast("当前是合成演示场景，请开启相机后拍照"); return }
        if (capturing) return
        capturing = true; shutter.isEnabled = false
        camera?.capture { success ->
            if (!isDestroyed) { capturing = false; shutter.isEnabled = true; toast(if (success) "照片已保存到相册 Pictures/FrameCoach" else "拍摄失败，请检查存储空间后重试") }
        } ?: run { capturing = false; shutter.isEnabled = true }
    }

    override fun onResume() {
        super.onResume(); started = true
        gate.reset(); ready = false
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        handler.post(demoTick)
    }
    override fun onPause() {
        started = false; handler.removeCallbacks(demoTick); sensors.unregisterListener(this)
        resetAnalysis(); super.onPause()
    }
    override fun onDestroy() { camera?.close(); camera = null; super.onDestroy() }
    override fun onSensorChanged(event: SensorEvent) {
        gyroSpeed = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat()
        gyroAt = SystemClock.elapsedRealtime()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun demoObservation(): Observation {
        if (mode == SceneMode.LANDSCAPE) return Observation(Light(.5f, .1f, .02f))
        return Observation(Light(.5f, .1f, .02f), listOf(Face(Point(.43f, .34f), .08f, .10f, 0f, null)), Body(mapOf(
            Joint.LEFT_SHOULDER to Point(.385f, .42f), Joint.RIGHT_SHOULDER to Point(.48f, .42f),
            Joint.LEFT_ELBOW to Point(.365f, .53f), Joint.RIGHT_ELBOW to Point(.5f, .51f),
            Joint.LEFT_WRIST to Point(.35f, .63f), Joint.RIGHT_WRIST to Point(.51f, .61f),
            Joint.LEFT_HIP to Point(.405f, .61f), Joint.RIGHT_HIP to Point(.455f, .61f),
            Joint.LEFT_KNEE to Point(.397f, .73f), Joint.RIGHT_KNEE to Point(.475f, .73f),
            Joint.LEFT_ANKLE to Point(.39f, .84f), Joint.RIGHT_ANKLE to Point(.49f, .83f)
        )))
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, action: (View) -> Unit) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false; setTextColor(muted)
        background = rounded(ink); setPadding(dp(5), 0, dp(5), 0); minimumWidth = 0; minWidth = 0
        setOnClickListener(action)
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
