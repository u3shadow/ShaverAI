package com.u3coding.shaver


import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel = ChatViewModel()
    private var input = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val text = findViewById<TextView>(R.id.tv_text)
        lifecycleScope.launch {
            viewModel.messages.collect { list ->
                text.text = list.joinToString("\n")
            }
        }
        val et = findViewById<EditText>(R.id.et_input)
        val btn = findViewById<Button>(R.id.btn_send)
        //给et加输入监听
        et.addTextChangedListener() {
            val str = it.toString()
            if (str.isNotBlank()) {
                input = str
            }
        }
        btn.setOnClickListener {
            if (input.isNotBlank()) {
                viewModel.sendMessage(input)
                et.text.clear()
                input = ""
            }
        }


    }
}
