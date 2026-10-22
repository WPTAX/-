package com.ironproject.woonghee

import android.view.ViewGroup
import android.webkit.WebView
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ManualFlowTest {
    private fun js(scenario: ActivityScenario<MainActivity>, script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        scenario.onActivity { activity ->
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            (root.getChildAt(0) as WebView).evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("WebView callback timed out", latch.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun awaitApp(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (js(scenario, "typeof window.IronModel === 'object' && typeof renderAll === 'function' && document.getElementById('homeGoal').textContent === '160분'") == "true") return
            Thread.sleep(200)
        }
        val state = js(scenario, "JSON.stringify({url:location.href,ready:document.readyState,ua:navigator.userAgent,model:typeof window.IronModel,app:typeof renderAll,scripts:Array.prototype.map.call(document.scripts,function(s){return s.src;})})")
        val errors = try {
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("logcat -d -t 500 -s chromium:E AndroidRuntime:E")
            ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText().takeLast(16000) }
        } catch (e: Exception) { "WebView log unavailable: ${e.javaClass.simpleName}" }
        println("IRON_WEBVIEW_STATE: $state")
        println("IRON_WEBVIEW_ERRORS: $errors")
        throw AssertionError("App script did not load after 30 seconds. State: $state; WebView errors: $errors")
    }

    @Test fun manualRunAndSleepPersistAfterActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (file in listOf("model.js", "app.js")) {
            val source = context.assets.open(file).bufferedReader().use { it.readText() }
            val modernSyntax = source.contains("??") || source.contains("?.")
            println("IRON_ASSET $file bytes=${source.length} modernSyntax=$modernSyntax")
            assertTrue("Packaged $file still contains old-WebView-incompatible syntax; check uploaded filename/path", !modernSyntax)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitApp(scenario)
            js(scenario, "localStorage.clear();manualRuns=[];autoRuns=[];manualSleep={};autoSleep=[];goal=160;renderAll();")
            js(scenario, "openRunModal();document.getElementById('runMinutes').value='38.1';saveManualRun();openSleepModal();document.getElementById('sleepHours').value='7.5';saveManualSleep();")
            assertEquals("\"38:06\"", js(scenario, "document.getElementById('homeDone').textContent"))
            assertEquals("\"121:54\"", js(scenario, "document.getElementById('homeRemain').textContent"))
            scenario.recreate()
            awaitApp(scenario)
            assertEquals("\"38:06\"", js(scenario, "document.getElementById('homeDone').textContent"))
            assertEquals("\"7.5시간\"", js(scenario, "document.getElementById('todaySleep').textContent"))
        }
    }
}
