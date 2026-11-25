package app.airhand.android.ui.session

import app.airhand.android.data.WheelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外接鼠标的滚轮换算成发给对方的量（120 = 一格） */
class MouseWheelTest {
    @Test
    fun oneNotchIs120() {
        val w = MouseWheel()
        assertEquals(0 to 120, w.steps(0f, 1f))
        assertEquals(-120 to 0, w.steps(-1f, 0f))
    }

    /** 高精度滚轮一次只滚一点点：不够 1 的攒着，不会丢 */
    @Test
    fun fractionsAddUp() {
        val w = MouseWheel()
        assertNull(w.steps(0f, 0.005f))
        assertEquals(0 to 1, w.steps(0f, 0.005f))
    }

    /** 换方向时丢掉反方向的余量 */
    @Test
    fun changingDirectionDropsTheRest() {
        val w = MouseWheel()
        assertNull(w.steps(0f, 0.005f))
        assertEquals(0 to -1, w.steps(0f, -0.01f))
    }

    @Test
    fun reverseAndSpeed() {
        val w = MouseWheel()
        w.reverse = true
        w.percent = 50
        assertEquals(-60 to -60, w.steps(1f, 1f))
        w.reverse = false
        w.percent = 200
        assertEquals(0 to 240, w.steps(0f, 1f))
        // 支持低至 10% 的精细速度
        w.percent = 20
        assertEquals(0 to 24, w.steps(0f, 1f))
    }

    /** 自适应模式：慢滚单格精细（0.5×），连续快速拨动平滑加速，换方向立即回到精细步进 */
    @Test
    fun adaptiveModeIsGentleSlowAndFasterFast() {
        val w = MouseWheel()
        w.mode = WheelMode.ADAPTIVE
        w.percent = 100
        // 慢滚第一格：120 × 0.5 = 60
        assertEquals(0 to 60, w.steps(0f, 1f, nowMs = 1000L))
        // 超过重置间隔再滚：依然 0.5×
        assertEquals(0 to 60, w.steps(0f, 1f, nowMs = 1500L))
        // 连续快速拨动（50ms 一格）：增速明显大于慢滚
        val fast1 = w.steps(0f, 1f, nowMs = 1550L)!!.second
        val fast2 = w.steps(0f, 1f, nowMs = 1600L)!!.second
        assertTrue("fast1=$fast1 should exceed 60", fast1 > 100)
        assertTrue("fast2=$fast2 should stay fast", fast2 >= fast1)
        // 反方向立刻回落到 0.5×（-60）
        assertEquals(0 to -60, w.steps(0f, -1f, nowMs = 1640L))
    }

    /** 平滑滚轮插值器：总增量守恒、首步立即发出、换方向丢弃旧余量 */
    @Test
    fun smoothWheelPreservesTotalAndDropsOnReverse() {
        val timers = GesturesTest.FakeTimers()
        val sent = ArrayList<Pair<Int, Int>>()
        val sw = SmoothWheel(timers) { dx, dy -> sent += dx to dy }
        sw.add(0, 60)
        assertTrue("first micro-step emitted immediately", sent.isNotEmpty() && sent.first().second in 1..59)
        timers.advance(500L)
        assertEquals(60, sent.sumOf { it.second })

        // 换方向立即清空反方向剩余量
        sent.clear()
        sw.add(0, 100)
        sw.add(0, -30)
        timers.advance(500L)
        assertTrue("reverse step emitted negative values", sent.last().second < 0)
    }
}
