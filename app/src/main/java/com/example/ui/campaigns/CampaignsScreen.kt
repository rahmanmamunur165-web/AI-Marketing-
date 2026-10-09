package com.example.ui.campaigns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.CampaignEntity
import com.example.ui.components.SaaSBadge
import com.example.ui.components.SaaSEmptyState
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.ContactFlowViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CampaignsScreen(
    viewModel: ContactFlowViewModel,
    modifier: Modifier = Modifier
) {
    val campaigns by viewModel.campaigns.collectAsState()
    val sendProgress by viewModel.sendProgress.collectAsState()
    val googleConn by viewModel.googleConnection.collectAsState()

    var showComposer by remember { mutableStateOf(false) }
    var campaignToApprove by remember { mutableStateOf<CampaignEntity?>(null) }
    var campaignForTestSend by remember { mutableStateOf<CampaignEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Outreach Dispatch Card (if sending)
        if (sendProgress.isSending || sendProgress.sentCount > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IndigoPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (sendProgress.isSending) EmeraldTertiary else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (sendProgress.isSending) "Live Gmail API Outreach" else "Outreach Summary",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (sendProgress.isSending) {
                                OutlinedButton(
                                    onClick = { viewModel.pauseCampaignDispatch() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (sendProgress.isSending) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = IndigoPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = sendProgress.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sendProgress.sentCount.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldTertiary
                                )
                                Text("Submitted to API", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sendProgress.suppressedCount.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = StatusWarning
                                )
                                Text("Suppressed / Skipped", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = sendProgress.totalCount.toString(),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = IndigoPrimary
                                )
                                Text("Total Queued", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Header and New Campaign button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Outreach Campaigns",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Compliant email outreach with explicit approval controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showComposer = true },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("btn_new_campaign")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Campaign", fontSize = 13.sp)
                }
            }
        }

        // Campaigns List
        if (campaigns.isEmpty()) {
            item {
                SaaSEmptyState(
                    icon = Icons.Default.Campaign,
                    title = "No campaigns created",
                    description = "Draft a message and select eligible public business contacts to organize your outreach."
                )
            }
        } else {
            items(campaigns) { campaign ->
                CampaignCard(
                    campaign = campaign,
                    onApproveAndSend = { campaignToApprove = campaign },
                    onTestSend = { campaignForTestSend = campaign },
                    onDelete = { viewModel.deleteCampaign(campaign.id) }
                )
            }
        }
    }

    // Composer Modal Dialog
    if (showComposer) {
        EmailComposerDialog(
            viewModel = viewModel,
            onDismiss = { showComposer = false }
        )
    }

    // Explicit Send Approval Confirmation Dialog
    if (campaignToApprove != null) {
        val camp = campaignToApprove!!
        var consentChecked by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { campaignToApprove = null },
            icon = { Icon(Icons.Default.Send, contentDescription = null, tint = IndigoPrimary) },
            title = { Text("Approve Campaign Outreach") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are preparing to send '${camp.name}' to ${camp.totalRecipients} selected recipients via your connected Gmail account (${googleConn?.googleAccountEmail ?: "Default Account"}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Rate limit of ~2 emails/sec will be applied to prevent mailbox flags.\n• All active suppressions and unsubscribes are automatically enforced.\n• Messages are logged as 'SENT_SUBMITTED' through the official Gmail API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary),
                            modifier = Modifier.testTag("cb_campaign_consent")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I explicitly approve sending this outreach batch and confirm compliance with applicable communication policies.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = camp.id
                        campaignToApprove = null
                        viewModel.approveAndDispatchCampaign(id)
                    },
                    enabled = consentChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    modifier = Modifier.testTag("btn_confirm_approve_send")
                ) {
                    Text("Approve & Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { campaignToApprove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Test Send Dialog
    if (campaignForTestSend != null) {
        val camp = campaignForTestSend!!
        val defaultEmail = googleConn?.googleAccountEmail ?: "your-email@example.com"
        var testEmailInput by remember { mutableStateOf(defaultEmail) }

        AlertDialog(
            onDismissRequest = { campaignForTestSend = null },
            icon = { Icon(Icons.Default.Mail, contentDescription = null, tint = IndigoPrimary) },
            title = { Text("Send Test Email") },
            text = {
                Column {
                    Text(
                        text = "Send a test copy of '${camp.subject}' to verify formatting and sender appearance before dispatching to recipients.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = testEmailInput,
                        onValueChange = { testEmailInput = it },
                        label = { Text("Your Verified Test Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_test_email")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendTestEmail(camp, testEmailInput.trim())
                        campaignForTestSend = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    modifier = Modifier.testTag("btn_send_test_email")
                ) {
                    Text("Send Test")
                }
            },
            dismissButton = {
                TextButton(onClick = { campaignForTestSend = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CampaignCard(
    campaign: CampaignEntity,
    onApproveAndSend: () -> Unit,
    onTestSend: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val dateStr = dateFormat.format(Date(campaign.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = campaign.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Subject: ${campaign.subject}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                SaaSBadge(text = campaign.status, type = campaign.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats breakdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text("Recipients: ${campaign.totalRecipients}", style = MaterialTheme.typography.labelSmall)
                Text("Sent: ${campaign.sentCount}", style = MaterialTheme.typography.labelSmall, color = EmeraldTertiary)
                Text("Suppressed: ${campaign.suppressedCount}", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                Text("Failed: ${campaign.failedCount}", style = MaterialTheme.typography.labelSmall, color = if (campaign.failedCount > 0) StatusError else Color.Gray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Created $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTestSend,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_test_send_${campaign.id}")
                    ) {
                        Text("Test Send", fontSize = 12.sp)
                    }

                    if (campaign.status == "DRAFT" || campaign.status == "CANCELLED") {
                        Button(
                            onClick = onApproveAndSend,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_approve_campaign_${campaign.id}")
                        ) {
                            Text("Review & Send", fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
