package com.vinay.fintrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.fintrack.FinTrackViewModel

@Composable
fun LockScreen(vm: FinTrackViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pf.Bg)
            .padding(Space.s4),
        verticalArrangement = Arrangement.spacedBy(Space.s6, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(Pf.Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("F", color = Pf.OnAccent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "FINTRACK",
                Modifier.padding(top = Space.s4, bottom = 6.dp),
                color = Pf.Accent400,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Text("Unlock FinTrack", color = Pf.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s4)
        ) {
            Muted("Enter 4-digit PIN for ${vm.activeProfile ?: "Vinay"}", size = 14)

            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(
                                if (i < vm.pinInput.length) Pf.Accent else Pf.Neutral700,
                                CircleShape
                            )
                    )
                }
            }

            if (vm.pinError) {
                Text("Incorrect PIN. Please try again.", color = Pf.Accent400, fontSize = 13.sp)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s3)
            ) {
                KEYPAD.chunked(3).forEach { rowKeys ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s3)
                    ) {
                        rowKeys.forEach { key ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1.3f)
                                    .then(
                                        if (key.isEmpty()) Modifier
                                        else Modifier
                                            .background(Pf.Surface, CircleShape)
                                            .clickable {
                                                if (key == "⌫") vm.pressBackspace() else vm.pressDigit(key)
                                            }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    color = Pf.Text,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val KEYPAD = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
