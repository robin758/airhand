package app.airhand.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.airhand.android.R
import app.airhand.android.core.Preset
import app.airhand.android.core.QualityAdvisor
import app.airhand.android.core.QualityProfile
import app.airhand.android.data.Settings
import app.airhand.android.ui.phone.presetName
import kotlinx.coroutines.delay

/** 单行画质参数摘要（例：1080p · 60 fps · 5 Mbps · 自适应） */
@Composable
fun qualitySummary(profile: QualityProfile, includeAdaptive: Boolean = true): String {
    val res = QualityProfile.formatResolution(profile.maxShort, stringResource(R.string.quality_res_native))
    val base = "$res · ${profile.fps} fps · ${QualityProfile.formatBitrate(profile.kbps)}"
    if (!includeAdaptive) return base
    val mode = stringResource(if (profile.adaptive) R.string.quality_adaptive_tag else R.string.quality_fixed_tag)
    return "$base · $mode"
}

/** 设置首页“视频与画面”分类摘要用的短标签 */
@Composable
fun qualitySettingsSummary(settings: Settings): String {
    if (settings.splitPathQuality) return stringResource(R.string.quality_split_summary)
    val p = settings.effectiveQuality()
    val matched = Preset.match(p)
    return if (matched != null && p.adaptive) {
        presetName(matched)
    } else {
        qualitySummary(p, includeAdaptive = false)
    }
}

/** 会话内：智能 60fps 流畅档预览 + 一键应用卡片 */
@Composable
fun SmartQualityCard(
    recommendation: QualityAdvisor.Recommendation,
    current: QualityProfile,
    onApply: (QualityProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val applied = current == recommendation.profile
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Speed,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.quality_smart_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    qualitySummary(recommendation.profile, includeAdaptive = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
                Text(
                    recommendationReason(recommendation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onApply(recommendation.profile) },
                enabled = !applied,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(if (applied) R.string.quality_smart_applied else R.string.quality_smart_apply))
            }
        }
    }
}

@Composable
private fun recommendationReason(rec: QualityAdvisor.Recommendation): String {
    val hwLabel = stringResource(if (rec.hardwareDecoder == false) R.string.software else R.string.hardware)
    val pathLabel = stringResource(if (rec.direct == true) R.string.path_direct else R.string.path_relayed)
    return when (rec.tier) {
        QualityAdvisor.LinkTier.DIRECT_FAST -> rec.rttMs?.let {
            stringResource(R.string.quality_smart_reason_direct_fast, it, hwLabel)
        } ?: stringResource(R.string.quality_smart_reason_no_rtt, pathLabel, hwLabel)
        QualityAdvisor.LinkTier.DIRECT_FAIR -> rec.rttMs?.let {
            stringResource(R.string.quality_smart_reason_direct_fair, it)
        } ?: stringResource(R.string.quality_smart_reason_no_rtt, pathLabel, hwLabel)
        QualityAdvisor.LinkTier.RELAY_GOOD -> rec.rttMs?.let {
            stringResource(R.string.quality_smart_reason_relay_good, it)
        } ?: stringResource(R.string.quality_smart_reason_no_rtt, pathLabel, hwLabel)
        QualityAdvisor.LinkTier.RELAY_FAIR -> rec.rttMs?.let {
            stringResource(R.string.quality_smart_reason_relay_fair, it)
        } ?: stringResource(R.string.quality_smart_reason_no_rtt, pathLabel, hwLabel)
        QualityAdvisor.LinkTier.CONGESTED -> stringResource(R.string.quality_smart_reason_congested)
        QualityAdvisor.LinkTier.DECODER_LIMITED -> stringResource(R.string.quality_smart_reason_decoder, hwLabel)
        QualityAdvisor.LinkTier.UNKNOWN -> stringResource(R.string.quality_smart_reason_unknown)
    }
}

/**
 * 首页「设置 → 视频与画面」与会话底部菜单「视频与画面」共用的画质编辑区：
 * - 直连 / 中继独立画质分档开关 + 链路切换
 * - 5 个预设快捷芯片 + 自定义状态指示
 * - 自定义分辨率 / 帧率 / 对数码率滑块（会话内 250ms 防抖）/ 自适应码率开关
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QualityEditor(
    settings: Settings,
    /** 当前会话实时链路（true=直连，false=中继，首页未连接时为 null） */
    liveDirect: Boolean?,
    /** 会话实时生效画质（首页未连接时传 null） */
    liveProfile: QualityProfile?,
    /** 拖动码率滑块时的防抖发送间隔（毫秒；首页传 0 仅松手保存，会话传 250） */
    debounceMs: Long,
    onUpdateGlobal: (QualityProfile) -> Unit,
    onToggleSplitPath: (Boolean) -> Unit,
    onUpdatePathProfile: (direct: Boolean, QualityProfile) -> Unit,
) {
    // 开启分档时：会话默认定位到当前链路，首页默认定位到直连档
    var editDirect by rememberSaveable { mutableStateOf(liveDirect ?: true) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.quality_split_path)) },
        supportingContent = { Text(stringResource(R.string.quality_split_path_desc)) },
        trailingContent = {
            Switch(
                checked = settings.splitPathQuality,
                onCheckedChange = onToggleSplitPath,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSplitPath(!settings.splitPathQuality) }
            .padding(horizontal = 8.dp),
    )

    if (settings.splitPathQuality) {
        val currentBadge = stringResource(R.string.quality_path_current_badge)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val directTitle = stringResource(R.string.quality_path_direct_tab) +
                if (liveDirect == true) " · $currentBadge" else ""
            val relayTitle = stringResource(R.string.quality_path_relayed_tab) +
                if (liveDirect == false) " · $currentBadge" else ""
            FilterChip(
                selected = editDirect,
                onClick = { editDirect = true },
                label = { Text(directTitle) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = !editDirect,
                onClick = { editDirect = false },
                label = { Text(relayTitle) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val profile = when {
        !settings.splitPathQuality -> liveProfile ?: settings.effectiveQuality()
        liveProfile != null && liveDirect != null && editDirect == liveDirect -> liveProfile
        editDirect -> settings.directQuality
        else -> settings.relayQuality
    }

    val emit: (QualityProfile) -> Unit = { next ->
        val clean = next.sanitized()
        if (settings.splitPathQuality) onUpdatePathProfile(editDirect, clean) else onUpdateGlobal(clean)
    }

    val matchedPreset = Preset.match(profile)
    val isCustom = matchedPreset == null

    // 当前参数摘要
    Text(
        qualitySummary(profile),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )

    // 预设快捷芯片 + 自定义状态芯片
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Preset.entries.forEach { p ->
            FilterChip(
                selected = matchedPreset == p,
                onClick = { emit(p.profile(adaptive = true)) },
                label = { Text(presetName(p)) },
            )
        }
        if (isCustom) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text(stringResource(R.string.quality_custom)) },
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    // 分辨率短边
    Text(
        stringResource(R.string.quality_resolution),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
    )
    val nativeLabel = stringResource(R.string.quality_res_native)
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QualityProfile.MAX_SHORT_OPTIONS.forEach { shortSide ->
            FilterChip(
                selected = profile.maxShort == shortSide,
                onClick = { if (profile.maxShort != shortSide) emit(profile.copy(maxShort = shortSide)) },
                label = { Text(QualityProfile.formatResolution(shortSide, nativeLabel)) },
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    // 帧率
    Text(
        stringResource(R.string.quality_fps),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
    )
    FlowRow(
        Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QualityProfile.FPS_OPTIONS.forEach { fps ->
            FilterChip(
                selected = profile.fps == fps,
                onClick = { if (profile.fps != fps) emit(profile.copy(fps = fps)) },
                label = { Text("$fps") },
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    // 对数码率上限滑块
    var dragging by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableFloatStateOf(QualityProfile.kbpsToSlider(profile.kbps)) }
    LaunchedEffect(profile.kbps, editDirect, settings.splitPathQuality) {
        if (!dragging) sliderPos = QualityProfile.kbpsToSlider(profile.kbps)
    }
    LaunchedEffect(sliderPos, dragging, debounceMs) {
        if (!dragging || debounceMs <= 0L) return@LaunchedEffect
        val target = QualityProfile.sliderToKbps(sliderPos)
        if (target == profile.kbps) return@LaunchedEffect
        delay(debounceMs)
        emit(profile.copy(kbps = target))
    }
    val shownKbps = if (dragging) QualityProfile.sliderToKbps(sliderPos) else profile.kbps

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.quality_bitrate),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                QualityProfile.formatBitrate(shownKbps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = sliderPos,
            onValueChange = {
                dragging = true
                sliderPos = it
            },
            onValueChangeFinished = {
                val target = QualityProfile.sliderToKbps(sliderPos)
                dragging = false
                if (target != profile.kbps) emit(profile.copy(kbps = target))
            },
            valueRange = 0f..1f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                QualityProfile.formatBitrate(QualityProfile.MIN_KBPS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                QualityProfile.formatBitrate(QualityProfile.MAX_KBPS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 自适应码率开关
    ListItem(
        headlineContent = { Text(stringResource(R.string.quality_adaptive)) },
        supportingContent = { Text(stringResource(R.string.quality_adaptive_desc)) },
        trailingContent = {
            Switch(
                checked = profile.adaptive,
                onCheckedChange = { emit(profile.copy(adaptive = it)) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { emit(profile.copy(adaptive = !profile.adaptive)) }
            .padding(horizontal = 8.dp),
    )
}
