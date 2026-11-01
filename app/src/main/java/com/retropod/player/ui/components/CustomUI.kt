package com.retropod.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retropod.player.ui.theme.*
import kotlin.math.sin

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(RetroGlass)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun WaveVisualizer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val width = size.width
        val height = size.height
        val path1 = Path()
        val path2 = Path()
        
        path1.moveTo(0f, height / 2)
        path2.moveTo(0f, height / 2)
        
        for (i in 0..width.toInt() step 5) {
            val x = i.toFloat()
            val y1 = height / 2 + sin(x * 0.05f) * 30f
            val y2 = height / 2 + sin(x * 0.08f + 1f) * 20f
            path1.lineTo(x, y1)
            path2.lineTo(x, y2)
        }
        
        drawPath(path1, color = Color.White.copy(alpha = 0.8f), style = Stroke(width = 3f))
        drawPath(path2, color = Color.Gray.copy(alpha = 0.5f), style = Stroke(width = 2f))
    }
}

@Composable
fun VerticalSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(4.dp).height(100.dp).background(Color.DarkGray)) {
            Box(modifier = Modifier.width(12.dp).height(12.dp)
                .align(Alignment.Center)
                .background(Color.White, RoundedCornerShape(50)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = RetroGray, fontSize = 10.sp)
    }
}
