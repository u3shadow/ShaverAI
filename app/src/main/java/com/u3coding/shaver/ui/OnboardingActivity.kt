package com.u3coding.shaver.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.u3coding.shaver.R

class OnboardingActivity : AppCompatActivity() {
    private lateinit var illustrationView: OnboardingIllustrationView
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var exampleView: TextView
    private lateinit var dotContainer: LinearLayout
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private var pageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        illustrationView = findViewById(R.id.guideIllustration)
        titleView = findViewById(R.id.tvGuideTitle)
        descView = findViewById(R.id.tvGuideDesc)
        exampleView = findViewById(R.id.tvGuideExample)
        dotContainer = findViewById(R.id.dotContainer)
        prevButton = findViewById(R.id.btnPrevGuide)
        nextButton = findViewById(R.id.btnStartUsing)

        findViewById<Button>(R.id.btnSkipGuide).setOnClickListener {
            markGuideSeen(this)
            finish()
        }
        prevButton.setOnClickListener {
            if (pageIndex > 0) {
                pageIndex -= 1
                renderPage()
            }
        }
        nextButton.setOnClickListener {
            if (pageIndex == pages.lastIndex) {
                markGuideSeen(this)
                finish()
            } else {
                pageIndex += 1
                renderPage()
            }
        }

        renderPage()
    }

    private fun renderPage() {
        val page = pages[pageIndex]
        illustrationView.pageIndex = pageIndex
        titleView.text = page.title
        descView.text = page.description
        exampleView.text = page.example
        prevButton.visibility = if (pageIndex == 0) View.INVISIBLE else View.VISIBLE
        nextButton.text = if (pageIndex == pages.lastIndex) {
            "\u5f00\u59cb\u4f7f\u7528"
        } else {
            "\u4e0b\u4e00\u9875"
        }
        renderDots()
    }

    private fun renderDots() {
        dotContainer.removeAllViews()
        pages.forEachIndexed { index, _ ->
            dotContainer.addView(
                View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (index == pageIndex) Color.rgb(98, 245, 154) else Color.argb(90, 205, 235, 255))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(if (index == pageIndex) 18 else 8), dp(8)).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }
                }
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class GuidePage(
        val title: String,
        val description: String,
        val example: String
    )

    companion object {
        private const val PREF_NAME = "onboarding_state"
        private const val KEY_GUIDE_SEEN = "guide_seen"
        private val pages = listOf(
            GuidePage(
                title = "\u50cf\u804a\u5929\u4e00\u6837\u8bbe\u7f6e",
                description = "\u544a\u8bc9 AI \u4f60\u60f3\u5728\u54ea\u4e2a Wi-Fi \u573a\u666f\u4e0b\u6539\u4ec0\u4e48\u3002",
                example = "\u201c\u8fdb\u5165\u5bb6\u65f6\uff0c\u4eae\u5ea6\u8c03\u5230 80%\u201d"
            ),
            GuidePage(
                title = "\u5148\u7ed9 Wi-Fi \u53d6\u4e2a\u5c0f\u540d",
                description = "\u628a\u590d\u6742\u7684 Wi-Fi \u540d\u79f0\u53d8\u6210\u201c\u5bb6\u201d\u201c\u516c\u53f8\u201d\uff0c\u4ee5\u540e\u66f4\u597d\u8bf4\u3002",
                example = "\u201c\u628a\u5f53\u524d Wi-Fi \u53eb\u505a\u5bb6\u201d"
            ),
            GuidePage(
                title = "\u5c0f\u7ec4\u4ef6\uff0c\u4e00\u70b9\u5c31\u5e94\u7528",
                description = "\u684c\u9762\u70b9\u4e00\u4e0b\uff0c\u5feb\u901f\u68c0\u6d4b Wi-Fi \u5e76\u5e94\u7528\u5168\u5c40\u914d\u7f6e\u3002",
                example = "\u684c\u9762\u957f\u6309 > \u5c0f\u7ec4\u4ef6 > Shaver"
            ),
            GuidePage(
                title = "\u914d\u7f6e\u90fd\u80fd\u56de\u6765\u7ba1",
                description = "\u5728\u573a\u666f\u7ba1\u7406\u91cc\u67e5\u770b\u8fdb\u5165\u548c\u79bb\u5f00 Wi-Fi \u65f6\u4f1a\u53d1\u751f\u4ec0\u4e48\u3002",
                example = "\u4e3b\u9875 > \u573a\u666f\u7ba1\u7406"
            )
        )

        fun shouldShow(context: Context): Boolean {
            return !context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GUIDE_SEEN, false)
        }

        fun markGuideSeen(context: Context) {
            context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GUIDE_SEEN, true)
                .apply()
        }
    }
}
