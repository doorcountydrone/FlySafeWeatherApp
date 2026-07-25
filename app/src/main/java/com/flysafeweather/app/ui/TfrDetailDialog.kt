package com.flysafeweather.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flysafeweather.app.data.TfrData
import com.flysafeweather.app.data.TfrDetail
import com.flysafeweather.app.data.TfrNotamUrls
import com.flysafeweather.app.data.TfrService

@Composable
fun TfrDetailDialog(
    tfr: TfrData,
    tfrService: TfrService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember(tfr.notamKey) { mutableStateOf<TfrDetail?>(null) }
    var isLoadingDetail by remember(tfr.notamKey) { mutableStateOf(tfr.notamKey.isNotBlank()) }
    var detailError by remember(tfr.notamKey) { mutableStateOf<String?>(null) }

    LaunchedEffect(tfr.notamKey, tfr.title, tfr.legal) {
        if (TfrNotamUrls.normalizeNotamId(tfr.notamKey) == null &&
            TfrNotamUrls.normalizeNotamId(tfr.title) == null &&
            TfrNotamUrls.normalizeNotamId(tfr.legal) == null
        ) {
            isLoadingDetail = false
            detailError = "No NOTAM ID available for this TFR"
            return@LaunchedEffect
        }
        isLoadingDetail = true
        detailError = null
        detail = null
        try {
            detail = tfrService.fetchTfrDetail(tfr.notamKey, tfr.title, tfr.legal)
            if (detail == null) {
                detailError = "Could not load full NOTAM from FAA. Summary below is from map data."
            }
        } catch (e: Exception) {
            detailError = "Could not load full NOTAM: ${e.message}"
        } finally {
            isLoadingDetail = false
        }
    }

    val altitudeText = detail?.formattedAltitudes()
        ?: tfr.summaryAltitudes()
    val effectiveText = detail?.formattedEffectivePeriod()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TFR Details", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailSection(
                    title = "Classification",
                    body = "Type: ${tfr.type.name.replace('_', ' ')}\nReason: ${tfr.reason}"
                )

                DetailSection(
                    title = "Identifiers",
                    body = buildString {
                        if (tfr.notamKey.isNotBlank()) append("NOTAM: ${tfr.notamKey}\n")
                        if (tfr.state.isNotBlank()) append("State: ${tfr.state}\n")
                        if (tfr.cnsLocationId.isNotBlank()) append("Location ID: ${tfr.cnsLocationId}\n")
                        if (tfr.lastModified.isNotBlank()) {
                            append("Map data updated: ${tfr.lastModified}")
                        }
                    }.trim()
                )

                detail?.let { d ->
                    d.tfrTypeCode?.takeIf { it.isNotBlank() }?.let {
                        DetailSection(title = "FAA type code", body = it)
                    }
                    d.facilityId?.takeIf { it.isNotBlank() }?.let { fac ->
                        val loc = listOfNotNull(d.facilityCity, d.facilityState)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                        DetailSection(
                            title = "Facility",
                            body = if (loc.isNotBlank()) "$fac — $loc" else fac
                        )
                    }
                }

                effectiveText?.let {
                    DetailSection(title = "Effective period", body = it)
                }

                DetailSection(title = "Altitudes", body = altitudeText)

                if (isLoadingDetail) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            "Loading full NOTAM from FAA…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                detailError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (tfr.title.isNotBlank()) {
                    DetailSection(title = "Title", body = tfr.title)
                }

                if (tfr.legal.isNotBlank()) {
                    DetailSection(title = "Legal / restriction text", body = tfr.legal)
                }

                detail?.fullDescription()?.takeIf { it.isNotBlank() }?.let { full ->
                    DetailSection(title = "Full NOTAM description", body = full)
                }

                detail?.let { d ->
                    val poc = buildString {
                        d.pocName?.takeIf { it.isNotBlank() }?.let { append(it) }
                        d.pocPhone?.takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append("\n")
                            append(it)
                        }
                    }.trim()
                    if (poc.isNotBlank()) {
                        DetailSection(title = "Point of contact", body = poc)
                    }
                }

                if (tfr.notamText.isNotBlank() && tfr.legal.isBlank()) {
                    DetailSection(title = "Summary", body = tfr.notamText)
                }

                Text(
                    text = "Verify on FAA TFR website or B4UFLY before flight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            val faaUrl = tfr.faaDetailUrl()
                ?: detail?.faaDetailUrl
                ?: TfrNotamUrls.tfrListPageUrl()
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(faaUrl)))
                }
            ) {
                Text(if (faaUrl.contains("detail_")) "View on FAA" else "Open FAA TFR List")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
