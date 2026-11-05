package com.iman.pdf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.StudentStore

private data class StudyMaterial(val id: String, val label: String, val title: String, val body: String, val page: Int?)

private enum class StudentSection(val title: String) {
    FLASHCARDS("Flashcards"), NOTES("Notes"), BOOKMARKS("Bookmarks"), REVISION("Revision"), IMPORTANT_QUESTIONS("Important Questions")
}

@Composable
fun StudentSpacesScreen() {
    val context = LocalContext.current
    val store = remember { StudentStore(context) }
    var section by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var highlightMode by remember { mutableStateOf(false) }
    var selectedHighlightId by remember { mutableStateOf<String?>(null) }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var notePage by remember { mutableStateOf("") }
    var revisionText by remember { mutableStateOf("") }
    var importantQuestionText by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var studyMode by remember { mutableStateOf(false) }
    var notesStudyMode by remember { mutableStateOf(false) }
    var studyIndex by remember { mutableIntStateOf(0) }

    val flashcards = remember(refresh) { store.flashcards() }
    val notes = remember(refresh) { store.notes() }
    val revision = remember(refresh) { store.revisionItems() }
    val importantQuestions = remember(refresh) { store.importantQuestions() }
    val bookmarks = remember(refresh) { store.bookmarks() }
    val highlights = remember(refresh) { store.highlights() }
    val textHighlights = remember(highlights) { highlights.filter { it.text.isNotBlank() } }
    val studyMaterials = remember(notes, highlights) {
        (notes.map { StudyMaterial(it.id, "NOTE", it.title, it.body, it.page) } +
            highlights.map { StudyMaterial(it.id, it.type.uppercase(), "PDF highlight", it.text.ifBlank { "Highlighted area saved in PDF" }, it.page) })
            .sortedWith(compareBy<StudyMaterial> { it.page ?: Int.MAX_VALUE }.thenBy { it.label })
    }

    Column(Modifier.fillMaxSize()) {
        Text("Student Spaces", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        TabRow(selectedTabIndex = section) {
            StudentSection.entries.forEachIndexed { index, item ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(item.title) })
            }
        }
        when (StudentSection.entries[section]) {
            StudentSection.FLASHCARDS -> {
                if (flashcards.isEmpty()) EmptyStudyState("No flashcards yet", "Create cards with a question and answer for offline revision.")
                else if (studyMode) {
                    val currentIndex = studyIndex.coerceIn(0, flashcards.lastIndex)
                    val card = flashcards[currentIndex]
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("STUDY MODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Card ${currentIndex + 1} of ${flashcards.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).clickable {
                                revealed = if (card.id in revealed) revealed - card.id else revealed + card.id
                            }
                        ) {
                            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (card.id in revealed) "ANSWER" else "QUESTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (card.id in revealed) card.answer else card.question,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(top = 18.dp)
                                )
                                Text("Tap card to ${if (card.id in revealed) "see question" else "reveal answer"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                studyIndex = (currentIndex - 1).coerceAtLeast(0)
                            }, enabled = currentIndex > 0) { Text("Previous") }
                            TextButton(onClick = {
                                studyIndex = (currentIndex + 1).coerceAtMost(flashcards.lastIndex)
                                revealed = revealed - card.id
                            }, enabled = currentIndex < flashcards.lastIndex) { Text("Next") }
                        }
                        TextButton(onClick = { studyMode = false; revealed = emptySet() }) { Text("Exit study mode") }
                    }
                } else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(flashcards, key = { it.id }) { card ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().clickable {
                            revealed = if (card.id in revealed) revealed - card.id else revealed + card.id
                        }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (card.id in revealed) "ANSWER" else "QUESTION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(if (card.id in revealed) card.answer else card.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { store.removeFlashcard(card.id); refresh++ }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
            StudentSection.NOTES -> {
                if (notesStudyMode && studyMaterials.isNotEmpty()) {
                    val currentIndex = studyIndex.coerceIn(0, studyMaterials.lastIndex)
                    val material = studyMaterials[currentIndex]
                    Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOTES & HIGHLIGHTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Item ${currentIndex + 1} of ${studyMaterials.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                            Column(Modifier.padding(24.dp)) {
                                Text(material.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(material.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                                Text(material.body.ifBlank { "Highlighted area saved in PDF" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
                                if (material.page != null) Text("PDF page ${material.page}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { studyIndex = (currentIndex - 1).coerceAtLeast(0) }, enabled = currentIndex > 0) { Text("Previous") }
                            TextButton(onClick = { studyIndex = (currentIndex + 1).coerceAtMost(studyMaterials.lastIndex) }, enabled = currentIndex < studyMaterials.lastIndex) { Text("Next") }
                        }
                        TextButton(onClick = { notesStudyMode = false }) { Text("Exit study mode") }
                    }
                } else if (notes.isEmpty() && highlights.isEmpty()) EmptyStudyState("No study notes or highlights yet", "Add notes from a PDF page and save PDFs after highlighting text.")
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(highlights, key = { it.id }) { highlight ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(highlight.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(highlight.text.ifBlank { "Highlighted area saved in PDF" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                                Text("${highlight.documentTitle} · page ${highlight.page}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    items(notes, key = { it.id }) { note ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { store.removeNote(note.id); refresh++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete note") }
                                }
                                Text(note.body, style = MaterialTheme.typography.bodyMedium)
                                if (note.page != null) Text((if (note.documentId != null) "Linked PDF · page " else "PDF page ") + note.page, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            StudentSection.BOOKMARKS -> {
                if (bookmarks.isEmpty()) EmptyStudyState("No bookmarks yet", "Bookmark pages while reading a PDF. They will appear here for revision.")
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bookmarks, key = { it.id }) { item ->
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.documentTitle, style = MaterialTheme.typography.bodyLarge)
                                    Text("Page ${item.page}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { store.removeBookmark(item.id); refresh++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove bookmark") }
                            }
                        }
                    }
                }
            }
            StudentSection.IMPORTANT_QUESTIONS -> {
                if (importantQuestions.isEmpty()) EmptyStudyState("No important questions yet", "Save exam-focused questions here for quick revision.")
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(importantQuestions, key = { it.id }) { item ->
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { store.removeImportantQuestion(item.id); refresh++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove important question") }
                            }
                        }
                    }
                }
            }
            StudentSection.REVISION -> {
                if (revision.isEmpty()) EmptyStudyState("Revision list is empty", "Add topics or questions you want to revisit.")
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(revision, key = { it.id }) { item ->
                        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = item.done, onCheckedChange = { store.setRevisionDone(item.id, it); refresh++ })
                                Text(item.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { store.removeRevisionItem(item.id); refresh++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove revision item") }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (section == 0 && flashcards.isNotEmpty() && !studyMode) {
                Button(onClick = { studyIndex = 0; studyMode = true; revealed = emptySet() }) { Text("Study mode") }
            }
            if (section == 1 && studyMaterials.isNotEmpty() && !notesStudyMode) {
                Button(onClick = { studyIndex = 0; notesStudyMode = true }) { Text("Study notes & highlights") }
            }
            FloatingActionButton(onClick = {
                if (section == 0) {
                    question = ""
                    answer = ""
                    highlightMode = false
                    selectedHighlightId = null
                }
                addOpen = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add study item")
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("Add " + StudentSection.entries[section].title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (StudentSection.entries[section]) {
                        StudentSection.FLASHCARDS -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { highlightMode = false }) { Text(if (!highlightMode) "✓ Manual" else "Manual") }
                                TextButton(onClick = { highlightMode = true }) { Text(if (highlightMode) "✓ From highlight" else "From highlight") }
                            }
                            if (highlightMode) {
                                Text("Choose a saved PDF highlight. Its text will be added as the answer; you can edit both fields.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (textHighlights.isEmpty()) {
                                    Text("No text highlights found. Highlight selectable text in a PDF first, then return here.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(textHighlights, key = { it.id }) { highlight ->
                                            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = if (selectedHighlightId == highlight.id) 3.dp else 1.dp, modifier = Modifier.fillMaxWidth().clickable {
                                                selectedHighlightId = highlight.id
                                                question = "What is the key idea in this highlight?"
                                                answer = highlight.text
                                            }) {
                                                Column(Modifier.padding(10.dp)) {
                                                    Text("${highlight.documentTitle} · page ${highlight.page}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    Text(highlight.text.take(180), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                                    if (selectedHighlightId == highlight.id) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, singleLine = true)
                            OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") }, minLines = 2)
                        }
                        StudentSection.NOTES -> {
                            OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, label = { Text("Title") }, singleLine = true)
                            OutlinedTextField(value = noteBody, onValueChange = { noteBody = it }, label = { Text("Note") })
                            OutlinedTextField(value = notePage, onValueChange = { notePage = it.filter(Char::isDigit) }, label = { Text("PDF page (optional)") }, singleLine = true)
                        }
                        StudentSection.BOOKMARKS -> Text("Bookmarks are added from the PDF viewer.")
                        StudentSection.REVISION -> OutlinedTextField(value = revisionText, onValueChange = { revisionText = it }, label = { Text("Topic to revise") }, singleLine = true)
                        StudentSection.IMPORTANT_QUESTIONS -> OutlinedTextField(value = importantQuestionText, onValueChange = { importantQuestionText = it }, label = { Text("Important question") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when (StudentSection.entries[section]) {
                        StudentSection.FLASHCARDS -> if (question.isNotBlank() && answer.isNotBlank()) store.addFlashcard(question, answer)
                        StudentSection.NOTES -> if (noteTitle.isNotBlank() && noteBody.isNotBlank()) store.addNote(noteTitle, noteBody, notePage.toIntOrNull()?.takeIf { it > 0 })
                        StudentSection.BOOKMARKS -> Unit
                        StudentSection.REVISION -> if (revisionText.isNotBlank()) store.addRevisionItem(revisionText)
                        StudentSection.IMPORTANT_QUESTIONS -> if (importantQuestionText.isNotBlank()) store.addImportantQuestion(importantQuestionText)
                    }
                    question = ""; answer = ""; highlightMode = false; selectedHighlightId = null; noteTitle = ""; noteBody = ""; notePage = ""; revisionText = ""; importantQuestionText = ""
                    addOpen = false
                    refresh++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyStudyState(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
