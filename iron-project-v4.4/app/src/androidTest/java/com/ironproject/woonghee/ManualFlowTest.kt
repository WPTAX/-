package com.ironproject.woonghee

import android.view.ViewGroup
import android.webkit.WebView
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

    @Test fun manualRunAndSleepPersistAfterActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            for (attempt in 0 until 50) {
                if (js(scenario, "typeof window.IronModel === 'object' && typeof renderAll === 'function'") == "true") {
                    ready = true
                    break
                }
                Thread.sleep(100)
            }
            assertTrue("App script did not load", ready)
            js(scenario, "localStorage.clear();manualRuns=[];autoRuns=[];manualSleep={};autoSleep=[];goal=160;renderAll();")
            js(scenario, "openRunModal();document.getElementById('runMinutes').value='38.1';saveManualRun();openSleepModal();document.getElementById('sleepHours').value='7.5';saveManualSleep();")
            assertEquals("\"38:06\"", js(scenario, "document.getElementById('homeDone').textContent"))
            assertEquals("\"121:54\"", js(scenario, "document.getElementById('homeRemain').textContent"))
            scenario.recreate()
            Thread.sleep(1000)
            assertEquals("\"38:06\"", js(scenario, "document.getElementById('homeDone').textContent"))
            assertEquals("\"7.5시간\"", js(scenario, "document.getElementById('todaySleep').textContent"))
        }
    }
}
