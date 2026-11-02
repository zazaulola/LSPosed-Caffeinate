package io.github.zazaulola.caffeinate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/** Экран настроек: режим, пресеты, состояние модуля, запуск/остановка. */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_CYCLE = "cycle"
        private const val REQ_NOTIFICATIONS = 1
    }

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var next: Button
    private lateinit var modes: RadioGroup
    private lateinit var modeHint: TextView
    private lateinit var presetsContainer: LinearLayout
    private lateinit var moduleStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderStatus()
            if (CaffeinateState.active && !CaffeinateState.isInfinite) handler.postDelayed(this, 500)
        }
    }
    private val stateListener: () -> Unit = { restartTicker(); renderPresets() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        next = findViewById(R.id.next)
        modes = findViewById(R.id.modes)
        modeHint = findViewById(R.id.mode_hint)
        presetsContainer = findViewById(R.id.presets)
        moduleStatus = findViewById(R.id.module_status)

        toggle.setOnClickListener {
            if (CaffeinateState.active) Controller.stop(this)
            else if (Prefs.presets(this).isNotEmpty()) Controller.startPreset(this, 0)
        }
        next.setOnClickListener { Controller.cycle(this) }

        modes.check(radioIdFor(Prefs.mode(this)))
        modes.setOnCheckedChangeListener { _, id ->
            val mode = modeFor(id)
            Prefs.setMode(this, mode)
            renderMode()
            Controller.applyMode(this)
        }

        findViewById<Button>(R.id.add_preset).setOnClickListener { showAddPresetDialog() }
        findViewById<Button>(R.id.reset_presets).setOnClickListener {
            Prefs.resetPresets(this)
            renderPresets()
        }

        val addTile = findViewById<Button>(R.id.add_tile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTile.setOnClickListener { requestAddTile() }
        } else {
            addTile.visibility = View.GONE
        }

        requestNotificationPermission()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CYCLE, false) == true) {
            intent.removeExtra(EXTRA_CYCLE)
            Controller.cycle(this)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        CaffeinateState.addListener(stateListener)
        renderMode()
        renderPresets()
        moduleStatus.setText(if (ModuleStatus.isActive()) R.string.module_active else R.string.module_inactive)
        restartTicker()
    }

    override fun onPause() {
        CaffeinateState.removeListener(stateListener)
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun restartTicker() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun renderStatus() {
        val st = CaffeinateState
        val presets = Prefs.presets(this)
        if (st.active) {
            val mode = getString(st.mode.titleRes)
            status.text = if (st.isInfinite) getString(R.string.status_on_infinite, mode)
            else getString(R.string.status_on, mode, TimeFormat.remaining(st.remainingMs()))
            toggle.setText(R.string.action_stop)
            toggle.isEnabled = true
        } else {
            status.setText(R.string.status_off)
            val first = presets.firstOrNull()
            toggle.text = if (first != null) getString(R.string.action_start_first, TimeFormat.preset(first))
            else getString(R.string.action_start)
            toggle.isEnabled = first != null
        }
        next.isEnabled = presets.isNotEmpty()
    }

    private fun renderMode() {
        modeHint.setText(Prefs.mode(this).hintRes)
    }

    private fun renderPresets() {
        presetsContainer.removeAllViews()
        val presets = Prefs.presets(this)
        if (presets.isEmpty()) {
            presetsContainer.addView(TextView(this).apply { setText(R.string.presets_empty) })
            renderStatus()
            return
        }
        presets.forEachIndexed { index, minutes ->
            presetsContainer.addView(presetRow(index, minutes, presets))
        }
        renderStatus()
    }

    private fun presetRow(index: Int, minutes: Int, presets: List<Int>): View {
        val running = CaffeinateState.active && CaffeinateState.presetIndex == index
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }
        val label = TextView(this).apply {
            text = TimeFormat.preset(minutes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            if (running) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val start = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            setText(R.string.action_start)
            setOnClickListener { Controller.startPreset(this@MainActivity, index) }
        }
        row.addView(start)

        val delete = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            setText(R.string.action_delete)
            setOnClickListener {
                val updated = presets.toMutableList().also { it.removeAt(index) }
                Prefs.setPresets(this@MainActivity, updated)
                renderPresets()
            }
        }
        row.addView(delete)
        return row
    }

    private fun showAddPresetDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            hint = getString(R.string.preset_dialog_hint)
            isSingleLine = true
        }
        val frame = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.preset_dialog_title)
            .setView(frame)
            .setPositiveButton(R.string.ok) { _, _ ->
                val minutes = TimeFormat.parsePreset(input.text.toString())
                val presets = Prefs.presets(this)
                when {
                    minutes == null -> toast(R.string.preset_invalid)
                    minutes in presets -> toast(R.string.preset_exists)
                    else -> {
                        Prefs.setPresets(this, presets + minutes)
                        renderPresets()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        input.requestFocus()
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val sbm = getSystemService(StatusBarManager::class.java) ?: return
        sbm.requestAddTileService(
            ComponentName(this, CaffeinateTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun radioIdFor(mode: Mode) = when (mode) {
        Mode.SCREEN_ON -> R.id.mode_screen_on
        Mode.NO_LOCK -> R.id.mode_no_lock
        Mode.BOTH -> R.id.mode_both
    }

    private fun modeFor(id: Int) = when (id) {
        R.id.mode_no_lock -> Mode.NO_LOCK
        R.id.mode_both -> Mode.BOTH
        else -> Mode.SCREEN_ON
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
