package com.vinay.fintrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
fun PrimaryButtonPreview() {
    FinTrackTheme {
        PrimaryButton(text = "Check UI", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun TagPreview() {
    FinTrackTheme {
        Tag(text = "Example Tag", background = Pf.Accent100, contentColor = Pf.Accent800)
    }
}
