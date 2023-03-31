package com.example.talkopenaiwatch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.TextView
import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.talkopenaiwatch.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Properties

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech

    private fun readApiSecrets(): String {
        return try {
            assets.open("secrets.properties").use { inputStream ->
                val properties = Properties().apply {
                    load(inputStream)
                }
                properties.getProperty("openai_api_key")
                    ?: throw IllegalStateException("Failed to find 'openai_api_key' in secrets.properties")
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read secrets.properties", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 textToSpeech
        textToSpeech = TextToSpeech(this, this)

        val versionTextView = findViewById<TextView>(R.id.version_text)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        versionTextView.text = "版本 $versionName"

        binding.root.setOnClickListener { onScreenTapped(it) }
    }

    private fun onScreenTapped(view: View) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出您的問題")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
        }
        startActivityForResult(intent, 100)
    }

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

                binding.text.text = "問：$recognizedText"

                val locale = when {
                    recognizedText.matches(Regex("[\\u4E00-\\u9FA5]+")) -> Locale.TAIWAN
                    else -> Locale.US
                }
                textToSpeech.language = locale

                val apiKey = readApiSecrets()
                val openAI = OpenAI(apiKey)

                CoroutineScope(Dispatchers.Main).launch {
                    val completionRequest = CompletionRequest(
                        model = ModelId("text-davinci-003"),
                        prompt = recognizedText,
                        maxTokens = 50
                    )
                    val completion = openAI.completion(completionRequest)
                    val answer = completion.choices.firstOrNull()?.text?.trim()

                    if (answer != null) {
                        val answerTextView = findViewById<TextView>(R.id.text_answer)
                        answerTextView.text = "答: $answer"

                        binding.text.postDelayed({
                            textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null)
                        }, 1000)
                    } else {
                        binding.textAnswer.text = "抱歉，我無法回答您的問題。"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        super.onDestroy()
    }
}
