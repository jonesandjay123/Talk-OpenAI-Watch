package com.example.talkopenaiwatch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.talkopenaiwatch.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 將版本號碼設置為 TextView
        val versionTextView = findViewById<TextView>(R.id.version_text)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        versionTextView.text = "版本 $versionName"

        // 為根視圖添加單擊監聽器
        binding.root.setOnClickListener { onScreenTapped(it) }

        textToSpeech = TextToSpeech(this, this)
    }

    private fun onScreenTapped(view: View) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出您的問題")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW") // 繁體中文
        }
        startActivityForResult(intent, 100)
    }

    // 實現 TextToSpeech.OnInitListener 中的 onInit 方法
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeech", "Language not supported")
            }
        } else {
            Log.e("TextToSpeech", "Initialization failed")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0]
                binding.text.text = recognizedText

                // 根據識別出的語言更新 TextToSpeech 的語言設置
                val locale = if (recognizedText.matches(Regex("[\\u4E00-\\u9FA5]+"))) {
                    Locale.TAIWAN
                } else {
                    Locale.US
                }
                textToSpeech.language = locale

                // 使用 postDelayed 來給語音識別一點時間完成處理
                binding.text.postDelayed({
                    // 朗讀文字
                    textToSpeech.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, null, "")
                }, 500) // 延遲 500 毫秒
            }
        }
    }

    override fun onDestroy() {
        // 釋放資源
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

        super.onDestroy()
    }
}