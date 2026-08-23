package com.bioluck.copywork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioluck.copywork.data.ArticleEntity
import coil.compose.AsyncImage

private enum class MainSection { ARTICLES, VOCABULARY, STATS }

@Composable fun HomeScreen(onArticle: (Long) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle(); var section by remember { mutableStateOf(MainSection.ARTICLES) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { NavigationBar {
        NavItem(section == MainSection.ARTICLES, Icons.Default.Home, "기사 목록") { section = MainSection.ARTICLES }
        NavItem(section == MainSection.VOCABULARY, Icons.Default.MenuBook, "단어장") { section = MainSection.VOCABULARY }
        NavItem(section == MainSection.STATS, Icons.Default.BarChart, "통계") { section = MainSection.STATS }
    } }) { padding -> when (section) {
        MainSection.ARTICLES -> ArticleList(state, vm, onArticle, Modifier.padding(padding))
        MainSection.VOCABULARY -> VocabularyList(state, vm::deleteWord, Modifier.padding(padding))
        MainSection.STATS -> StatsScreen(state, Modifier.padding(padding))
    } }
}
@Composable private fun RowScope.NavItem(selected: Boolean, icon: ImageVector, label: String, action: () -> Unit) = NavigationBarItem(selected, action, { Icon(icon, label) }, label = { Text(label) })

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ArticleList(state: HomeState, vm: HomeViewModel, onArticle: (Long) -> Unit, modifier: Modifier) {
    if (state.sync.running) AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("기사 동기화") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.sync.total > 0) LinearProgressIndicator(
                progress = { state.sync.current.toFloat() / state.sync.total }, Modifier.fillMaxWidth()
            ) else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.sync.message.ifBlank { "기사 목록 확인 중…" })
        } },
    )
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp)); Text("日語 筆寫ノート", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("하루 한 편, 눈이 편한 일본어 필사", color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("천성인어 (天声人語)", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("최근 업데이트: ${state.articles.maxOfOrNull { it.publishedAt }?.replace('-', '.') ?: "-"}", Modifier.weight(1f))
            TextButton({ vm.refresh(false) }, enabled = !state.sync.running) { Icon(Icons.Default.Refresh, null); Text("새로고침") }
        }
        if (state.sync.running) {
            if (state.sync.total > 0) LinearProgressIndicator(
                progress = { state.sync.current.toFloat() / state.sync.total }, Modifier.fillMaxWidth()
            ) else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.sync.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        } else if (state.sync.message.isNotBlank()) Text(state.sync.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(state.filter == ProgressFilter.INCOMPLETE, { vm.selectFilter(ProgressFilter.INCOMPLETE) }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Default.Edit, null) }) { Text("미완료 (${state.incompleteCount})") }
            SegmentedButton(state.filter == ProgressFilter.COMPLETE, { vm.selectFilter(ProgressFilter.COMPLETE) }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Default.CheckBox, null) }) { Text("완료 (${state.completeCount})") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(12.dp))
        if (state.articles.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (state.loading) "최신 글을 가져오는 중…" else "해당하는 기사가 없습니다.") }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(state.articles, key = { it.id }) { ArticleCard(it) { onArticle(it.id) } } }
    }
}
@Composable private fun ArticleCard(article: ArticleEntity, action: () -> Unit) = Card(action, Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(4.dp)) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(Modifier.size(108.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            if (article.thumbnailUrl.isNotBlank()) AsyncImage(article.thumbnailUrl, article.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.EditNote, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.secondary) }
        }
        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(article.publishedAt.replace('-', '.'), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(5.dp)); Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp)); Text(article.koreanText.ifBlank { article.japaneseText }.take(110), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            TextButton(action, Modifier.align(Alignment.End)) { Text("보기"); Icon(Icons.Default.ChevronRight, null) }
        }
    }
}
@Composable private fun VocabularyList(state: HomeState, delete: (String) -> Unit, modifier: Modifier) = Column(modifier.fillMaxSize().padding(18.dp)) {
    Text("나의 단어장", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("본문에서 저장한 한자와 단어 ${state.vocabulary.size}개", color = MaterialTheme.colorScheme.secondary); Spacer(Modifier.height(16.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { items(state.vocabulary, key = { it.word }) { word -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(word.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(word.reading); Text(word.meaning); if (word.onyomi.isNotBlank()) Text("음독 ${word.onyomi} · 훈독 ${word.kunyomi}", style = MaterialTheme.typography.bodySmall) }
        IconButton({ delete(word.word) }) { Icon(Icons.Default.DeleteOutline, "삭제") }
    } } } }
}
@Composable private fun StatsScreen(state: HomeState, modifier: Modifier) = Column(modifier.fillMaxSize().padding(18.dp)) {
    Text("필사 통계", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("완료한 필사", "${state.totalCompleted}편", Modifier.weight(1f)); StatCard("연속 학습", "${state.streakDays}일", Modifier.weight(1f))
    }; Spacer(Modifier.height(12.dp)); StatCard("저장한 단어", "${state.vocabulary.size}개", Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp)); Text("최근 7일 필사 완료", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    CompletionLineChart(state.completionTrend, Modifier.fillMaxWidth().height(190.dp))
}
@Composable private fun StatCard(label: String, value: String, modifier: Modifier) = Card(modifier, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(22.dp)) { Text(label, color = MaterialTheme.colorScheme.secondary); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) } }

@Composable private fun CompletionLineChart(values: List<Int>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .45f)
    Card(modifier, shape = RoundedCornerShape(22.dp)) { Canvas(Modifier.fillMaxSize().padding(22.dp)) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height))
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX; val y = size.height - (value.toFloat() / max) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(lineColor, radius = 6f, center = androidx.compose.ui.geometry.Offset(x, y))
        }
        drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = StrokeCap.Round))
    } }
}
