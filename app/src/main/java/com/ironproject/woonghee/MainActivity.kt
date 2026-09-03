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
import androidx.health.connect.client.records.metadata.DataOrigin
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
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    }

    private lateinit var webView: WebView
    private var client: HealthConnectClient? = null

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            lifecycleScope.launch {
                sendStatus("connected", "권한 설정이 완료되었습니다. 삼성헬스 기록을 확인합니다.")
                syncRunningDataInternal()
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

        val status = HealthConnectClient.getSdkStatus(this)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
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
            lifecycleScope.launch { syncRunningDataInternal() }
        }

        @JavascriptInterface
        fun autoSyncIfPermitted() {
            lifecycleScope.launch {
                val c = client
                if (c == null) {
                    sendStatus("error", healthConnectUnavailableMessage())
                    return@launch
                }
                val needed = basicPermissions()
                val granted = c.permissionController.getGrantedPermissions()
                if (granted.containsAll(needed)) {
                    syncRunningDataInternal()
                }
            }
        }
    }

    private fun basicPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
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
            val provider = HealthConnectClient.DEFAULT_PROVIDER_PACKAGE_NAME
            val uri = Uri.parse("market://details?id=$provider&url=healthconnect%3A%2F%2Fonboarding")
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

    private suspend fun syncRunningDataInternal() {
        val c = client
        if (c == null) {
            sendStatus("error", healthConnectUnavailableMessage())
            return
        }

        val granted = c.permissionController.getGrantedPermissions()
        if (!granted.containsAll(basicPermissions())) {
            sendStatus("error", "먼저 '연결 / 권한 허용'을 눌러 운동·거리·심박 읽기 권한을 허용해 주세요.")
            return
        }

        sendStatus("syncing", "Samsung Health 러닝 기록을 불러오는 중…")

        try {
            // Prefer 6 months. If historical permission is unavailable/not granted, Health Connect
            // will expose the allowed range; retry 30 days on a read restriction.
            val sixMonthsAgo = ZonedDateTime.now().minusMonths(6).toInstant()
            val now = Instant.now()
            val sessions = try {
                readSamsungRuns(c, sixMonthsAgo, now)
            } catch (e: Exception) {
                readSamsungRuns(c, now.minus(Duration.ofDays(30)), now)
            }

            val array = JSONArray()
            for (session in sessions.sortedBy { it.startTime }) {
                val sourceFilter = setOf(DataOrigin(SAMSUNG_HEALTH))
                val agg = c.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            HeartRateRecord.BPM_AVG
                        ),
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                        dataOriginFilter = sourceFilter
                    )
                )

                val minutes = Duration.between(session.startTime, session.endTime).seconds / 60.0
                val km = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
                val hr = agg[HeartRateRecord.BPM_AVG]?.toDouble() ?: 0.0
                val localDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString()

                array.put(JSONObject().apply {
                    put("date", localDate)
                    put("startEpoch", session.startTime.toEpochMilli())
                    put("minutes", minutes)
                    put("distanceKm", km)
                    put("avgHr", hr)
                    put("source", "Samsung Health")
                })
            }

            val quoted = JSONObject.quote(array.toString())
            runOnUiThread {
                webView.evaluateJavascript("window.receiveHealthData($quoted);", null)
            }
        } catch (e: Exception) {
            sendStatus("error", "동기화 중 오류: ${e.message ?: "알 수 없는 오류"}")
        }
    }

    private suspend fun readSamsungRuns(
        c: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<ExerciseSessionRecord> {
        val response = c.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = setOf(DataOrigin(SAMSUNG_HEALTH)),
                pageSize = 1000
            )
        )
        return response.records.filter {
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
            it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        }
    }

    private fun sendStatus(status: String, message: String) {
        val s = JSONObject.quote(status)
        val m = JSONObject.quote(message)
        runOnUiThread {
            webView.evaluateJavascript("window.onHealthStatus($s,$m);", null)
        }
    }
}
