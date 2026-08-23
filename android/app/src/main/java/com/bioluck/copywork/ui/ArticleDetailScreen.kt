package com.bioluck.copywork.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioluck.copywork.data.ArticleEntity
import com.bioluck.copywork.data.AdvancedVocabulary

private enum class FocusMode { SPLIT, ORIGINAL, TRANSLATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(articleId: Long, onBack: () -> Unit, vm: DetailViewModel = viewModel()) {
    LaunchedEffect(articleId) { vm.bind(articleId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val article = state.article
    var focus by rememberSaveable { mutableStateOf(FocusMode.SPLIT) }
    var vocabularyFocus by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(
        title = { Text("천성인어 / 天声人語", fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        actions = { IconButton({ vm.toggleBookmark() }) { Icon(if (state.articleState?.bookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "북마크") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    ) }) { padding ->
        if (article == null) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= 600.dp) TabletSplit(article, state, vm, focus, { focus = it }, { vocabularyFocus = true })
            else PhoneLayout(article, state, vm, focus, { focus = it }, { vocabularyFocus = true })
        }
    }
    state.dictionary?.let { DictionaryDialog(it, state.wordSaved, vm) }
    if (vocabularyFocus) VocabularyFocusDialog(state.advancedVocabulary, vm::saveWord) { vocabularyFocus = false }
}

@Composable private fun TabletSplit(article: ArticleEntity, state: DetailState, vm: DetailViewModel, focus: FocusMode, setFocus: (FocusMode) -> Unit, showWords: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        when (focus) {
            FocusMode.ORIGINAL -> OriginalPanel(article, state, vm, Modifier.fillMaxSize()) { setFocus(FocusMode.SPLIT) }
            FocusMode.TRANSLATION -> TranslationPanel(article, state, vm, Modifier.fillMaxSize(), { setFocus(FocusMode.SPLIT) }, showWords)
            FocusMode.SPLIT -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OriginalPanel(article, state, vm, Modifier.weight(.6f).fillMaxHeight()) { setFocus(FocusMode.ORIGINAL) }
                TranslationPanel(article, state, vm, Modifier.weight(.4f).fillMaxHeight(), { setFocus(FocusMode.TRANSLATION) }, showWords)
            }
        }
    }
}

@Composable private fun PhoneLayout(article: ArticleEntity, state: DetailState, vm: DetailViewModel, focus: FocusMode, setFocus: (FocusMode) -> Unit, showWords: () -> Unit) {
    when (focus) {
        FocusMode.ORIGINAL -> OriginalPanel(article, state, vm, Modifier.fillMaxSize().padding(12.dp)) { setFocus(FocusMode.SPLIT) }
        FocusMode.TRANSLATION -> TranslationPanel(article, state, vm, Modifier.fillMaxSize().padding(12.dp), { setFocus(FocusMode.SPLIT) }, showWords)
        FocusMode.SPLIT -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            OriginalPanel(article, state, vm, Modifier.fillMaxWidth().height(520.dp)) { setFocus(FocusMode.ORIGINAL) }
            Spacer(Modifier.height(12.dp)); TranslationPanel(article, state, vm, Modifier.fillMaxWidth().height(620.dp), { setFocus(FocusMode.TRANSLATION) }, showWords)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun OriginalPanel(article: ArticleEntity, state: DetailState, vm: DetailViewModel, modifier: Modifier, toggleFocus: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { ArticleHeading(article) }
            IconButton(toggleFocus) { Icon(Icons.Default.Fullscreen, "일본어 원문 전체화면") }
        }
        Text("한자 또는 루비를 누르면 뜻을 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        InteractiveJapaneseText(state.paragraphs, vm::lookup, Modifier.fillMaxWidth().weight(1f))
    } }
}

@Composable private fun ArticleHeading(article: ArticleEntity) {
    Text(article.publishedAt.replace('-', '.'), color = MaterialTheme.colorScheme.secondary)
    Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp)); Text("일본어 원문", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable private fun TranslationPanel(article: ArticleEntity, state: DetailState, vm: DetailViewModel, modifier: Modifier, toggleFocus: () -> Unit, showWords: () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp)) { Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("한국어 번역 보기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("자가 독해 후 확인해 보세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(state.translationVisible, { vm.toggleTranslation() })
            IconButton(toggleFocus) { Icon(Icons.Default.Fullscreen, "한국어 번역 전체화면") }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (state.translationVisible) Text(
                article.koreanText.ifBlank { "사전 번역 데이터가 없습니다." },
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f
            ) else Text("스위치를 켜면 번역이 표시됩니다.", color = MaterialTheme.colorScheme.secondary)
        }
        HorizontalDivider()
        VocabularyCard(state.advancedVocabulary, vm::saveWord, showWords)
        HorizontalDivider()
        Button(
            { if (state.articleState?.completed == true) vm.reopen() else vm.complete() },
            Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp))
            Text(if (state.articleState?.completed == true) "완료 취소" else "✔ 필사 완료")
        }
    } }
}

@Composable private fun VocabularyCard(words: List<AdvancedVocabulary>, save: (com.bioluck.copywork.data.DictionaryDto) -> Unit, showAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📌 본문 핵심 어휘 (JLPT N2 이상)", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(showAll, enabled = words.isNotEmpty()) { Icon(Icons.Default.Fullscreen, "단어 전체화면 보기") }
        }
        if (words.isEmpty()) Text("등록된 N2/N1 핵심 어휘가 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        else words.take(3).forEach { word -> VocabularyRow(word, save) }
    }
}

@Composable private fun VocabularyRow(word: AdvancedVocabulary, save: (com.bioluck.copywork.data.DictionaryDto) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${word.entry.word} (${word.entry.reading}) · ${word.level}", fontWeight = FontWeight.Bold)
            Text(word.entry.meaning, style = MaterialTheme.typography.bodySmall)
        }
        TextButton({ save(word.entry) }) { Text("+ 저장") }
    }
}

@Composable private fun VocabularyFocusDialog(words: List<AdvancedVocabulary>, save: (com.bioluck.copywork.data.DictionaryDto) -> Unit, dismiss: () -> Unit) {
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(20.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("본문 핵심 어휘 · JLPT N2/N1", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    IconButton(dismiss) { Icon(Icons.Default.FullscreenExit, "전체화면 닫기") }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 10.dp)) {
                    words.forEach { VocabularyRow(it, save); HorizontalDivider() }
                }
            }
        }
    }
}

@Composable private fun DictionaryDialog(entry: com.bioluck.copywork.data.DictionaryDto, saved: Boolean, vm: DetailViewModel) {
    AlertDialog(onDismissRequest = vm::dismissDictionary,
        confirmButton = { Button({ vm.saveWord() }, enabled = !saved) { Text(if (saved) "단어장에 저장됨" else "+ 단어장에 추가") } },
        dismissButton = { TextButton(vm::dismissDictionary) { Text("닫기") } },
        title = { Column { Text(entry.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); if (entry.romanization.isNotBlank()) Text(entry.romanization, color = MaterialTheme.colorScheme.secondary) } },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("발음  ${entry.reading.ifBlank { "정보 없음" }}"); if (entry.partOfSpeech.isNotBlank()) Text("품사  ${entry.partOfSpeech}")
            Text("뜻  ${entry.meaning}"); if (entry.onyomi.isNotBlank()) Text("[음독] ${entry.onyomi}"); if (entry.kunyomi.isNotBlank()) Text("[훈독] ${entry.kunyomi}")
        } })
}
