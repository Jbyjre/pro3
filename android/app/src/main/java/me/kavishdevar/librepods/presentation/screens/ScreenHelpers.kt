package me.kavishdevar.librepods.presentation.screens
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.librepods.presentation.theme.glintFontFamily

@Composable
internal fun SectionLabel(text: String, ink: Color) {
    Text(
        text,
        style = TextStyle(fontFamily = glintFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.6f)),
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

internal fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: Exception) { false }
