package app.airhand.android.core

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 智能 60fps 流畅画质推荐（纯逻辑，不直接修改底层链路或静默重建编码器）。
 * 依据当前直连/中继链路质量、本机解码能力和屏幕尺寸，计算出最适合稳定 60fps 的 (分辨率, 码率上限, 自适应)。
 */
object QualityAdvisor {
    enum class LinkTier {
        DIRECT_FAST,
        DIRECT_FAIR,
        RELAY_GOOD,
        RELAY_FAIR,
        CONGESTED,
        DECODER_LIMITED,
        UNKNOWN,
    }

    data class Context(
        val stats: Stats?,
        val decoder: DecoderStats?,
        /** 本机屏幕短边像素（<= 0 表示未知） */
        val screenShortPx: Int = 0,
        /** 被控端当前显示器短边像素（<= 0 表示未知） */
        val hostShortPx: Int = 0,
    )

    data class Recommendation(
        val profile: QualityProfile,
        val tier: LinkTier,
        val direct: Boolean?,
        val rttMs: Int?,
        val decodeMs: Float?,
        val hardwareDecoder: Boolean?,
    )

    fun recommendSmooth60(ctx: Context): Recommendation {
        val st = ctx.stats?.takeIf { it.width > 0 }
        val dec = ctx.decoder
        if (st == null) {
            return Recommendation(
                profile = QualityProfile(fps = 60, kbps = 5_000, maxShort = 1080, adaptive = true),
                tier = LinkTier.UNKNOWN,
                direct = null,
                rttMs = null,
                decodeMs = dec?.decodeMs,
                hardwareDecoder = dec?.hardware,
            )
        }

        val rtt = st.rttMs
        val jitter = st.jitterMs
        val loss = st.lossPct

        var maxShort: Int
        var kbps: Int
        var tier: LinkTier

        when {
            loss >= 8.0f || (rtt != null && rtt >= 250f) -> {
                maxShort = 720
                kbps = 1_800
                tier = LinkTier.CONGESTED
            }
            loss >= 3.0f || (rtt != null && rtt >= 150f) || jitter >= 30f -> {
                maxShort = 720
                kbps = 2_500
                tier = LinkTier.CONGESTED
            }
            st.direct && (rtt == null || rtt < 25f) && jitter < 8f && loss < 0.5f -> {
                maxShort = 1440
                kbps = 16_000
                tier = LinkTier.DIRECT_FAST
            }
            st.direct -> {
                maxShort = 1080
                kbps = 10_000
                tier = LinkTier.DIRECT_FAIR
            }
            (rtt == null || rtt < 60f) && jitter < 15f && loss < 1.0f -> {
                maxShort = 1080
                kbps = 5_000
                tier = LinkTier.RELAY_GOOD
            }
            else -> {
                maxShort = 720
                kbps = 3_000
                tier = LinkTier.RELAY_FAIR
            }
        }

        // 本机解码能力保护：软件解码或解码耗时接近 60fps 帧预算（16.6ms）时主动封顶
        if (dec != null) {
            val decodeMs = dec.decodeMs ?: 0f
            if (!dec.hardware || decodeMs >= 14f) {
                maxShort = minNonZero(maxShort, 720)
                kbps = min(kbps, 4_000)
                if (tier != LinkTier.CONGESTED) tier = LinkTier.DECODER_LIMITED
            } else if (decodeMs >= 10f) {
                val cappedShort = minNonZero(maxShort, 1080)
                val cappedKbps = min(kbps, 8_000)
                if (cappedShort != maxShort || cappedKbps != kbps) {
                    maxShort = cappedShort
                    kbps = cappedKbps
                    if (tier != LinkTier.CONGESTED) tier = LinkTier.DECODER_LIMITED
                }
            }
        }

        // 屏幕与被控端分辨率保护：避免给小屏或低分辨率显示器浪费编码预算
        if (ctx.screenShortPx in 1..800) {
            maxShort = minNonZero(maxShort, 720)
            kbps = min(kbps, 6_000)
        } else if (ctx.screenShortPx in 801..1150 && !st.direct) {
            maxShort = minNonZero(maxShort, 1080)
        }

        if (ctx.hostShortPx in 1..800) {
            maxShort = minNonZero(maxShort, 720)
        } else if (ctx.hostShortPx in 801..1150) {
            maxShort = minNonZero(maxShort, 1080)
        }

        return Recommendation(
            profile = QualityProfile(fps = 60, kbps = kbps, maxShort = maxShort, adaptive = true),
            tier = tier,
            direct = st.direct,
            rttMs = rtt?.roundToInt(),
            decodeMs = dec?.decodeMs,
            hardwareDecoder = dec?.hardware,
        )
    }

    private fun minNonZero(a: Int, b: Int): Int = when {
        a == 0 -> b
        b == 0 -> a
        else -> min(a, b)
    }
}
