package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            verticalArrangement = Arrangement.spacedBy(Space.s2)
        ) {
            if (vm.chat.isEmpty()) item { Intro(vm) }

            items(vm.chat) { m ->
                val fromUser = m.role == "user"
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Text(
                        m.text,
                        Modifier
                            .fillMaxWidth(0.88f)
                            .background(if (fromUser) Pf.Accent else Pf.Surface, Radius.Md)
                            .border(
                                1.dp,
                                if (fromUser) Color.Transparent else Pf.Hairline,
                                Radius.Md
                            )
                            .padding(horizontal = Space.s3, vertical = Space.s3),
                        color = if (fromUser) Color.White else Pf.Text,
                        fontSize = 14.sp
                    )
                }
            }

            if (vm.chatBusy) item { Muted("Thinking…", Modifier.padding(top = Space.s1)) }

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
                Icon(Icons.Default.Send, "Send", Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun Intro(vm: FinTrackViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(
            "Ask me anything",
            color = Pf.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
        )
        Muted(
            if (vm.chatReady) {
                "I can read your accounts, transactions and commitments, and change " +
                    "them too — add a payment, fix a wrong amount, set a budget, " +
                    "confirm this month's EMI."
            } else {
                "Add an OpenAI key in Settings and I'll be able to read your data " +
                    "and change it for you."
            }
        )
        listOf(
            "How much did I spend on groceries this month?",
            "What's left in the joint account?",
            "Add 450 for Swiggy from ICICI Joint",
            "The last Swiggy one should be 540, fix it",
            "Set the eating out budget to 4000",
            "Confirm this month's car EMI"
        ).forEach { Muted("· $it") }
        Muted(
            "Your figures are sent to OpenAI to answer. Nothing else leaves the app.",
            Modifier.padding(top = Space.s2)
        )
        Hairline(Modifier.padding(top = Space.s2))
    }
}
