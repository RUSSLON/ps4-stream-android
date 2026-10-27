package com.ps4stream.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.text.TextUtils
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.Executors

/** Fully native control shell around a same-origin WebView; no addJavascriptInterface. */
class MainActivity : Activity() {
    private val bg = Color.rgb(9, 14, 29)
    private val surface = Color.rgb(20, 28, 49)
    private val pale = Color.rgb(233, 238, 253)
    private val muted = Color.rgb(161, 178, 207)
    private val accent = Color.rgb(135, 111, 255)
    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var secrets: SecureKeyStore
    private lateinit var info: TextView
    private lateinit var loading: ProgressBar
    private lateinit var searchInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var epoch = 0
    private var isBridgeReady = false
    private var mediaTitle = "Выбери фильм"
    private var currentHost = "rezka-tv.org"
    private var adapterScript = ""

    private fun dip(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun corners(color: Int, radius: Int = 15, stroke: Int? = null): Drawable {
        val shape = GradientDrawable()
        shape.setColor(color)
        shape.cornerRadius = dip(radius).toFloat()
        if (stroke != null) shape.setStroke(dip(1), stroke)
        return shape
    }
    private fun gradient(): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(113, 78, 234), Color.rgb(45, 133, 250))
    ).apply {cornerRadius = dip(15).toFloat()}

    private fun label(text: String, size: Float = 15f, color: Int = pale, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; setTextColor(color); textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun button(text: String, primary: Boolean = false): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 13f
        minHeight = 0
        minimumHeight = 0
        background = if (primary) gradient() else corners(Color.rgb(35, 45, 72), 14, Color.rgb(58, 70, 103))
        setPadding(dip(13), dip(12), dip(13), dip(12))
    }
    private fun pad(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dip(12)
    }
    private fun block(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dip(18), dip(17), dip(18), dip(17))
        background = corners(surface, 20, Color.rgb(49, 59, 89))
    }
    private fun notify(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun origin(host: String) = "https://$host/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        prefs = getSharedPreferences("ps4stream_config", Context.MODE_PRIVATE)
        secrets = SecureKeyStore(this)
        currentHost = prefs.getString("active_host", "rezka-tv.org") ?: "rezka-tv.org"
        if (currentHost !in AppConfig.allHosts(prefs)) currentHost = "rezka-tv.org"
        adapterScript = assets.open("rezka_adapter.js").bufferedReader().use { it.readText() }
        buildUi()
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(origin(currentHost))
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)}
        setContentView(root)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(16), dip(14), dip(16), dip(10))
            background = corners(Color.rgb(15, 23, 43), 0)
        }
        val top = LinearLayout(this).apply {orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL}
        val identity = LinearLayout(this).apply {orientation = LinearLayout.VERTICAL}
        identity.addView(label("◆  PS4 STREAM", 19f, Color.WHITE, true))
        identity.addView(label("MOBILE  ·  SEND TO PLAYSTATION", 10f, Color.rgb(175, 158, 250), true))
        top.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        val settingsButton = button("⚙")
        settingsButton.setOnClickListener { settings() }
        top.addView(settingsButton, LinearLayout.LayoutParams(dip(51), dip(43)))
        header.addView(top)

        val row = LinearLayout(this).apply {orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dip(12), 0, 0)}
        searchInput = EditText(this).apply {
            hint = "Поиск фильма или сериала"
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(muted); textSize = 14f
            setPadding(dip(13), 0, dip(10), 0)
            background = corners(Color.rgb(31, 41, 67), 13, Color.rgb(56, 69, 101))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, id, _ ->
                if(id == EditorInfo.IME_ACTION_SEARCH) {search(); true} else false
            }
        }
        row.addView(searchInput, LinearLayout.LayoutParams(0, dip(46), 1f))
        val searchButton = button("Поиск")
        val searchParams = LinearLayout.LayoutParams(-2, dip(46)).apply {leftMargin = dip(8)}
        row.addView(searchButton, searchParams)
        searchButton.setOnClickListener {search()}
        header.addView(row)
        val nav = LinearLayout(this).apply {orientation = LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dip(9),0,0)}
        val back = button("‹")
        back.setOnClickListener {if(web.canGoBack())web.goBack()}
        val forward = button("›")
        forward.setOnClickListener {if(web.canGoForward())web.goForward()}
        val home = button("Главная")
        home.setOnClickListener { web.loadUrl(origin(currentHost)) }
        val mirror = button("Сайт ▾")
        mirror.setOnClickListener {chooseMirror()}
        for (b in listOf(back,forward,home,mirror)) {
            val p = LinearLayout.LayoutParams(0,dip(42),if(b===mirror)1.45f else if(b===home)1.25f else .65f)
            p.rightMargin = dip(7)
            nav.addView(b,p)
        }
        header.addView(nav)
        root.addView(header)

        loading = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress=0; progressTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        root.addView(loading,LinearLayout.LayoutParams(-1,dip(3)))
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(13,16,25))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    if (AppConfig.allowedUrl(request.url.toString(), AppConfig.allHosts(prefs))) return false
                    // Don't evaluate adapter code on unrelated pages. Open external links in the system browser.
                    runCatching {startActivity(Intent(Intent.ACTION_VIEW, request.url))}
                    return true
                }
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    epoch++; isBridgeReady = false
                    info.text = "Загружаем сайт…"
                    loading.progress=5
                }
                override fun onPageFinished(view: WebView, url: String) {
                    loading.progress=100
                    if (!AppConfig.allowedUrl(url, AppConfig.allHosts(prefs))) {
                        info.text="Открой поддерживаемое зеркало";return
                    }
                    installAdapter()
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel() // NEVER bypass certificate checks.
                    notify("Ошибка сертификата HTTPS; загрузка отменена")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {loading.progress=newProgress}
            }
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(14),dip(12),dip(14),dip(18))
            background = corners(Color.rgb(17, 25, 46), 0)
        }
        info = label("Открой фильм на сайте",13f, muted)
        info.maxLines=1; info.ellipsize=TextUtils.TruncateAt.END
        bottom.addView(info,pad())
        val actions = LinearLayout(this).apply {orientation = LinearLayout.HORIZONTAL}
        val episodes = button("Озвучка / серии")
        actions.addView(episodes,LinearLayout.LayoutParams(0,dip(56),1f).apply {rightMargin=dip(10)})
        episodes.setOnClickListener {openSelectors()}
        val send = button("Отправить на PS4",true)
        actions.addView(send,LinearLayout.LayoutParams(0,dip(56),1.2f))
        send.setOnClickListener {findSources()}
        bottom.addView(actions)
        root.addView(bottom)
    }

    private fun search() {
        val query=searchInput.text.toString().trim()
        if (query.isEmpty()) {notify("Введи название фильма");return}
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // Mirrors may implement different search routes. Their page has native search as fallback.
        web.loadUrl("https://$currentHost/search/?do=search&subaction=search&q=$encoded")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken,0)
        searchInput.clearFocus()
    }

    private fun installAdapter() {
        if (!AppConfig.allowedUrl(web.url.orEmpty(), AppConfig.allHosts(prefs))) return
        val expected=epoch
        web.evaluateJavascript(adapterScript + "\ntrue;") {
            if(expected==epoch){isBridgeReady=true; snapshot { data ->
                mediaTitle=data.optString("title","Открой фильм")
                info.text = if(data.optBoolean("ready")) mediaTitle else "Выбери фильм на сайте"
            }}
        }
    }

    private fun evalObject(expression: String, cb: (JSONObject) -> Unit) {
        if (!isBridgeReady || !AppConfig.allowedUrl(web.url.orEmpty(), AppConfig.allHosts(prefs))) {
            notify("Открой фильм и дождись полной загрузки сайта");return
        }
        web.evaluateJavascript("JSON.stringify($expression)") { raw ->
            try {
                val decoded = JSONTokener(raw).nextValue() as String
                cb(JSONObject(decoded))
            } catch (_: Exception) {notify("Не удалось прочитать данные страницы. Обнови сайт.")}
        }
    }
    private fun snapshot(cb: (JSONObject) -> Unit) = evalObject("window.PS4MobileAdapter.snapshot()",cb)

    private fun openSelectors() {
        snapshot { data ->
            val kinds = listOf("translations","seasons","episodes")
            val titles = listOf("Озвучка / перевод","Сезон","Серия")
            val active = kinds.indices.filter {data.optJSONArray(kinds[it])?.length() ?: 0 > 0}
            if(active.isEmpty()) {notify("Выбор озвучек и серий на этой странице пока не найден");return@snapshot}
            AlertDialog.Builder(this).setTitle("Выбери на сайте")
                .setItems(active.map{titles[it]}.toTypedArray()) { _, idx ->
                    selectItem(kinds[active[idx]], titles[active[idx]])
                }.setNegativeButton("Закрыть",null).show()
        }
    }
    private fun selectItem(kind: String, title: String) {
        snapshot { data ->
            val arr=data.optJSONArray(kind) ?: JSONArray()
            if(arr.length()==0) {notify("Нет вариантов: $title");return@snapshot}
            val labels=(0 until arr.length()).map { i ->
                val entry=arr.optJSONObject(i)
                (if(entry?.optBoolean("selected")==true) "✓  " else "")+(entry?.optString("label") ?: "Вариант ${i+1}")
            }
            AlertDialog.Builder(this).setTitle(title)
                .setItems(labels.toTypedArray()) { _, index ->
                    // kind comes only from our own fixed values; index is an integer.
                    val expected=epoch
                    web.evaluateJavascript("window.PS4MobileAdapter.choose('$kind',$index)") {
                        handler.postDelayed({
                            if(expected == epoch) {
                                snapshot { d -> mediaTitle=d.optString("title",mediaTitle); info.text=mediaTitle }
                            }
                        },900)
                    }
                }.setNegativeButton("Отмена",null).show()
        }
    }

    private fun findSources() {
        if (!isBridgeReady) {notify("Страница ещё загружается");return}
        val before=epoch
        info.text="Ищем качества и субтитры…"
        web.evaluateJavascript("window.PS4MobileAdapter.begin()") {
            pollSources(before,System.currentTimeMillis())
        }
    }
    private fun pollSources(expectedEpoch: Int, started: Long) {
        if(expectedEpoch != epoch || isFinishing) return
        if(System.currentTimeMillis()-started > 20_000) {info.text="Время ожидания истекло";notify("Источник долго не отвечает. Повтори позже.");return}
        web.evaluateJavascript("JSON.stringify(window.PS4MobileAdapter.peek())") { raw ->
            if(expectedEpoch != epoch)return@evaluateJavascript
            try {
                val decoded=JSONTokener(raw).nextValue() as String
                val obj=JSONObject(decoded)
                when(obj.optString("state")) {
                    "done" -> {
                        info.text="Выбери качество для PS4"
                        val sources=obj.getJSONObject("data")
                        showSources(sources)
                    }
                    "error" -> {info.text="Не удалось получить видео";showError(obj.optString("message"))}
                    else -> handler.postDelayed({pollSources(expectedEpoch,started)},400)
                }
            } catch (e: Exception) {showError("Ошибка чтения ответа: ${e.message}")}
        }
    }

    private fun showSources(data: JSONObject) {
        val videos = data.optJSONArray("videos") ?: JSONArray()
        if (videos.length()==0) {showError("Прямые MP4-ссылки не обнаружены");return}
        val entries=(0 until videos.length()).map {videos.getJSONObject(it)}
        val subtitlesJson=data.optJSONArray("subtitles") ?: JSONArray()
        val subs=(0 until subtitlesJson.length()).mapNotNull {i ->
            subtitlesJson.optJSONObject(i)?.let {obj ->
                val link=obj.optString("url")
                if(link.startsWith("https://")) RenderClient.Subtitle(obj.optString("lang","Субтитры"),link) else null
            }
        }
        val sheet = sheet("Отправить на PlayStation", "Выбери поток и субтитры. Render выдаст PIN из 4 цифр.")
        val holder=sheet.first
        holder.addView(label(data.optString("title", "Видео"),17f,Color.WHITE,true),pad())
        holder.addView(label("КАЧЕСТВО",12f,Color.rgb(186,175,255),true),pad())
        val options=RadioGroup(this)
        entries.forEachIndexed {index,entry ->
            val radio=RadioButton(this).apply {
                text = entry.optString("quality","Авто")
                setTextColor(pale);textSize=15f
                id=View.generateViewId()
                tag=index
                isChecked=index==0
                buttonTintList=android.content.res.ColorStateList.valueOf(accent)
                setPadding(dip(9),dip(4),dip(4),dip(4))
            }
            options.addView(radio,pad())
        }
        holder.addView(options)
        val checks=mutableListOf<Pair<CheckBox,RenderClient.Subtitle>>()
        holder.addView(label("СУБТИТРЫ",12f,Color.rgb(186,175,255),true),pad())
        if(subs.isEmpty()) holder.addView(label("На странице субтитры не найдены",13f,muted),pad())
        subs.forEach { sub ->
            val cb=CheckBox(this).apply {
                text=sub.lang;setTextColor(pale)
                buttonTintList=android.content.res.ColorStateList.valueOf(accent)
            }
            checks.add(cb to sub);holder.addView(cb)
        }
        val send=button("ОТПРАВИТЬ НА PS4  →",true)
        holder.addView(send,LinearLayout.LayoutParams(-1,dip(54)).apply {topMargin=dip(14)})
        val dlg=sheet.second
        send.setOnClickListener {
            val checked=options.findViewById<RadioButton>(options.checkedRadioButtonId)
            val idx=(checked?.tag as? Int) ?: 0
            val e=entries[idx]
            val media=RenderClient.Media(
                data.optString("title","Видео"), e.optString("quality"),e.optString("url"),subs,web.url.orEmpty()
            )
            val key=secrets.read()
            if(key.length<12) {dlg.dismiss();notify("Сначала введи API_KEY в настройках");settings();return@setOnClickListener}
            send.isEnabled=false;send.text="Отправляем на Render…"
            val selected=checks.filter {it.first.isChecked}.map {it.second}
            io.execute {
                val result=runCatching {RenderClient.send(media,selected,key)}
                runOnUiThread {
                    if(isFinishing)return@runOnUiThread
                    dlg.dismiss()
                    result.onSuccess {showPin(it)}.onFailure {showError(it.message ?: "Ошибка отправки")}
                }
            }
        }
        dlg.show()
        dlg.window?.setLayout((resources.displayMetrics.widthPixels*.96f).toInt(),-2)
    }

    /** Returns vertical content and a bottom-aligned dialog with a scroll container. */
    private fun sheet(title: String, subtitle: String): Pair<LinearLayout,Dialog> {
        val dlg=Dialog(this)
        val base=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dip(20),dip(19),dip(20),dip(20)); background=corners(Color.rgb(22,29,50),24,Color.rgb(66,76,109))}
        base.addView(label("◆ PS4 STREAM",11f,Color.rgb(185,172,255),true),pad())
        base.addView(label(title,23f,Color.WHITE,true),pad())
        base.addView(label(subtitle,13f,muted),pad())
        val scroll=ScrollView(this).apply {isFillViewport=false}
        val container=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        scroll.addView(container)
        base.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        dlg.setContentView(base)
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dlg.window?.setGravity(Gravity.BOTTOM)
        dlg.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dlg.window?.setDimAmount(.65f)
        return container to dlg
    }

    private fun showPin(result: RenderClient.Result) {
        val (content, dialog)=sheet("Готово!", "Открой страницу Render на PlayStation 4")
        val pin=label(result.pin,54f,Color.WHITE,true).apply {
            gravity=Gravity.CENTER
            letterSpacing=.22f
            setPadding(0,dip(12),0,dip(12))
            background=corners(Color.rgb(12,20,39),15,Color.rgb(89,78,153))
        }
        content.addView(pin,pad())
        content.addView(label("Код действует 10 минут и подходит только для одного подключения.",13f,muted),pad())
        val url=label(AppConfig.RENDER_URL+"/",16f,Color.rgb(177,164,255),true)
        url.gravity=Gravity.CENTER
        content.addView(url,pad())
        if(result.warnings.isNotEmpty())content.addView(label("Субтитры: "+result.warnings.joinToString("; "),12f,Color.rgb(252,191,170)),pad())
        val copy=button("СКОПИРОВАТЬ PIN",true)
        copy.setOnClickListener {
            val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PS4 Stream PIN",result.pin))
            notify("PIN скопирован")
        }
        content.addView(copy,LinearLayout.LayoutParams(-1,dip(52)))
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*.96f).toInt(),-2)
        info.text="Отправлено на PlayStation · PIN: ${result.pin}"
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setTitle("PS4 Stream").setMessage(message)
            .setPositiveButton("Понятно",null).show()
    }

    private fun chooseMirror() {
        val hosts=AppConfig.allHosts(prefs)
        AlertDialog.Builder(this).setTitle("Выбери сайт")
            .setItems(hosts.map {if(it==currentHost) "✓  $it" else it}.toTypedArray()) {_,index ->
                currentHost=hosts[index]
                prefs.edit().putString("active_host",currentHost).apply()
                web.loadUrl(origin(currentHost))
            }.setNeutralButton("Добавить зеркало") {_,_->settings()}
            .setNegativeButton("Закрыть",null).show()
    }

    private fun settings() {
        val (content, dialog)=sheet("Настройки", "Облачный сервер закреплён. API_KEY нужен для отправки PIN.")
        content.addView(label("RENDER",11f,Color.rgb(185,172,255),true),pad())
        content.addView(label(AppConfig.RENDER_URL+"/",14f,Color.WHITE),pad())
        content.addView(label("API_KEY",13f,muted,true),pad())
        val input=EditText(this).apply {
            hint="Секретный ключ из Render → Environment"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE);setHintTextColor(muted);setSingleLine(true)
            setText(secrets.read());textSize=14f
            background=corners(Color.rgb(32,41,66),11,Color.rgb(67,79,112))
            setPadding(dip(12),dip(11),dip(12),dip(11))
        }
        content.addView(input,pad())
        val save=button("Сохранить API_KEY",true)
        save.setOnClickListener {
            val value=input.text.toString().trim()
            if(value.length<12){notify("Ключ слишком короткий");return@setOnClickListener}
            runCatching {secrets.write(value)}.onSuccess {notify("API_KEY сохранён в Android Keystore")}
                .onFailure {notify("Не удалось защитить ключ: ${it.message}")}
            input.clearFocus()
        }
        content.addView(save,LinearLayout.LayoutParams(-1,dip(48)).apply{bottomMargin=dip(10)})
        val demo=button("Отправить тестовый ролик (MDN)")
        demo.setOnClickListener {
            val key=secrets.read()
            if(key.length < 12){notify("Сначала сохрани API_KEY");return@setOnClickListener}
            demo.isEnabled=false;demo.text="Отправляем тест…"
            io.execute {
                val item=RenderClient.Media(
                    "Демо · Цветы (MDN)", "MP4",
                    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
                    emptyList(),"https://developer.mozilla.org/"
                )
                val outcome=runCatching {RenderClient.send(item,emptyList(),key)}
                runOnUiThread {
                    demo.isEnabled=true;demo.text="Отправить тестовый ролик (MDN)"
                    dialog.dismiss()
                    outcome.onSuccess {showPin(it)}.onFailure {showError(it.message?:"Ошибка тестовой отправки")}
                }
            }
        }
        content.addView(demo,LinearLayout.LayoutParams(-1,dip(48)).apply{bottomMargin=dip(20)})
        content.addView(label("ЗЕРКАЛА",13f,Color.rgb(185,172,255),true),pad())
        val hostInput=EditText(this).apply {
            hint="https://другое-зеркало.example"
            setSingleLine(true);textSize=13f;setTextColor(Color.WHITE);setHintTextColor(muted)
            setPadding(dip(10),dip(10),dip(10),dip(10))
            background=corners(Color.rgb(32,41,66),11,Color.rgb(67,79,112))
        }
        content.addView(hostInput,pad())
        val add=button("Добавить HTTPS-зеркало")
        add.setOnClickListener {
            runCatching {
                val host=AppConfig.normalizeHost(hostInput.text.toString())
                val custom=AppConfig.allHosts(prefs).filter{it !in AppConfig.defaultHosts}
                if(host !in AppConfig.defaultHosts && host !in custom) AppConfig.saveHosts(prefs,custom+host)
                hostInput.text.clear()
                notify("Добавлено: $host")
            }.onFailure{notify(it.message ?: "Некорректный домен")}
        }
        content.addView(add,LinearLayout.LayoutParams(-1,dip(48)).apply{bottomMargin=dip(10)})
        val remove=button("Удалить дополнительное зеркало")
        remove.setOnClickListener {
            val custom=AppConfig.allHosts(prefs).filter{it !in AppConfig.defaultHosts}
            if(custom.isEmpty()){notify("Дополнительных зеркал нет");return@setOnClickListener}
            AlertDialog.Builder(this).setTitle("Удалить зеркало")
                .setItems(custom.toTypedArray()){_,idx ->
                    AppConfig.saveHosts(prefs,custom.filter{it!=custom[idx]})
                    if(currentHost==custom[idx]){currentHost="rezka-tv.org";prefs.edit().putString("active_host",currentHost).apply();web.loadUrl(origin(currentHost))}
                    notify("Зеркало удалено")
                }.setNegativeButton("Отмена",null).show()
        }
        content.addView(remove,LinearLayout.LayoutParams(-1,dip(48)))
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*.96f).toInt(),-2)
    }

    @Deprecated("Legacy Android Back button integration")
    override fun onBackPressed() {
        if(web.canGoBack())web.goBack() else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState);super.onSaveInstanceState(outState) }
    override fun onPause() {super.onPause();web.onPause()}
    override fun onResume() {super.onResume();web.onResume()}
    override fun onDestroy() {handler.removeCallbacksAndMessages(null);web.destroy();io.shutdownNow();super.onDestroy()}
}
