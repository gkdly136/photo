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


class MainActivity : ComponentActivity(), SensorEventListener {
    private val ink = Color.rgb(17, 19, 21)
    private val panel = Color.rgb(29, 32, 35)
    private val lime = Color.rgb(235, 198, 151)
    private val muted = Color.rgb(167, 173, 180)
    private lateinit var preview: PreviewView
    private lateinit var demoView: DemoView
    private lateinit var overlay: GuideOverlay
    private lateinit var badge: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tipList: LinearLayout
    private lateinit var shutter: View
    private lateinit var cameraButton: Button
    private lateinit var modePortrait: Button
    private lateinit var modeLandscape: Button
    private lateinit var layoutButton: Button
    private var selectedCategory: String? = null
    private lateinit var tabs: LinearLayout
    private var manual = false
    private var guidesEnabled = true
    private var bodyEnabled = false
    private val gyroFilter = GyroSmoother()
    private var mode = SceneMode.PORTRAIT
    private var composition = Layout.THIRDS
    private var mood = Mood.NATURAL
    private var dwell = 2500L
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
            dwell = savedInstanceState.getLong("dwell", 2500L)
            gate = SceneStability(dwell)
        }
        guidesEnabled = savedInstanceState?.getBoolean("guides", true) ?: true
        bodyEnabled = savedInstanceState?.getBoolean("body", false) ?: false
        buildScreen()
        showDemo()
        if (savedInstanceState?.getBoolean("camera") == true && hasPermission()) startCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("mode", mode.name); outState.putString("layout", composition.name)
        outState.putString("mood", mood.name); outState.putLong("dwell", dwell); outState.putBoolean("camera", !demo)
        outState.putBoolean("guides", guidesEnabled); outState.putBoolean("body", bodyEnabled)
        super.onSaveInstanceState(outState)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22), 0, dp(14), 0) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("观景", 23f, Color.rgb(244, 242, 237)).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        brand.addView(label("F R A M E C O A C H", 8f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("使用说明") { showKnowledge() }, LinearLayout.LayoutParams(dp(82), dp(48)))
        header.addView(button("设置") { showSettings() }.apply { id = R.id.settings }, LinearLayout.LayoutParams(dp(52), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(66)))

        val modes = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        modePortrait = button("人像") { changeMode(SceneMode.PORTRAIT) }
        modeLandscape = button("风光") { changeMode(SceneMode.LANDSCAPE) }
        modes.addView(modePortrait, LinearLayout.LayoutParams(dp(70), dp(44)))
        modes.addView(modeLandscape, LinearLayout.LayoutParams(dp(70), dp(44)))
        modes.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        layoutButton = button(composition.label + "  ▾") { chooseLayout() }
        modes.addView(layoutButton, LinearLayout.LayoutParams(dp(110), dp(44)))
        root.addView(modes, LinearLayout.LayoutParams(-1, dp(52)))

        val stage = FrameLayout(this).apply {
            background = rounded(Color.BLACK, 22); clipToOutline = true; minimumHeight = dp(100)
        }
        preview = PreviewView(this).apply { id = R.id.camera_preview }
        demoView = DemoView(this)
        overlay = GuideOverlay(this).apply {
            showGuides = guidesEnabled; showBody = bodyEnabled
            onHorizon = { y -> horizon = y; if (ready) renderTips(); toast("已手动标记水平线") }
        }
        stage.addView(preview, match()); stage.addView(demoView, match()); stage.addView(overlay, match())
        badge = label("演示场景 · 非实时识别", 10f, Color.rgb(233, 230, 224)).apply {
            setPadding(dp(11), 0, dp(11), 0); background = rounded(Color.argb(180, 17, 19, 21), 16)
        }
        stage.addView(badge, FrameLayout.LayoutParams(-2, dp(30), Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        status = label("", 11f, Color.WHITE).apply {
            id = R.id.assist_status; gravity = Gravity.CENTER
            background = rounded(Color.argb(205, 17, 19, 21), 16); setPadding(dp(12), 0, dp(12), 0)
        }
        stage.addView(status, FrameLayout.LayoutParams(-1, dp(36), Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(12)) })

        val advice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel, 22); setPadding(dp(16), dp(3), dp(12), dp(6))
        }
        val titleRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label("拍摄向导", 12f, muted), LinearLayout.LayoutParams(0, dp(40), 1f))
        titleRow.addView(button("立即辅助  ↗") { assistNow() }.apply { id = R.id.assist_now; setTextColor(lime) }, LinearLayout.LayoutParams(dp(100), dp(48)))
        advice.addView(titleRow)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(lime)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(54, 57, 60))
        }
        advice.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabs = row().apply { gravity = Gravity.CENTER_VERTICAL }
        horizontal.addView(tabs); advice.addView(horizontal, LinearLayout.LayoutParams(-1, dp(38)))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        tipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), dp(4), dp(6)) }
        scroll.addView(tipList); advice.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val middle = row()
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            middle.addView(stage, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
            middle.addView(advice, LinearLayout.LayoutParams(dp(304), -1).apply { setMargins(0, 0, dp(12), 0) })
            root.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(dp(12), 0, dp(12), dp(10)) })
            root.addView(advice, LinearLayout.LayoutParams(-1, dp(198)).apply { setMargins(dp(12), 0, dp(12), 0) })
        }

        val footer = row().apply { gravity = Gravity.CENTER; setPadding(dp(14), dp(6), dp(14), dp(6)) }
        cameraButton = button("开启相机") { if (demo) requestCamera() else showDemo() }
        footer.addView(cameraButton, LinearLayout.LayoutParams(0, dp(60), 1f))
        shutter = ShutterButton(this).apply { id = R.id.shutter; setOnClickListener { capture() } }
        footer.addView(shutter, LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(18), 0, dp(18), 0) })
        footer.addView(button("切换镜头") {
            if (demo) toast("开启相机后可切换前后镜头") else { camera?.flip(); resetAnalysis() }
        }, LinearLayout.LayoutParams(0, dp(60), 1f))
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(88)))
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
            onError = { message -> if (!demo) {
                ready = false; gate.reset(); horizon = null
                overlay.guidance = null; overlay.body = null; overlay.invalidate()
                progress.progress = 0; showWaiting(); status.text = message; toast(message)
            } }
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
            horizon = null; manual = false
            if (ready) { overlay.guidance = null; overlay.invalidate(); showWaiting() }
            ready = false
            status.text = "正在重新取景 · 停下后自动辅助"
        } else {
            ready = state.ready
            status.text = when {
                ready && manual -> if (demo) "演示 · 已手动请求辅助" else "已手动请求辅助 · 可继续调整取景"
                ready && demo -> "演示建议已就绪 · 开启相机试试"
                ready -> "辅助已就绪 · 轻微手抖不影响"
                else -> "轻松持稳 · 观察约 ${dwell / 1000f} 秒"
            }
        }
        if (state.triggered || (ready && SystemClock.elapsedRealtime() - lastTips > 2000)) renderTips()
    }

    private fun assistNow() {
        if (!gate.assistNow()) { toast("正在读取画面，请稍后重试"); return }
        ready = true; manual = true; progress.progress = 100
        status.text = if (demo) "演示 · 已手动请求辅助" else "已手动请求辅助 · 可继续调整取景"
        renderTips()
    }

    private fun renderTips() {
        if (!ready) return
        val guidance = Coach.advise(mode, composition, mood, observation, horizon)
        overlay.guidance = guidance; overlay.invalidate()
        lastTips = SystemClock.elapsedRealtime()
        val categories = guidance.tips.map { it.category }.distinct()
        if (selectedCategory !in categories) selectedCategory = categories.firstOrNull()
        tabs.removeAllViews()
        categories.forEach { category ->
            tabs.addView(button(category) { selectedCategory = category; renderTips() }.apply {
                textSize = 11f; setTextColor(if (selectedCategory == category) lime else muted)
                background = rounded(if (selectedCategory == category) Color.rgb(58, 51, 44) else panel, 10)
            }, LinearLayout.LayoutParams(dp(53), dp(36)).apply { marginEnd = dp(3) })
        }
        tipList.removeAllViews()
        guidance.tips.filter { it.category == selectedCategory }.forEach { tip ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; isClickable = true; isFocusable = true
                contentDescription = "${tip.basis.label}：${tip.title}。${tip.instruction}。点击查看依据"
                setOnClickListener { showTip(tip) }
            }
            card.addView(label(tip.title, 17f, Color.rgb(244, 242, 237)).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
            card.addView(label(tip.instruction, 12f, Color.rgb(191, 196, 202)).apply { setPadding(0, dp(6), 0, dp(5)); setLineSpacing(dp(2).toFloat(), 1f) })
            card.addView(label((if (demo) "演示数据 · " else "") + tip.basis.label + "  ·  点击查看依据 ↗", 10f, lime))
            tipList.addView(card)
        }
    }

    private fun resetAnalysis() {
        gate.reset(); ready = false; manual = false; selectedCategory = null; horizon = null; progress.progress = 0
        overlay.guidance = null; overlay.body = null; overlay.invalidate()
        observation = if (demo) demoObservation() else Observation(observation.light)
        status.text = "保持取景，等待 ${dwell / 1000} 秒"
        showWaiting()
    }
    private fun showWaiting() {
        tabs.removeAllViews(); tipList.removeAllViews()
        tabs.addView(label("  01  取景    /    02  观察    /    03  尝试", 10f, muted))
        tipList.addView(label("把注意力留给眼前。", 18f, Color.rgb(244, 242, 237)))
        tipList.addView(label(if (mode == SceneMode.PORTRAIT)
            "轻微手抖没关系。停留后给出建议，也可点「立即辅助」。" else
            "选定想留下的景物。辅助后可点击画面，手动标记水平线。", 12f, muted).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(dp(3).toFloat(), 1f) })
    }

    private fun changeMode(value: SceneMode) {
        mode = value; camera?.portrait = mode == SceneMode.PORTRAIT
        demoView.portrait = mode == SceneMode.PORTRAIT; demoView.invalidate()
        updateModeUi(); resetAnalysis()
    }
    private fun updateModeUi() {
        modePortrait.setTextColor(if (mode == SceneMode.PORTRAIT) lime else muted)
        modePortrait.background = rounded(if (mode == SceneMode.PORTRAIT) panel else ink)
        modeLandscape.setTextColor(if (mode == SceneMode.LANDSCAPE) lime else muted)
        modeLandscape.background = rounded(if (mode == SceneMode.LANDSCAPE) panel else ink)
        overlay.landscape = mode == SceneMode.LANDSCAPE; overlay.layout = composition
    }
    private fun chooseLayout() {
        AlertDialog.Builder(this).setTitle("选择你想尝试的构图")
            .setSingleChoiceItems(Layout.entries.map { it.label }.toTypedArray(), composition.ordinal) { dialog, which ->
                composition = Layout.entries[which]; layoutButton.text = composition.label + "  ▾"
                overlay.layout = composition; overlay.invalidate(); if (ready) renderTips(); dialog.dismiss()
            }.show()
    }
    private fun moodLabel() = when (mood) { Mood.NATURAL -> "自然"; Mood.QUIET -> "安静"; Mood.LIVELY -> "轻快" }
    private fun chooseMood() {
        AlertDialog.Builder(this).setTitle("想拍出什么感觉？")
            .setSingleChoiceItems(arrayOf("自然", "安静", "轻快"), mood.ordinal) { dialog, which ->
                mood = Mood.entries[which]; if (ready) renderTips(); dialog.dismiss()
            }.show()
    }
    private fun showSettings() {
        val options = arrayOf(
            "停留时间：${dwell / 1000f} 秒",
            "辅助线：${if (guidesEnabled) "开" else "关"}",
            "人体关键点：${if (bodyEnabled) "开" else "关"}",
            "人像氛围：${moodLabel()}",
            "重新观察当前画面"
        )
        AlertDialog.Builder(this).setTitle("取景设置").setItems(options) { _, which ->
            when (which) {
                0 -> AlertDialog.Builder(this).setTitle("停留多久开始辅助？")
                    .setItems(arrayOf("2 秒", "2.5 秒（默认）", "3 秒")) { _, index ->
                        dwell = longArrayOf(2000, 2500, 3000)[index]; gate = SceneStability(dwell); resetAnalysis()
                    }.show()
                1 -> { guidesEnabled = !guidesEnabled; overlay.showGuides = guidesEnabled; overlay.invalidate() }
                2 -> { bodyEnabled = !bodyEnabled; overlay.showBody = bodyEnabled; overlay.invalidate() }
                3 -> chooseMood()
                4 -> resetAnalysis()
            }
        }.setNegativeButton("完成", null).show()
    }

    private fun showTip(tip: Tip) {
        AlertDialog.Builder(this).setTitle(tip.title)
            .setMessage("${tip.basis.label}\n\n${tip.instruction}\n\n依据与限制\n${tip.evidence}\n\n${tip.sourceIds.mapNotNull { Knowledge.find(it)?.title }.joinToString("\n")}")
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
        AlertDialog.Builder(this).setTitle("观景 · 0.2 试用版")
            .setMessage("稳定取景 → 观察画面 → 尝试建议 → 保存照片\n\n实时帧在本机处理，应用代码不上传照片。不识别身份，不对容貌或情绪打分。只保存你主动拍摄的照片，辅助线不会写进原片。ML Kit 自身的数据实践请见项目隐私说明。\n\n风光水平线由你手动标记；单帧亮度无法确定太阳方向。复杂人群、暗光、遮挡和动态背景会影响检测。\n\n当前支持手机相机，不支持连接单反或微单的实时取景。公开课程和书目用于知识整理，并未读完你提到的所有书籍，也未用获奖作品训练模型。")
            .setPositiveButton("查看知识来源") { _, _ -> chooseSource(Knowledge.sources) }
            .setNeutralButton("辅助逻辑") { _, _ ->
                AlertDialog.Builder(this).setTitle("这些建议是怎样来的？")
                    .setMessage("① 观察取景约 2.5 秒，容忍轻微手抖；确认持续移动才重新计时。也可点击「立即辅助」。\n\n② 在本机测量预览亮度、人脸位置、头部转角和人体关键点。关键点只描述位置，并不理解动作是否好看。\n\n③ 结合你选的构图和氛围，用规则生成建议。画面测量代表有检测依据；创作建议是可尝试的模板。没有人脸时不画目标圈。\n\n④ 风光尚未自动识别天空、道路、山峰，水平线需手动标记。表情来自你的氛围选择，不是情绪识别。\n\n当前没有专门训练摄影审美模型，也未把书籍或获奖照片训练进模型。实时照片不用于在线学习。")
                    .setPositiveButton("明白了", null).show()
            }
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
        resetAnalysis(); gyroFilter.reset(); gyroSpeed = 0f; gyroAt = 0L
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        handler.post(demoTick)
    }
    override fun onPause() {
        started = false; handler.removeCallbacks(demoTick); sensors.unregisterListener(this)
        resetAnalysis(); super.onPause()
    }
    override fun onDestroy() { camera?.close(); camera = null; super.onDestroy() }
    override fun onSensorChanged(event: SensorEvent) {
        gyroAt = SystemClock.elapsedRealtime()
        gyroSpeed = gyroFilter.update(event.values[0], event.values[1], event.values[2], gyroAt)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun demoObservation(): Observation {
        if (mode == SceneMode.LANDSCAPE) return Observation(Light(.5f, .1f, .02f))
        val scale = if (demoView.width > 0) minOf(demoView.width, demoView.height).toFloat() / demoView.width else 1f
        return Observation(Light(.5f, .1f, .02f), listOf(Face(Point(.43f, .34f), .08f * scale, .10f, 0f, null)), Body(mapOf(
            Joint.LEFT_SHOULDER to Point(.385f, .42f), Joint.RIGHT_SHOULDER to Point(.48f, .42f),
            Joint.LEFT_ELBOW to Point(.365f, .53f), Joint.RIGHT_ELBOW to Point(.5f, .51f),
            Joint.LEFT_WRIST to Point(.35f, .63f), Joint.RIGHT_WRIST to Point(.51f, .61f),
            Joint.LEFT_HIP to Point(.405f, .61f), Joint.RIGHT_HIP to Point(.455f, .61f),
            Joint.LEFT_KNEE to Point(.397f, .73f), Joint.RIGHT_KNEE to Point(.475f, .73f),
            Joint.LEFT_ANKLE to Point(.39f, .84f), Joint.RIGHT_ANKLE to Point(.49f, .83f)
        ).mapValues { (_, point) -> point.copy(x = .43f + (point.x - .43f) * scale) }))
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(value: String, action: (View) -> Unit) = Button(this).apply {
        text = value; textSize = 12f; isAllCaps = false; setTextColor(muted)
        stateListAnimator = null; elevation = 0f
        background = rounded(ink); setPadding(dp(5), 0, dp(5), 0); minimumWidth = 0; minWidth = 0
        setOnClickListener(action)
    }
    private fun rounded(color: Int, radius: Int = 12) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
