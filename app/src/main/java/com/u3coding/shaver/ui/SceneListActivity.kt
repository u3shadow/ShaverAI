package com.u3coding.shaver.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.u3coding.shaver.R
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.action.rule.WifiAliasRepo
import com.u3coding.shaver.device.WifiProvider

class SceneListActivity : AppCompatActivity() {
    private lateinit var sceneListContainer: LinearLayout
    private lateinit var tvEmptyScenes: TextView
    private lateinit var wifiProvider: WifiProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_list)

        RuleRepo.init(applicationContext)
        wifiProvider = WifiProvider(this)
        sceneListContainer = findViewById(R.id.sceneListContainer)
        tvEmptyScenes = findViewById(R.id.tvEmptyScenes)

        findViewById<Button>(R.id.btnAddCurrentWifi).setOnClickListener {
            val ssid = wifiProvider.getCurrentWifiSsid()
            if (ssid.isNullOrBlank()) {
                Toast.makeText(this, "\u672a\u8bfb\u53d6\u5230\u5f53\u524d Wi-Fi", Toast.LENGTH_SHORT).show()
            } else {
                openConfig(ssid)
            }
        }

        findViewById<Button>(R.id.btnAddManualWifi).setOnClickListener {
            showManualAddDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        renderScenes()
    }

    private fun renderScenes() {
        sceneListContainer.removeAllViews()
        val scenes = RuleRepo.getAllSceneSsids()
        tvEmptyScenes.visibility = if (scenes.isEmpty()) View.VISIBLE else View.GONE
        scenes.forEach { ssid ->
            sceneListContainer.addView(createSceneCard(ssid))
        }
    }

    private fun createSceneCard(ssid: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = getDrawable(R.drawable.bg_scene_card)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, dp(12))
            layoutParams = params
            setOnClickListener { openConfig(ssid) }
        }
        val alias = WifiAliasRepo.findAliasBySsid(ssid)
        val title = TextView(this).apply {
            text = alias ?: ssid
            setTextColor(0xFF113541.toInt())
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val detail = TextView(this).apply {
            text = "Wi-Fi \u540d\u79f0\uff1a$ssid\n${buildSceneSummary(ssid)}"
            setTextColor(0xFF4D6F7C.toInt())
            textSize = 13f
        }
        card.addView(title)
        card.addView(detail)
        return card
    }

    private fun buildSceneSummary(ssid: String): String {
        val enterCount = RuleRepo.getRules(ssid, com.u3coding.shaver.action.rule.RuleEventType.WIFI_CONNECTED).size
        val leaveCount = RuleRepo.getRules(ssid, com.u3coding.shaver.action.rule.RuleEventType.WIFI_DISCONNECTED).size
        return "\u8fdb\u5165\u914d\u7f6e $enterCount \u9879  |  \u79bb\u5f00\u914d\u7f6e $leaveCount \u9879"
    }

    private fun showManualAddDialog() {
        val input = EditText(this).apply {
            hint = "\u8bf7\u8f93\u5165 Wi-Fi \u540d\u79f0"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("\u624b\u52a8\u6dfb\u52a0 Wi-Fi \u573a\u666f")
            .setView(input)
            .setPositiveButton("\u786e\u5b9a") { _, _ ->
                val ssid = input.text?.toString()?.trim().orEmpty()
                if (ssid.isBlank()) {
                    Toast.makeText(this, "\u8bf7\u8f93\u5165 Wi-Fi \u540d\u79f0", Toast.LENGTH_SHORT).show()
                } else {
                    openConfig(ssid)
                }
            }
            .setNegativeButton("\u53d6\u6d88", null)
            .show()
    }

    private fun openConfig(ssid: String) {
        startActivity(
            Intent(this, SceneConfigActivity::class.java).putExtra(SceneConfigActivity.EXTRA_SSID, ssid)
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
