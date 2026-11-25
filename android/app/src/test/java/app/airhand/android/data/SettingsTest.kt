package app.airhand.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    /** 拖动窗口时显示本地光标：默认打开（保存的设置里没有这一项也是打开），关掉之后记得住 */
    @Test
    fun localCursorIsOnByDefaultAndRemembered() {
        assertTrue(Settings().localCursor)
        assertTrue(Settings.parse(JSONObject().put("navKeys", false)).localCursor)
        val off = Settings(localCursor = false)
        assertFalse(Settings.parse(off.toJson()).localCursor)
        assertEquals(off, Settings.parse(off.toJson()))
    }

    /** 外接鼠标滚轮与双指滑动偏好：默认值、范围校验与持久化 */
    @Test
    fun wheelPreferences() {
        val old = Settings.parse(JSONObject())
        assertFalse(old.reverseWheel)
        assertEquals(100, old.wheelSpeed)
        assertEquals(WheelMode.ADAPTIVE, old.wheelMode)
        assertFalse(old.smoothWheel)
        assertEquals(100, old.touchScrollSpeed)
        assertFalse(old.touchScrollFling)
        val custom = Settings(
            reverseWheel = true,
            wheelSpeed = 5,
            wheelMode = WheelMode.LINEAR,
            smoothWheel = true,
            touchScrollSpeed = 10,
            touchScrollFling = true,
        )
        assertEquals(custom, Settings.parse(custom.toJson()))
        assertEquals(100, Settings.parse(JSONObject().put("wheelSpeed", 0)).wheelSpeed)
        assertEquals(100, Settings.parse(JSONObject().put("touchScrollSpeed", 1)).touchScrollSpeed)
    }

    /** 顶部状态浮窗、自动旋转与双击浮标开关键盘：默认值与持久化 */
    @Test
    fun displayAndInteractionPreferences() {
        val def = Settings()
        assertFalse(def.showStats)
        assertTrue(def.autoRotate)
        assertFalse(def.doubleTapKeyboard)
        val parsedEmpty = Settings.parse(JSONObject())
        assertFalse(parsedEmpty.showStats)
        assertTrue(parsedEmpty.autoRotate)
        assertFalse(parsedEmpty.doubleTapKeyboard)
        val custom = Settings(showStats = true, autoRotate = false, doubleTapKeyboard = true)
        assertEquals(custom, Settings.parse(custom.toJson()))
    }
}
