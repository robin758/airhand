package app.airhand.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityAdvisorTest {
    @Test
    fun bitrateSliderMappingAndFormatting() {
        assertEquals(QualityProfile.MIN_KBPS, QualityProfile.sliderToKbps(0f))
        assertEquals(QualityProfile.MAX_KBPS, QualityProfile.sliderToKbps(1f))

        // 单调递增且代表档位往返准确
        var prev = 0
        for (i in 0..100) {
            val kbps = QualityProfile.sliderToKbps(i / 100f)
            assertTrue("slider should be monotonic at $i: $prev -> $kbps", kbps >= prev)
            prev = kbps
        }
        for (v in listOf(500, 1200, 4000, 5000, 8000, 16000, 30000, 100000)) {
            val roundTrip = QualityProfile.sliderToKbps(QualityProfile.kbpsToSlider(v))
            assertEquals("round-trip for $v kbps", v, roundTrip)
        }

        assertEquals("500 kbps", QualityProfile.formatBitrate(500))
        assertEquals("1.2 Mbps", QualityProfile.formatBitrate(1_200))
        assertEquals("4 Mbps", QualityProfile.formatBitrate(4_000))
        assertEquals("5.5 Mbps", QualityProfile.formatBitrate(5_500))
        assertEquals("16 Mbps", QualityProfile.formatBitrate(16_000))
        assertEquals("100 Mbps", QualityProfile.formatBitrate(100_000))

        assertEquals("720p", QualityProfile.formatResolution(720, "Native"))
        assertEquals("1080p", QualityProfile.formatResolution(1080, "Native"))
        assertEquals("1440p", QualityProfile.formatResolution(1440, "Native"))
        assertEquals("4K", QualityProfile.formatResolution(2160, "Native"))
        assertEquals("Native", QualityProfile.formatResolution(0, "Native"))
    }

    @Test
    fun unknownStatsReturnsBaselineSmooth60() {
        val rec = QualityAdvisor.recommendSmooth60(QualityAdvisor.Context(stats = null, decoder = null))
        assertEquals(QualityAdvisor.LinkTier.UNKNOWN, rec.tier)
        assertEquals(QualityProfile(60, 5_000, 1080, true), rec.profile)
    }

    @Test
    fun recommendsByNetworkLinkTier() {
        val hwDec = DecoderStats(Codec.H265, "c2.hw.hevc", hardware = true, decodeMs = 4.2f)

        val directFast = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 58f, kbps = 12_000f, rttMs = 8f, width = 2560, height = 1440, direct = true, jitterMs = 1.2f, lossPct = 0f),
                decoder = hwDec,
                screenShortPx = 1200,
                hostShortPx = 1912,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.DIRECT_FAST, directFast.tier)
        assertEquals(QualityProfile(60, 16_000, 1440, true), directFast.profile)

        val directFair = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 55f, kbps = 8_000f, rttMs = 42f, width = 1920, height = 1080, direct = true, jitterMs = 5f, lossPct = 0.2f),
                decoder = hwDec,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.DIRECT_FAIR, directFair.tier)
        assertEquals(QualityProfile(60, 10_000, 1080, true), directFair.profile)

        val relayGood = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 46f, kbps = 5_000f, rttMs = 38f, width = 1920, height = 1080, direct = false, jitterMs = 4f, lossPct = 0.1f),
                decoder = hwDec,
                screenShortPx = 1080,
                hostShortPx = 1912,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.RELAY_GOOD, relayGood.tier)
        assertEquals(QualityProfile(60, 5_000, 1080, true), relayGood.profile)

        val relayFair = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 32f, kbps = 4_000f, rttMs = 85f, width = 1920, height = 1080, direct = false, jitterMs = 12f, lossPct = 0.6f),
                decoder = hwDec,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.RELAY_FAIR, relayFair.tier)
        assertEquals(QualityProfile(60, 3_000, 720, true), relayFair.profile)

        val congested = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 24f, kbps = 3_000f, rttMs = 180f, width = 1920, height = 1080, direct = false, jitterMs = 20f, lossPct = 4.5f),
                decoder = hwDec,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.CONGESTED, congested.tier)
        assertEquals(QualityProfile(60, 2_500, 720, true), congested.profile)

        val severelyCongested = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = Stats(fps = 18f, kbps = 2_000f, rttMs = 290f, width = 1280, height = 720, direct = false, jitterMs = 40f, lossPct = 10f),
                decoder = hwDec,
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.CONGESTED, severelyCongested.tier)
        assertEquals(QualityProfile(60, 1_800, 720, true), severelyCongested.profile)
    }

    @Test
    fun capsRecommendationForSlowDecoderOrSmallDisplay() {
        val fastStats = Stats(fps = 60f, kbps = 16_000f, rttMs = 6f, width = 2560, height = 1440, direct = true)

        val swDec = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = fastStats,
                decoder = DecoderStats(Codec.H265, "c2.android.hevc.decoder", hardware = false, decodeMs = 9f),
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.DECODER_LIMITED, swDec.tier)
        assertEquals(QualityProfile(60, 4_000, 720, true), swDec.profile)

        val slowHwDec = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = fastStats,
                decoder = DecoderStats(Codec.H265, "c2.hw.hevc", hardware = true, decodeMs = 11.5f),
            ),
        )
        assertEquals(QualityAdvisor.LinkTier.DECODER_LIMITED, slowHwDec.tier)
        assertEquals(QualityProfile(60, 8_000, 1080, true), slowHwDec.profile)

        val smallPhone = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = fastStats,
                decoder = DecoderStats(Codec.H265, "c2.hw.hevc", hardware = true, decodeMs = 3.5f),
                screenShortPx = 720,
            ),
        )
        assertEquals(QualityProfile(60, 6_000, 720, true), smallPhone.profile)

        val host1080p = QualityAdvisor.recommendSmooth60(
            QualityAdvisor.Context(
                stats = fastStats,
                decoder = DecoderStats(Codec.H265, "c2.hw.hevc", hardware = true, decodeMs = 3.5f),
                screenShortPx = 1440,
                hostShortPx = 1080,
            ),
        )
        assertEquals(1080, host1080p.profile.maxShort)
        assertEquals(60, host1080p.profile.fps)
    }
}
