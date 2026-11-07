package com.framecoach.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.framecoach.core.*

class PoseLibraryDialog(
    private val context: Context,
    private var conditions: PoseConditions,
    private var selectedId: String?,
    private val favorites: MutableSet<String>,
    private val onConditions: (PoseConditions) -> Unit,
    private val onChoose: (PoseCard) -> Unit,
    private val onClear: () -> Unit,
    private val onFavorites: (Set<String>) -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private lateinit var cards: LinearLayout
    private lateinit var count: TextView
    private lateinit var dialog: AlertDialog
    private var onlyFavorites = false
    private val accent = Color.rgb(235, 198, 151)
    private fun dp(value: Int) = (value * density).toInt()
    private fun text(value: String, size: Float = 14f) = TextView(context).apply { text = value; textSize = size; setTextColor(Color.rgb(231, 233, 235)); setPadding(0, dp(4), 0, dp(4)) }

    fun show() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(4), dp(18), dp(4)) }
        root.addView(text("原创线稿 · 手臂对照试用。动作以舒适为准。", 12f))
        val filters = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun chooser(labels: List<String>, selected: Int, changed: (Int) -> Unit): Spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
            setSelection(selected)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { changed(position) }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        filters.addView(chooser(Posture.entries.map { it.label }, conditions.posture.ordinal) { update(conditions.copy(posture = Posture.entries[it])) }.apply { id = R.id.pose_posture }, LinearLayout.LayoutParams(0, dp(48), 1f))
        filters.addView(chooser(Framing.entries.map { it.label }, conditions.framing.ordinal) { update(conditions.copy(framing = Framing.entries[it])) }.apply { id = R.id.pose_framing }, LinearLayout.LayoutParams(0, dp(48), 1f))
        filters.addView(chooser(PoseView.entries.map { it.label }, conditions.view.ordinal) { update(conditions.copy(view = PoseView.entries[it])) }.apply { id = R.id.pose_view }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(filters)
        val props = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        props.addView(CheckBox(context).apply { id = R.id.pose_chair; text = "有稳固座椅"; isChecked = conditions.chair; setOnCheckedChangeListener { _, value -> update(conditions.copy(chair = value)) } })
        props.addView(CheckBox(context).apply { id = R.id.pose_wall; text = "有可轻靠墙面"; isChecked = conditions.wall; setOnCheckedChangeListener { _, value -> update(conditions.copy(wall = value)) } })
        root.addView(props)
        root.addView(CheckBox(context).apply { id = R.id.pose_favorites; text = "只看收藏"; setOnCheckedChangeListener { _, value -> onlyFavorites = value; render() } })
        count = text("").apply { id = R.id.pose_count }; root.addView(count)
        cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(cards) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog = AlertDialog.Builder(context).setTitle("姿势库 · ${PoseLibrary.cards.size} 个原创动作")
            .setView(root).setPositiveButton("返回取景", null)
            .setNeutralButton("清除目标") { _, _ -> onClear() }.create()
        dialog.setOnShowListener {
            dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * .96f).toInt(), (context.resources.displayMetrics.heightPixels * .88f).toInt())
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).id = R.id.pose_clear
        }
        render(); dialog.show()
    }

    private fun update(value: PoseConditions) {
        if (conditions == value) return
        conditions = value; onConditions(value)
        if (PoseLibrary.find(selectedId)?.eligible(value) == false) selectedId = null
        if (::cards.isInitialized) render()
    }
    private fun render() {
        val filtered = PoseLibrary.filtered(conditions, favorites, onlyFavorites)
        count.text = "符合条件 ${filtered.size} / 40 · 已锁定：${PoseLibrary.find(selectedId)?.title ?: "未选择"}"
        cards.removeAllViews()
        if (filtered.isEmpty()) {
            cards.addView(text(when {
                conditions.posture == Posture.SITTING && !conditions.chair -> "坐姿需要稳固座椅。确认有座椅后勾选上方选项。"
                conditions.posture == Posture.LEANING && !conditions.wall -> "靠姿需要可轻靠的墙面。确认条件后勾选上方选项。"
                onlyFavorites -> "当前条件下还没有收藏，取消「只看收藏」浏览。"
                else -> "当前条件没有适合的动作，请换一个条件。"
            }))
        }
        filtered.forEach { card ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            row.addView(PoseSketchView(context, card, conditions.view, conditions.framing), LinearLayout.LayoutParams(dp(84), dp(116)))
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(text(card.title, 16f)); info.addView(text(card.instruction, 12f))
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(context).apply {
                text = "查看与选择"; textSize = 12f; setTextColor(accent)
                contentDescription = "查看姿势：${card.id}"
                setOnClickListener {
                    val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
                    content.addView(PoseSketchView(context, card, conditions.view, conditions.framing), LinearLayout.LayoutParams(-1, dp(200)))
                    content.addView(text("动作：${card.instruction}\n构图：${card.composition}\n目光与表达：${card.expression(conditions.mood)}\n光线：${card.light}\n\n原创动作示意，未经真机标定；${if (card.matchingEnabled) "只对照可见手臂，不评价整张照片。" else "动态或遮挡动作仅展示参考。"}", 13f))
                    AlertDialog.Builder(context).setTitle(card.title).setView(ScrollView(context).apply { addView(content) })
                        .setPositiveButton("选为本次目标") { _, _ -> selectedId = card.id; onChoose(card); dialog.dismiss() }
                        .setNegativeButton("返回", null).show().getButton(AlertDialog.BUTTON_POSITIVE).id = R.id.pose_lock
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(Button(context).apply {
                text = if (card.id in favorites) "已收藏" else "收藏"; textSize = 12f
                contentDescription = "${card.title}，${text}"
                setOnClickListener { if (!favorites.add(card.id)) favorites.remove(card.id); onFavorites(favorites.toSet()); render() }
            }, LinearLayout.LayoutParams(dp(75), dp(48)))
            info.addView(actions); row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); cards.addView(row)
        }
    }
}
