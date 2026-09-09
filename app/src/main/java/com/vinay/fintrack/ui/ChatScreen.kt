package com.vinay.fintrack.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel

/**
 * Ask anything about the data, or tell it to change something. The assistant
 * reads and writes through the same paths the screens use, so what it does here
 * shows up there.
 */
@Composable
fun ChatScreen(vm: FinTrackViewModel) {
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                vm.chatInput = spokenText
            }
        }
    }

    val triggerSpeechRecognition = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to ask FinTrack...")
        }
        runCatching {
            speechRecognizerLauncher.launch(intent)
        }.onFailure {
            android.widget.Toast.makeText(context, "Voice input not supported on this device.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Follow the conversation as it grows.
    LaunchedEffect(vm.chat.size, vm.chatBusy) {
        if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.size)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3)
        ) {
            if (vm.chat.isEmpty()) item { Intro(vm) }

            items(vm.chat) { m ->
                val fromUser = m.role == "user"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!fromUser) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Pf.Accent.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Pf.Accent.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                Modifier.size(15.dp),
                                tint = Pf.Accent400
                            )
                        }
                        Spacer(Modifier.width(Space.s2))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(0.84f)
                            .background(
                                if (fromUser) Brush.linearGradient(listOf(Color(0xFF673AB7), Color(0xFF7E57C2)))
                                else Brush.linearGradient(listOf(Pf.Surface, Pf.Surface2)),
                                Radius.Lg
                            )
                            .border(
                                1.dp,
                                if (fromUser) Color.Transparent else Pf.Hairline,
                                Radius.Lg
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            m.text,
                            color = if (fromUser) Color.White else Pf.Text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Says what it is doing rather than just that it is busy: the reply
            // lands in one piece at the end, so several seconds of blank screen
            // otherwise looks like nothing is happening.
            if (vm.chatBusy) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        modifier = Modifier.padding(start = 36.dp, top = Space.s1)
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(Pf.Accent400, CircleShape)
                        )
                        Muted(
                            vm.chatStatus.ifEmpty { "Thinking…" },
                            size = 12
                        )
                    }
                }
            }

            // A deletion waits here for a tap. The model can ask; only this
            // button does it.
            vm.pendingDeletion?.let { p ->
                item {
                    Column(
                        Modifier
                            .padding(top = Space.s2)
                            .fillMaxWidth()
                            .background(Pf.Surface2, Radius.Lg)
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), Radius.Lg)
                            .padding(Space.s4)
                    ) {
                        Text(
                            "Delete ${p.what}?",
                            color = Pf.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Muted(p.detail, Modifier.padding(top = 2.dp, bottom = Space.s3))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            PrimaryButton("Delete", vm::confirmDeletion)
                            SecondaryButton("Keep it", vm::cancelDeletion)
                        }
                    }
                }
            }

            // The question survives a failure, so it can go again as it was.
            if (vm.failedMessage.isNotBlank() && !vm.chatBusy) {
                item {
                    Row(
                        Modifier.padding(top = Space.s2),
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        PrimaryButton("Try again", vm::retryChat)
                        SecondaryButton("Let it go", vm::dismissRetry)
                    }
                }
            }

            // Only after something was actually changed.
            if (vm.canUndoAssistant && !vm.chatBusy) {
                item {
                    Row(
                        Modifier.padding(top = Space.s2),
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        SecondaryButton("Undo that change", { vm.undoAssistant() })
                    }
                }
            }
        }

        // Quick prompt recommendation pills
        if (vm.chatReady && !vm.chatBusy) {
            val suggestions = listOf(
                "💰 Spent this month",
                "💳 Credit card dues",
                "📊 Joint balance",
                "🚗 Active loan EMIs",
                "💡 Subscriptions"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.s3, vertical = Space.s1),
                horizontalArrangement = Arrangement.spacedBy(Space.s2)
            ) {
                suggestions.forEach { prompt ->
                    Box(
                        Modifier
                            .background(Pf.Surface2, Radius.Pill)
                            .border(1.dp, Pf.Hairline, Radius.Pill)
                            .clickable {
                                vm.chatInput = prompt.substringAfter(" ")
                                vm.sendChat()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(prompt, color = Pf.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.s3),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            PfField(
                value = vm.chatInput,
                onValueChange = { vm.chatInput = it },
                placeholder = if (vm.chatReady) "Ask or tell me anything…"
                else "Add an OpenAI key in Settings",
                modifier = Modifier.weight(1f),
                singleLine = false
            )
            if (vm.chatReady && !vm.chatBusy) {
                IconButton(
                    onClick = { triggerSpeechRecognition() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Pf.Surface2, Radius.Pill)
                        .border(1.dp, Pf.Accent.copy(alpha = 0.4f), Radius.Pill)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        Modifier.size(20.dp),
                        tint = Pf.Accent400
                    )
                }
            }
            IconButton(
                onClick = vm::sendChat,
                enabled = !vm.chatBusy && vm.chatInput.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (vm.chatBusy || vm.chatInput.isBlank()) Pf.Surface2 else Pf.Accent,
                        Radius.Pill
                    )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun Intro(vm: FinTrackViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3)
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(
                    Brush.linearGradient(listOf(Pf.Accent, Color(0xFF7E57C2))),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(26.dp), tint = Color.White)
        }

        Text(
            "FinTrack Assistant",
            color = Pf.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )

        if (!vm.chatReady) {
            Box(
                Modifier
                    .background(Pf.Surface, Radius.Md)
                    .border(1.dp, Pf.Hairline, Radius.Md)
                    .padding(Space.s4)
            ) {
                Text(
                    "Add your OpenAI API key in Settings to unlock AI answers and voice commands.",
                    color = Pf.Muted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@Column
        }

        Muted(
            "Ask about balances, record new expenses, or verify dues in natural language.",
            size = 13
        )

        Box(
            Modifier
                .fillMaxWidth()
                .background(Pf.Surface, Radius.Lg)
                .border(1.dp, Pf.Hairline, Radius.Lg)
                .padding(Space.s4)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text("EXAMPLES", color = Pf.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                listOf(
                    "What's left in the joint account?",
                    "Add 450 for Swiggy from ICICI Joint",
                    "Make the last Swiggy one 540",
                    "Confirm this month's car EMI"
                ).forEach { example ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.chatInput = example
                                vm.sendChat()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s2)
                    ) {
                        Text("•", color = Pf.Accent400, fontWeight = FontWeight.Bold)
                        Text(example, color = Pf.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Muted("Figures are processed directly via your OpenAI API key. PINs and credentials are never touched.", size = 11)
    }
}
