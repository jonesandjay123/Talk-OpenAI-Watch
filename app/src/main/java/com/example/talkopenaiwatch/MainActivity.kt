package com.example.talkopenaiwatch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.talkopenaiwatch.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.util.Properties

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private var maxTokens = 75
    private var isPlaying = false // 新增此變量

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

        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val tokenMaxText = findViewById<TextView>(R.id.token_max_text)
        seekBar.max = 190 // 最大值 - 最小值
        seekBar.progress = 50 // 初始值 - 最小值
        tokenMaxText.text = "token上限: ${seekBar.progress + 10}" // 初始顯示

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val selectedValue = ((progress + 10) / 5) * 5 // 最小值 + progress，並確保它是5的倍數
                tokenMaxText.text = "token上限: $selectedValue" // 更新顯示
                maxTokens = selectedValue // 調整maxTokens的值
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 可選操作：當用戶開始拖動 SeekBar 時觸發
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // 可選操作：當用戶停止拖動 SeekBar 時觸發
            }
        })

        val versionTextView = findViewById<TextView>(R.id.version_text)
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        versionTextView.text = "Version $versionName"

        // 修改按鈕的點擊事件
        binding.buttonAsk.setOnClickListener {
            if (isPlaying) {
                onStopSpeaking()
            } else {
                onAskButtonClicked()
            }
        }
    }

    private fun onAskButtonClicked() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出您的問題")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
        }
        startActivityForResult(intent, 100)
    }

    // 新增 onStopSpeaking() 方法
    private fun onStopSpeaking() {
        textToSpeech.stop()
        binding.buttonAsk.text = "點擊開始提問"
        isPlaying = false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeech", "Language not supported")
            }
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        binding.buttonAsk.text = "停止回答"
                        isPlaying = true
                    }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        binding.buttonAsk.text = "點擊開始提問"
                        isPlaying = false
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        binding.buttonAsk.text = "點擊開始提問"
                        isPlaying = false
                    }
                }
            })
        } else {
            Log.e("TextToSpeech", "Initialization failed")
        }
    }

    @OptIn(BetaOpenAI::class)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognizedText = results[0]

                binding.text.text = "問：$recognizedText"

                val answerTextView = findViewById<TextView>(R.id.text_answer)
                val startTime = System.currentTimeMillis()
                val timer = object : CountDownTimer(60000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val elapsedTime = System.currentTimeMillis() - startTime
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime)
                        answerTextView.text = "等待回答：$seconds 秒"
                    }
                    override fun onFinish() {
                        answerTextView.text = "等待時間已超過一分鐘..."
                    }
                }
                timer.start()

                val locale = when {
                    recognizedText.matches(Regex("[\\u4E00-\\u9FA5]+")) -> Locale.TAIWAN
                    else -> Locale.US
                }
                textToSpeech.language = locale

                val apiKey = readApiSecrets()
                val openAI = OpenAI(apiKey)

                CoroutineScope(Dispatchers.Main).launch {
                    val systemMessage = if (locale == Locale.TAIWAN) "你是一個用字精簡的智能問答助手，嚴格的遵守規則字數限制規則，每次最多僅用${maxTokens}個字來簡短回答所有提問。" else "You are an intelligent Q&A assistant that strictly follows rules, answering all questions concisely using only $maxTokens words each time."

                    val chatMessages = listOf(
                        ChatMessage(role = ChatRole.System, content = systemMessage),
                        ChatMessage(role = ChatRole.User, content = recognizedText)
                    )

                    val chatCompletionRequest = ChatCompletionRequest(
                        model = ModelId("gpt-4"),
                        messages = chatMessages,
                        maxTokens = maxTokens
                    )
                    val completion = openAI.chatCompletion(chatCompletionRequest)
                    val answer = completion.choices.firstOrNull()?.message?.content?.trim()

                    if (answer != null) {
                        val answerTextView = findViewById<TextView>(R.id.text_answer)
                        answerTextView.text = "答: $answer"
                        timer.cancel()
                        textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
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
