package com.jarvis.ultimate

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.min

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var transcript: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private lateinit var orb: HudView
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var listening = false
    private val prefs by lazy { getSharedPreferences("jarvis_settings", MODE_PRIVATE) }
    private val notes = mutableListOf<String>()
    private val history = JSONArray()
    private val cyan = Color.rgb(65, 226, 255)
    private val bg = Color.rgb(3, 8, 17)
    private val panel = Color.rgb(7, 20, 34)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        notes.addAll(readNotes())
        tts = TextToSpeech(this, this)
        buildUi()
        addMessage("JARVIS online. Estou pronto. Pode falar comigo normalmente ou usar os comandos abaixo. Para conversa inteligente, configure uma API nas opções.", false)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("pt", "BR")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(8), dp(15), dp(10))
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        titleCol.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; textSize = 22f; letterSpacing = .18f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setTextColor(cyan)
        })
        titleCol.addView(TextView(this).apply { text = "PERSONAL INTELLIGENCE SYSTEM"; textSize = 9f; letterSpacing = .12f; setTextColor(Color.rgb(107, 164, 190)) })
        top.addView(titleCol)
        top.addView(TextView(this).apply {
            text = "⚙"; textSize = 27f; setTextColor(cyan); gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(4), 0)
            setOnClickListener { settingsDialog() }
            contentDescription = "Configurações"
        })
        root.addView(top)

        orb = HudView(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, dp(235)))
        statusText = TextView(this).apply {
            text = "● SISTEMA PRONTO  ·  VOZ PT-BR"; textSize = 10f; letterSpacing = .08f; setTextColor(cyan); gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(9))
        }
        root.addView(statusText)

        val miniStats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) }
        listOf("CORE / ACTIVE", "MEM / ${notes.size}", "ANDROID / READY").forEach { label ->
            miniStats.addView(TextView(this).apply { text = label; textSize = 8f; letterSpacing = .04f; gravity = Gravity.CENTER; setTextColor(Color.rgb(100, 178, 205)); setPadding(dp(5), dp(5), dp(5), dp(5)) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(miniStats)

        scroller = ScrollView(this).apply { isFillViewport = false; setBackgroundColor(panel); setPadding(dp(10), dp(10), dp(10), dp(10)) }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(transcript)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(4)) }
        listOf("AJUDA", "HORA", "ABRIR YOUTUBE", "MEMÓRIA").forEach { label ->
            quick.addView(Button(this).apply {
                text = label; textSize = 8f; isAllCaps = false; setTextColor(cyan); setBackgroundColor(Color.rgb(9, 29, 44)); minHeight = dp(37); setPadding(dp(3), 0, dp(3), 0)
                setOnClickListener { answer(when (label) { "AJUDA" -> "ajuda"; "HORA" -> "que horas são"; "ABRIR YOUTUBE" -> "abrir youtube"; else -> "memória" }) }
            }, LinearLayout.LayoutParams(0, dp(39), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(quick)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, 0) }
        input = EditText(this).apply {
            hint = "Fale ou digite para o JARVIS..."; setHintTextColor(Color.rgb(93, 129, 147)); setTextColor(Color.WHITE); textSize = 14f; singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(12), dp(7), dp(10), dp(7)); setBackgroundColor(Color.rgb(9, 25, 40))
            setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false }
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(49), 1f))
        row.addView(makeActionButton("➤") { sendInput() }, LinearLayout.LayoutParams(dp(48), dp(49)))
        row.addView(makeActionButton("🎙") { startListening() }, LinearLayout.LayoutParams(dp(48), dp(49)))
        root.addView(row)
        setContentView(root)
    }

    private fun makeActionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 17f; setTextColor(cyan); setBackgroundColor(Color.rgb(12, 39, 55)); setOnClickListener { action() }
    }

    private fun addMessage(text: String, user: Boolean) {
        val label = TextView(this).apply {
            this.text = (if (user) "VOCÊ  ›\n" else "JARVIS  ›\n") + text
            textSize = 13f; setTextColor(if (user) Color.rgb(224, 246, 255) else Color.rgb(156, 226, 244)); setPadding(dp(11), dp(9), dp(11), dp(9))
            setBackgroundColor(if (user) Color.rgb(13, 46, 64) else Color.rgb(9, 32, 47))
        }
        val width = if (user) (resources.displayMetrics.widthPixels * .84).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
        transcript.addView(label, LinearLayout.LayoutParams(width, -2).apply { gravity = if (user) Gravity.END else Gravity.START; bottomMargin = dp(8) })
        scroller.post { scroller.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendInput() {
        val text = input.text.toString().trim()
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        answer(text)
    }

    private fun answer(raw: String) {
        val s = raw.trim()
        if (s.isEmpty()) return
        addMessage(s, true)
        orb.mode = HudMode.THINKING
        val t = s.lowercase(Locale("pt", "BR"))
        val local = localAnswer(s, t)
        if (local != null) { respond(local); return }
        val key = prefs.getString("api_key", "") ?: ""
        if (key.isBlank()) {
            respond("Entendi. Para eu responder perguntas abertas como uma IA conversacional, toque em ⚙ e configure uma chave de API compatível. Sem essa conexão, posso executar comandos locais e abrir aplicativos.")
            return
        }
        statusText.text = "◌ CONECTANDO À INTELIGÊNCIA ONLINE..."
        thread {
            val response = try { askOnline(s, key) } catch (e: Exception) { "Não consegui conectar agora. Verifique a internet e as configurações da API. Detalhe: ${e.message ?: "conexão indisponível"}" }
            runOnUiThread { respond(response) }
        }
    }

    private fun localAnswer(s: String, t: String): String? {
        if (t in listOf("ajuda", "comandos", "o que você faz", "o que voce faz")) return "COMANDOS DISPONÍVEIS\n• Que horas são? / data de hoje\n• Abrir YouTube, Google, Instagram ou configurações\n• Pesquisar na internet: assunto\n• Calcular: 12*(4+2)\n• Lembrar: texto / memória / esquecer tudo\n• Fale normalmente: conecte uma API nas configurações para respostas inteligentes."
        if (t.contains("que horas") || t.contains("data de hoje") || t == "hora" || t == "data") return "Agora são ${SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())}, em ${SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date())}."
        if (t == "status" || t.contains("diagnóstico")) return "SISTEMA JARVIS\nInterface: operacional\nVoz: ${if (ttsReady) "pronta" else "inicializando"}\nMemória local: ${notes.size} nota(s)\nIA online: ${if ((prefs.getString("api_key", "") ?: "").isNotBlank()) "configurada" else "não configurada"}."
        if (t == "memória" || t == "memoria" || t == "minhas notas") return if (notes.isEmpty()) "A memória local ainda está vazia." else "MEMÓRIA LOCAL\n" + notes.mapIndexed { i, note -> "${i + 1}. $note" }.joinToString("\n")
        if (t.startsWith("lembrar:") || t.startsWith("lembre:") || t.startsWith("anota ")) {
            val note = if (t.startsWith("anota ")) s.substringAfter(" ").trim() else s.substringAfter(":").trim()
            if (note.isBlank()) return "Diga: lembrar: tenho prova de história amanhã."
            notes.add(note); saveNotes(); return "Certo. Salvei na memória local deste aparelho."
        }
        if (t == "esquecer tudo") { notes.clear(); saveNotes(); return "Apaguei as notas locais salvas." }
        if (t.startsWith("calcular ") || t.startsWith("quanto é ") || t.startsWith("quanto e ")) {
            val expr = s.substringAfter(" ").trim()
            return try { "Resultado: ${ExpressionParser(expr).parse()}" } catch (_: Exception) { "Não consegui interpretar essa conta. Exemplo: calcular (12+8)*3." }
        }
        if (t.startsWith("abrir ")) {
            val app = t.removePrefix("abrir ").trim()
            val pkg = when { app.contains("youtube") -> "com.google.android.youtube"; app.contains("instagram") -> "com.instagram.android"; app.contains("chrome") -> "com.android.chrome"; app.contains("whatsapp") -> "com.whatsapp"; app.contains("configura") -> "android.settings.SETTINGS"; app.contains("google") -> "com.google.android.googlequicksearchbox"; else -> "" }
            if (app.contains("configura")) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)); return "Abrindo as configurações do Android." }
            if (pkg.isNotBlank()) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) { startActivity(intent); return "Abrindo $app." }
                return "Não encontrei $app instalado neste aparelho."
            }
            return "Ainda não tenho um atalho para esse aplicativo. Posso abrir YouTube, Google, Instagram, Chrome, WhatsApp e configurações."
        }
        if (t.startsWith("pesquisar ") || t.startsWith("pesquisa ")) {
            val query = s.substringAfter(" ").trim()
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query))))
            return "Pesquisando na internet por: $query"
        }
        if (t in listOf("oi", "olá", "ola", "e aí", "eai", "bom dia", "boa tarde", "boa noite")) return "Olá! JARVIS online e pronto para ajudar. O que vamos fazer?"
        if (t.contains("quem é você") || t.contains("quem e voce")) return "Sou o JARVIS, seu assistente pessoal para Android. Posso ouvir sua voz, falar, guardar notas e executar alguns comandos. Para conversa inteligente ampla, configure uma API nas opções."
        return null
    }

    private fun respond(message: String) {
        addMessage(message, false)
        statusText.text = "● SISTEMA PRONTO  ·  VOZ PT-BR"
        orb.mode = HudMode.IDLE
        if (ttsReady && prefs.getBoolean("speak", true)) tts?.speak(message.take(1800), TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { respond("O reconhecimento de voz não está disponível neste Android."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41); return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusText.text = "◉ OUVINDO VOCÊ..."; orb.mode = HudMode.LISTENING; listening = true }
                override fun onBeginningOfSpeech() { }
                override fun onRmsChanged(rmsdB: Float) { orb.level = (rmsdB / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() { listening = false; statusText.text = "◌ INTERPRETANDO..."; orb.mode = HudMode.THINKING }
                override fun onError(error: Int) { listening = false; orb.mode = HudMode.IDLE; statusText.text = "● SISTEMA PRONTO  ·  VOZ PT-BR"; if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Toast.makeText(this@MainActivity, "Não consegui entender. Tente novamente.", Toast.LENGTH_SHORT).show() }
                override fun onResults(results: Bundle?) { listening = false; val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (!spoken.isNullOrBlank()) answer(spoken); else orb.mode = HudMode.IDLE }
                override fun onPartialResults(partialResults: Bundle?) { }
                override fun onEvent(eventType: Int, params: Bundle?) { }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Pode falar, estou ouvindo")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) { respond("Não consegui iniciar o microfone. Confira a permissão do app.") }
    }

    private fun settingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val keyInput = EditText(this).apply { hint = "Chave de API (não compartilhe)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("api_key", "")) }
        val endpointInput = EditText(this).apply { hint = "URL compatível com OpenAI"; setText(prefs.getString("endpoint", "https://openrouter.ai/api/v1/chat/completions")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val modelInput = EditText(this).apply { hint = "Nome do modelo"; setText(prefs.getString("model", "openai/gpt-oss-20b:free")) }
        val speakCheck = CheckBox(this).apply { text = "Responder falando"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean("speak", true) }
        layout.addView(TextView(this).apply { text = "Conversa inteligente (opcional)\nUse uma API compatível. Pode haver limites ou custos conforme o provedor. Sua chave fica salva localmente no aparelho."; setTextColor(Color.DKGRAY); textSize = 13f; setPadding(0, 0, 0, dp(10)) })
        layout.addView(endpointInput); layout.addView(modelInput); layout.addView(keyInput); layout.addView(speakCheck)
        AlertDialog.Builder(this).setTitle("JARVIS · CONFIGURAÇÕES").setView(layout)
            .setPositiveButton("SALVAR") { _, _ ->
                prefs.edit().putString("api_key", keyInput.text.toString().trim()).putString("endpoint", endpointInput.text.toString().trim()).putString("model", modelInput.text.toString().trim()).putBoolean("speak", speakCheck.isChecked).apply()
                Toast.makeText(this, "Configurações salvas neste aparelho.", Toast.LENGTH_SHORT).show()
            }.setNeutralButton("APAGAR CHAVE") { _, _ -> prefs.edit().remove("api_key").apply(); Toast.makeText(this, "Chave removida.", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("FECHAR", null).show()
    }

    private fun askOnline(userText: String, key: String): String {
        val endpoint = prefs.getString("endpoint", "https://openrouter.ai/api/v1/chat/completions")!!.trim()
        val model = prefs.getString("model", "openai/gpt-oss-20b:free")!!.trim()
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Authorization", "Bearer $key")
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "Você é JARVIS, um assistente pessoal para Android. Responda em português brasileiro natural, útil e conciso. Seja honesto sobre o que consegue executar no aparelho."))
        synchronized(history) { for (i in 0 until history.length()) messages.put(history.getJSONObject(i)) }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.7)
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw Exception("API ${connection.responseCode}: ${response.take(250)}")
        val answer = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "Não recebi uma resposta válida.")
        synchronized(history) { history.put(JSONObject().put("role", "user").put("content", userText)); history.put(JSONObject().put("role", "assistant").put("content", answer)); while (history.length() > 12) history.remove(0) }
        return answer
    }

    private fun readNotes(): List<String> = try { val a = JSONArray(prefs.getString("notes", "[]")); (0 until a.length()).map { a.getString(it) } } catch (_: Exception) { emptyList() }
    private fun saveNotes() { val a = JSONArray(); notes.forEach { a.put(it) }; prefs.edit().putString("notes", a.toString()).apply() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else if (requestCode == 41) Toast.makeText(this, "Permissão do microfone necessária para ouvir sua voz.", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy()
    }

    private enum class HudMode { IDLE, LISTENING, THINKING }

    private inner class HudView(context: Context) : View(context) {
        var mode = HudMode.IDLE; set(value) { field = value; invalidate() }
        var level = 0f; set(value) { field = value; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var tick = 0f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f; val r = min(width * .39f, height * .46f)
            tick += if (mode == HudMode.THINKING) 2.7f else if (mode == HudMode.LISTENING) 1.6f else .45f
            p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Color.rgb(19, 88, 119); p.alpha = 170
            for (i in 0..5) canvas.drawCircle(cx, cy, r * (.48f + i * .095f), p)
            p.color = cyan; p.alpha = 200; p.strokeWidth = 2f
            canvas.drawArc(RectF(cx-r*.93f, cy-r*.93f, cx+r*.93f, cy+r*.93f), tick, 78f, false, p)
            canvas.drawArc(RectF(cx-r*.80f, cy-r*.80f, cx+r*.80f, cy+r*.80f), -tick*1.4f, 112f, false, p)
            canvas.drawArc(RectF(cx-r*.67f, cy-r*.67f, cx+r*.67f, cy+r*.67f), tick*.8f+140f, 58f, false, p)
            p.strokeWidth = 1f; p.alpha = 140
            for (i in 0 until 72) {
                val a = Math.toRadians((i*5.0 + tick*.25).toDouble()); val inner = if (i%3==0) r*.77f else r*.81f; val outer = r*.86f
                canvas.drawLine(cx + kotlin.math.cos(a).toFloat()*inner, cy + kotlin.math.sin(a).toFloat()*inner, cx + kotlin.math.cos(a).toFloat()*outer, cy + kotlin.math.sin(a).toFloat()*outer, p)
            }
            p.style = Paint.Style.FILL; p.alpha = if (mode == HudMode.LISTENING) 60 + (level*70).toInt() else 40
            canvas.drawCircle(cx, cy, r*.43f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.alpha = 230
            canvas.drawCircle(cx, cy, r*.39f, p); canvas.drawCircle(cx, cy, r*.28f, p)
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); p.textSize = r*.24f; p.alpha = 255
            canvas.drawText("J", cx, cy + r*.09f, p)
            p.textSize = r*.075f; p.alpha = 220
            canvas.drawText(when(mode) { HudMode.IDLE -> "J.A.R.V.I.S  /  STANDBY"; HudMode.LISTENING -> "VOICE INPUT  /  LISTENING"; HudMode.THINKING -> "NEURAL CORE  /  PROCESSING" }, cx, cy+r*1.10f, p)
            p.textAlign = Paint.Align.LEFT
            postInvalidateDelayed(35)
        }
    }

    private class ExpressionParser(private val source: String) {
        private val s = source.replace(" ", "")
        private var i = 0
        fun parse(): String { val v = expression(); if (i != s.length || !v.isFinite()) throw IllegalArgumentException(); return if (v % 1.0 == 0.0 && v in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) v.toLong().toString() else v.toString() }
        private fun expression(): Double { var v = term(); while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val n = term(); v = if (op == '+') v+n else v-n }; return v }
        private fun term(): Double { var v = factor(); while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) { val op = s[i++]; val n = factor(); if ((op == '/' || op == '%') && n == 0.0) throw ArithmeticException(); v = when(op) {'*' -> v*n; '/' -> v/n; else -> v%n} }; return v }
        private fun factor(): Double { if (i < s.length && (s[i] == '+' || s[i] == '-')) { val sign=s[i++]; val n=factor(); return if(sign=='-') -n else n }; if (i < s.length && s[i] == '(') { i++; val v=expression(); if (i>=s.length || s[i++]!=')') throw IllegalArgumentException(); return v }; val start=i; while(i<s.length && (s[i].isDigit() || s[i]=='.')) i++; if(start==i) throw IllegalArgumentException(); return s.substring(start,i).toDouble() }
    }
}
