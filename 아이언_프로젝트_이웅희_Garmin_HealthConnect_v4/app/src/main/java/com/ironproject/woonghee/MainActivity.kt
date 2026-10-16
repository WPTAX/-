package com.ironproject.woonghee

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {

    companion object {
        private const val HEALTH_CONNECT_PROVIDER = "com.google.android.apps.healthdata"
        private const val GARMIN_CONNECT = "com.garmin.android.apps.connectmobile"
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    }

    private lateinit var webView: WebView
    private var client: HealthConnectClient? = null

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            lifecycleScope.launch {
                sendStatus("connected", "권한 설정이 완료되었습니다. 기록을 불러옵니다.")
                syncAllInternal()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            addJavascriptInterface(IronBridge(), "AndroidIron")
            webViewClient = object : WebViewClient() {}
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            client = HealthConnectClient.getOrCreate(this)
        }
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
            lifecycleScope.launch {
                val c = client ?: return@launch
                val granted = c.permissionController.getGrantedPermissions()
                if (granted.containsAll(basicPermissions())) syncAllInternal()
            }
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
        val status = HealthConnectClient.getSdkStatus(this)
        if (status == HealthConnectClient.SDK_UNAVAILABLE) {
            sendStatus("error", "이 기기에서는 Health Connect를 사용할 수 없습니다.")
            return
        }
        if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            val uri = Uri.parse("market://details?id=$HEALTH_CONNECT_PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.vending")
                putExtra("overlay", true)
                putExtra("callerId", packageName)
            })
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

    private suspend fun syncAllInternal() {
        val c = client
        if (c == null) {
            sendStatus("error", healthConnectUnavailableMessage())
            return
        }

        val granted = c.permissionController.getGrantedPermissions()
        if (!granted.containsAll(basicPermissions())) {
            sendStatus("error", "먼저 Health Connect 읽기 권한을 허용해 주세요.")
            return
        }

        sendStatus("syncing", "Garmin/Health Connect 기록을 불러오는 중…")

        try {
            val now = Instant.now()
            val start = ZonedDateTime.now().minusMonths(6).toInstant()
            val runs = try { readRuns(c, start, now) }
            catch (_: Exception) { readRuns(c, now.minus(Duration.ofDays(30)), now) }

            val runArray = JSONArray()
            for (session in runs.sortedBy { it.startTime }) {
                val origin = session.metadata.dataOrigin
                val agg = c.aggregate(
                    AggregateRequest(
                        metrics = setOf(DistanceRecord.DISTANCE_TOTAL, HeartRateRecord.BPM_AVG),
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                        dataOriginFilter = setOf(origin)
                    )
                )
                val minutes = Duration.between(session.startTime, session.endTime).seconds / 60.0
                val km = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
                val hr = agg[HeartRateRecord.BPM_AVG]?.toDouble() ?: 0.0
                val localDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                runArray.put(JSONObject().apply {
                    put("date", localDate)
                    put("startEpoch", session.startTime.toEpochMilli())
                    put("minutes", minutes)
                    put("distanceKm", km)
                    put("avgHr", hr)
                    put("source", sourceLabel(origin.packageName))
                    put("sourcePackage", origin.packageName)
                })
            }

            val sleeps = try { readSleep(c, start, now) }
            catch (_: Exception) { readSleep(c, now.minus(Duration.ofDays(30)), now) }
            val sleepArray = JSONArray()
            for (s in sleeps.sortedBy { it.startTime }) {
                val mins = Duration.between(s.startTime, s.endTime).seconds / 60.0
                val date = s.endTime.atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val pkg = s.metadata.dataOrigin.packageName
                sleepArray.put(JSONObject().apply {
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
                webView.evaluateJavascript("window.receiveHealthData($runsQuoted);", null)
                webView.evaluateJavascript("window.receiveSleepData($sleepQuoted);", null)
            }
            sendStatus("connected", "동기화 완료 · Garmin Connect는 Health Connect를 통해 반영됩니다.")
        } catch (e: Exception) {
            sendStatus("error", "동기화 중 오류: ${e.message ?: "알 수 없는 오류"}")
        }
    }

    private suspend fun readRuns(c: HealthConnectClient, start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val response = c.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1000
            )
        )
        return response.records.filter {
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        }
    }

    private suspend fun readSleep(c: HealthConnectClient, start: Instant, end: Instant): List<SleepSessionRecord> {
        return c.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                pageSize = 1000
            )
        ).records
    }

    private fun sendStatus(status: String, message: String) {
        val s = JSONObject.quote(status)
        val m = JSONObject.quote(message)
        runOnUiThread { webView.evaluateJavascript("window.onHealthStatus($s,$m);", null) }
    }
}
