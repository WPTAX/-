package com.ironproject.woonghee

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    companion object {
        private const val HEALTH_CONNECT_PROVIDER = "com.google.android.apps.healthdata"
        private const val GARMIN_CONNECT = "com.garmin.android.apps.connectmobile"
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    }

    private lateinit var webView: WebView
    private var client: HealthConnectClient? = null
    private var pageReady = false
    private var syncing = false

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            lifecycleScope.launch {
                syncAllInternal()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(IronBridge(), "AndroidIron")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.toString() != "file:///android_asset/index.html"

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "file:///android_asset/index.html") {
                        pageReady = true
                        lifecycleScope.launch { syncAllInternal(true) }
                    }
                }
            }
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

    }

    override fun onResume() {
        super.onResume()
        if (pageReady) lifecycleScope.launch { syncAllInternal(true) }
    }

    override fun onDestroy() {
        pageReady = false
        webView.removeJavascriptInterface("AndroidIron")
        webView.destroy()
        super.onDestroy()
    }

    inner class IronBridge {
        @JavascriptInterface
        fun requestHealthPermissions() {
            runOnUiThread { requestPermissionsInternal() }
        }

        @JavascriptInterface
        fun syncRunningData() {
            lifecycleScope.launch { syncAllInternal() }
        }

        @JavascriptInterface
        fun autoSyncIfPermitted() {
            lifecycleScope.launch { syncAllInternal(true) }
        }
    }

    private fun basicPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    private fun requestedPermissions(): Set<String> {
        val result = basicPermissions().toMutableSet()
        val c = client
        if (c != null &&
            c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            result.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        }
        return result
    }

    private fun requestPermissionsInternal() {
        try { requestPermissionsSafely() }
        catch (_: Exception) { sendStatus("error", "권한 화면을 열지 못했습니다. 기기 설정에서 Health Connect를 확인해 주세요.") }
    }

    private fun requestPermissionsSafely() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status == HealthConnectClient.SDK_UNAVAILABLE) {
            sendStatus("error", "이 기기에서는 Health Connect를 사용할 수 없습니다.")
            return
        }
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uri = Uri.parse("market://details?id=$HEALTH_CONNECT_PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
            try { startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.vending")
                putExtra("overlay", true)
                putExtra("callerId", packageName)
            }) } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PROVIDER")))
            }
            sendStatus("error", "Health Connect 설치 또는 업데이트가 필요합니다.")
            return
        }
        if (client == null) client = HealthConnectClient.getOrCreate(this)
        permissionLauncher.launch(requestedPermissions())
    }

    private fun healthConnectUnavailableMessage(): String {
        val status = HealthConnectClient.getSdkStatus(this)
        return if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
            "Health Connect 설치 또는 업데이트가 필요합니다."
        else "Health Connect를 사용할 수 없습니다."
    }

    private fun sourceLabel(packageName: String): String = when (packageName) {
        GARMIN_CONNECT -> "Garmin Connect"
        SAMSUNG_HEALTH -> "Samsung Health"
        else -> packageName.substringAfterLast('.').ifBlank { "Health Connect" }
    }

    private suspend fun syncAllInternal(silent: Boolean = false) {
        if (!pageReady || syncing) return
        syncing = true
        try { syncSnapshot(silent) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { sendStatus("error", "동기화 실패: 권한 및 Health Connect 상태를 확인해 주세요. 기존 기록은 유지합니다.") }
        finally { syncing = false }
    }

    private suspend fun syncSnapshot(silent: Boolean) {
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            if (client == null) client = HealthConnectClient.getOrCreate(this)
        } else client = null
        val c = client
        if (c == null) {
            if (!silent) sendStatus("error", healthConnectUnavailableMessage())
            return
        }

        val granted = c.permissionController.getGrantedPermissions()
        val canRun = HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted
        val canSleep = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
        if (!canRun && !canSleep) {
            if (!silent) sendStatus("error", "러닝 또는 수면 읽기 권한을 연결해 주세요.")
            return
        }

        sendStatus("syncing", "Garmin/Health Connect 기록을 불러오는 중…")

        try {
            val now = Instant.now()
            val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
            val start = now.minus(Duration.ofDays(if (history) 180 else 29))
            var runOk = canRun
            var sleepOk = canSleep
            var metricWarning = false
            val runs = if (canRun) try { readRuns(c, start, now) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { runOk = false; emptyList() } else emptyList()

            val runArray = JSONArray()
            for (session in runs.sortedBy { it.startTime }) {
                val origin = session.metadata.dataOrigin
                val canDistance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
                val canHr = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
                val metrics = buildSet<androidx.health.connect.client.aggregate.AggregateMetric<*>> {
                    if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
                    if (canHr) add(HeartRateRecord.BPM_AVG)
                }
                val agg = if (metrics.isEmpty()) null else try { c.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                        dataOriginFilter = setOf(origin)
                    )
                ) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { metricWarning = true; null }
                val minutes = Duration.between(session.startTime, session.endTime).seconds / 60.0
                val km = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers
                val hr = agg?.get(HeartRateRecord.BPM_AVG)?.toDouble()
                val localDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                runArray.put(JSONObject().apply {
                    put("id", session.metadata.id)
                    put("endEpoch", session.endTime.toEpochMilli())
                    put("date", localDate)
                    put("startEpoch", session.startTime.toEpochMilli())
                    put("minutes", minutes)
                    put("distanceKm", km ?: JSONObject.NULL)
                    put("avgHr", hr ?: JSONObject.NULL)
                    put("source", sourceLabel(origin.packageName))
                    put("sourcePackage", origin.packageName)
                })
            }

            val sleeps = if (canSleep) try { readSleep(c, start, now) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { sleepOk = false; emptyList() } else emptyList()
            val sleepArray = JSONArray()
            for (s in sleeps.sortedBy { it.startTime }) {
                val mins = Duration.between(s.startTime, s.endTime).seconds / 60.0
                val date = s.endTime.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val pkg = s.metadata.dataOrigin.packageName
                sleepArray.put(JSONObject().apply {
                    put("id", s.metadata.id)
                    put("endEpoch", s.endTime.toEpochMilli())
                    put("date", date)
                    put("minutes", mins)
                    put("hours", mins / 60.0)
                    put("startEpoch", s.startTime.toEpochMilli())
                    put("source", sourceLabel(pkg))
                    put("sourcePackage", pkg)
                })
            }

            val runsQuoted = JSONObject.quote(runArray.toString())
            val sleepQuoted = JSONObject.quote(sleepArray.toString())
            runOnUiThread {
                if (pageReady) {
                    if (runOk) webView.evaluateJavascript("window.receiveHealthData($runsQuoted,${start.toEpochMilli()},${now.toEpochMilli()});", null)
                    if (sleepOk) webView.evaluateJavascript("window.receiveSleepData($sleepQuoted,${start.toEpochMilli()},${now.toEpochMilli()});", null)
                }
            }
            val complete = runOk && sleepOk && !metricWarning && granted.containsAll(basicPermissions())
            val scope = if (history) "최근 180일" else "최근 29일 (과거 기록 권한 없음)"
            sendStatus(if (complete) "connected" else "partial",
                "$scope · 러닝 ${if (runOk) runArray.length().toString() else "미갱신"} · 수면 ${if (sleepOk) sleepArray.length().toString() else "미갱신"}" +
                    (if (complete) " · 동기화 완료" else " · 일부 권한 미허용 또는 읽기 실패. 미갱신 기록은 유지합니다."))
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            sendStatus("error", "동기화 실패: Health Connect 상태를 확인해 주세요. 기존 기록은 유지합니다.")
        }
    }

    private suspend fun readRuns(c: HealthConnectClient, start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val records = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        do {
        val response = c.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1000,
                pageToken = token
            )
        )
        records.addAll(response.records.filter {
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        })
        token = response.pageToken
        } while (token != null)
        return records
    }

    private suspend fun readSleep(c: HealthConnectClient, start: Instant, end: Instant): List<SleepSessionRecord> {
        val records = mutableListOf<SleepSessionRecord>()
        var token: String? = null
        do {
        val response = c.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1000,
                pageToken = token
            )
        )
        records.addAll(response.records)
        token = response.pageToken
        } while (token != null)
        return records
    }

    private fun sendStatus(status: String, message: String) {
        val s = JSONObject.quote(status)
        val m = JSONObject.quote(message)
        runOnUiThread { if (pageReady) webView.evaluateJavascript("window.onHealthStatus($s,$m);", null) }
    }
}
