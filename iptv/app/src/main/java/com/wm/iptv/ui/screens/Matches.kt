package com.wm.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wm.iptv.data.SportMatch
import com.wm.iptv.ui.C
import com.wm.iptv.ui.FocusBox
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale("ar"))
private fun matchTime(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFmt)

/** خلفية "ملعب" للمباريات بدل صورة الفيلم */
@Composable
fun MatchesBackdrop() {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0E4D3A), Color(0xFF0B2A3F), Color(0xFF0A0A0C)))))
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x3329D07A), Color.Transparent), radius = 900f)))
}

/** عرض أهم مباريات اليوم: مباراة كبيرة مميزة + بقية المباريات تحتها */
@Composable
fun MatchesShowcase(matches: List<SportMatch>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("مباريات اليوم", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4ADE80))
            Spacer(Modifier.width(10.dp))
            Text("اضغط OK لفتح القنوات", fontSize = 13.sp, color = C.Muted)
        }
        val top = matches.firstOrNull()
        if (top == null) {
            Text("ما فيه مباريات مهمة اليوم", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("أول ما يكون فيه مباراة قوية بتطلع لك هنا", fontSize = 15.sp, color = C.Text3)
            return@Column
        }
        Featured(top)
        val rest = matches.drop(1).take(4)
        if (rest.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            rest.forEach { MiniMatch(it, Modifier.weight(1f)) }
            repeat(4 - rest.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Featured(m: SportMatch) {
    val big = m.weight >= 70
    Column(
        Modifier.width(820.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x14FFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                m.live -> Badge("مباشر الآن", C.Live)
                big -> Badge("قمة اليوم", Color(0xFFF59E0B))
                else -> Badge("أبرز مباراة", Color(0xFF22C55E))
            }
            Text(m.league, fontSize = 15.sp, color = C.Text2)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Team(m.home, m.homeLogo, 80.dp, Modifier.weight(1f))
            Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (m.score.isNotEmpty()) m.score else matchTime(m.startMs), fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val sub = when { m.live -> m.detail; m.finished -> "انتهت"; else -> "اليوم" }
                Text(sub, fontSize = 14.sp, color = if (m.live) C.Live else C.Text3, maxLines = 1)
            }
            Team(m.away, m.awayLogo, 80.dp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Team(name: String, logo: String, size: Dp, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Logo(logo, name, size)
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Logo(url: String, name: String, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0x1AFFFFFF)), contentAlignment = Alignment.Center) {
            Text(name.take(1), fontSize = (size.value / 2.6f).sp, fontWeight = FontWeight.Bold, color = Color(0x55FFFFFF))
        }
        if (url.isNotBlank()) AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.82f))
    }
}

@Composable
private fun MiniMatch(m: SportMatch, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.live) { Box(Modifier.size(7.dp).background(C.Live, CircleShape)); Spacer(Modifier.width(6.dp)) }
            Text(m.league, fontSize = 11.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (m.score.isNotEmpty()) m.score else matchTime(m.startMs), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (m.live) C.Live else C.Text)
        }
        MiniTeam(m.home, m.homeLogo)
        MiniTeam(m.away, m.awayLogo)
    }
}

@Composable
private fun MiniTeam(name: String, logo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(logo, name, 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
        modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** بطاقة مباراة تتركز عليها بالريموت (تفتح المباشر) */
@Composable
fun MatchCard(m: SportMatch, focusMod: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(focusMod.width(250.dp).height(116.dp), onClick = onClick, shape = RoundedCornerShape(16.dp), focusScale = 1.06f) { _ ->
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.live) { Box(Modifier.size(7.dp).background(C.Live, CircleShape)); Spacer(Modifier.width(6.dp)) }
                Text(m.league, fontSize = 11.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (m.score.isNotEmpty()) m.score else matchTime(m.startMs), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (m.live) C.Live else C.Text)
            }
            MiniTeam(m.home, m.homeLogo)
            MiniTeam(m.away, m.awayLogo)
        }
    }
}
