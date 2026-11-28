package app.airhand.android.data

import app.airhand.android.core.Preset
import app.airhand.android.core.QualityProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** 自定义画质与直连/中继分档：旧配置向后兼容、往返序列化、越界值回退 */
    @Test
    fun customQualityAndSplitPathProfiles() {
        val def = Settings()
        assertNull(def.customQuality)
        assertFalse(def.splitPathQuality)
        assertEquals(Preset.BALANCED.profile(), def.effectiveQuality())
        assertEquals(Preset.BALANCED.profile(), def.startupQuality())
        assertEquals(QualityProfile.DEFAULT_DIRECT, def.directQuality)
        assertEquals(QualityProfile.DEFAULT_RELAY, def.relayQuality)

        // 旧版 settings.json 只有 preset 字段：完全保持原预设行为
        val legacy = Settings.parse(JSONObject().put("preset", "hd"))
        assertNull(legacy.customQuality)
        assertFalse(legacy.splitPathQuality)
        assertEquals(Preset.HD.profile(), legacy.effectiveQuality())
        assertEquals(Preset.HD.profile(), legacy.startupQuality())
        assertEquals(Preset.HD.profile(), legacy.qualityFor(direct = true))
        assertEquals(Preset.HD.profile(), legacy.qualityFor(direct = false))

        // 自定义全局画质往返
        val customGlobal = def.withGlobalQuality(QualityProfile(fps = 60, kbps = 5_000, maxShort = 1080, adaptive = true))
        assertEquals(QualityProfile(60, 5_000, 1080, true), customGlobal.customQuality)
        assertEquals(customGlobal, Settings.parse(customGlobal.toJson()))

        // 选回标准预设且开启自适应：同步写回 preset 并清空 customQuality
        val backToPreset = customGlobal.withGlobalQuality(Preset.SMOOTH.profile(adaptive = true))
        assertEquals("smooth", backToPreset.preset)
        assertNull(backToPreset.customQuality)

        // 同预设但关闭自适应（固定码率）：保留 customQuality 以记住自适应开关
        val fixedBitrate = def.withGlobalQuality(Preset.SMOOTH.profile(adaptive = false))
        assertEquals(QualityProfile(60, 8_000, 1080, false), fixedBitrate.customQuality)
        assertEquals(fixedBitrate, Settings.parse(fixedBitrate.toJson()))

        // 直连 / 中继独立画质分档：起步走中继档，按 direct 分别读取
        val split = Settings(
            splitPathQuality = true,
            directQuality = QualityProfile(60, 20_000, 1440, true),
            relayQuality = QualityProfile(60, 3_500, 720, false),
        )
        assertEquals(split.relayQuality, split.startupQuality())
        assertEquals(split.directQuality, split.qualityFor(direct = true))
        assertEquals(split.relayQuality, split.qualityFor(direct = false))
        assertEquals(split, Settings.parse(split.toJson()))

        // 非法或越界字段安全回退默认值，不抛出异常
        val corrupted = JSONObject()
            .put("preset", "smooth")
            .put("customQuality", JSONObject().put("fps", 999).put("kbps", -1).put("maxShort", 120).put("adaptive", false))
            .put("directQuality", JSONObject().put("fps", 0).put("kbps", 999_999).put("maxShort", -5))
            .put("relayQuality", JSONObject().put("fps", 60).put("kbps", 4_500).put("maxShort", 0))
        val parsed = Settings.parse(corrupted)
        assertEquals(QualityProfile(60, 8_000, 1080, false), parsed.customQuality)
        assertEquals(QualityProfile.DEFAULT_DIRECT, parsed.directQuality)
        assertEquals(QualityProfile(60, 4_500, 0, true), parsed.relayQuality)
    }
}
