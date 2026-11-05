package com.example.hyundaipleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF16191D)
private val Blue = Color(0xFF1687F8)
private val MapBase = Color(0xFFF1F5FA)

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.systemUiVisibility = (4 or 1024 or 4096)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MapBase) { NavigationMap() }
            }
        }
    }
}

@Composable
private fun NavigationMap() {
    Box(Modifier.fillMaxSize().background(MapBase)) {
        MapArtwork(Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 24.dp)) {
            Row(
                Modifier.widthIn(min = 320.dp, max = 500.dp).fillMaxWidth(.72f)
                    .clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFE4E8ED), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = Ink, fontSize = 27.sp)
                Spacer(Modifier.width(14.dp))
                Text("어디로 갈까요?", color = Ink, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MapChip("⌂", "집")
                MapChip("▦", "회사")
                MapChip("★", "즐겨찾기")
                MapChip("ϟ", "충전소")
            }
        }

        MapChip("2D  ▲", null, Modifier.align(Alignment.TopEnd).padding(20.dp))

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapButton("◖))")
            MapButton("⚙")
        }

        Box(
            Modifier.align(Alignment.Center).size(38.dp).clip(CircleShape)
                .background(Color.White).border(2.dp, Blue, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("▲", color = Blue, fontSize = 20.sp) }

        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 24.dp)
                .size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .88f))
                .border(1.dp, Color(0xFFCCD7E2), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) { Text("⊙", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Light) }
    }
}

@Composable
private fun MapChip(icon: String, label: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE4E8ED), RoundedCornerShape(24.dp))
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = if (icon == "ϟ") Blue else Ink, fontSize = 17.sp)
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Ink, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MapButton(icon: String) {
    Box(
        Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE0E4E9), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) { Text(icon, color = Ink, fontSize = 20.sp) }
}

@Composable
private fun MapArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val roads = listOf(
            listOf(Offset(.02f,.22f),Offset(.26f,.18f),Offset(.42f,.36f),Offset(.63f,.31f),Offset(.98f,.10f)),
            listOf(Offset(.05f,.70f),Offset(.28f,.55f),Offset(.47f,.65f),Offset(.70f,.53f),Offset(1f,.76f)),
            listOf(Offset(.20f,0f),Offset(.30f,.26f),Offset(.25f,.50f),Offset(.40f,.78f),Offset(.35f,1f)),
            listOf(Offset(.76f,0f),Offset(.68f,.25f),Offset(.78f,.48f),Offset(.66f,.73f),Offset(.80f,1f)),
            listOf(Offset(0f,.42f),Offset(.3f,.40f),Offset(.55f,.47f),Offset(.78f,.38f),Offset(1f,.44f))
        )
        roads.forEachIndexed { index, points ->
            val path = Path().apply {
                points.forEachIndexed { pointIndex, point ->
                    val p = Offset(point.x * size.width, point.y * size.height)
                    if (pointIndex == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(
                path,
                if (index == 1) Color(0xFFD7E2EC) else Color.White,
                style = Stroke(if (index == 1) 11.dp.toPx() else 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        drawLine(Color(0xFFD6E8F2), Offset(size.width*.86f,0f), Offset(size.width*.72f,size.height), 22.dp.toPx())
        drawCircle(Color(0xFFE4F0E5), size.minDimension * .055f, Offset(size.width*.22f,size.height*.30f))
        drawCircle(Color(0xFFE4F0E5), size.minDimension * .07f, Offset(size.width*.83f,size.height*.69f))
    }
}
