package com.iman.pdf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private enum class StudentSection(val title: String) {
    FLASHCARDS("Flashcards"), NOTES("Notes"), REVISION("Revision")
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
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var notePage by remember { mutableStateOf("") }
    var revisionText by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(setOf<String>()) }

    val flashcards = remember(refresh) { store.flashcards() }
    val notes = remember(refresh) { store.notes() }
    val revision = remember(refresh) { store.revisionItems() }

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
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                if (notes.isEmpty()) EmptyStudyState("No study notes yet", "Keep important points here and optionally link a note to a PDF page.")
                else LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { store.removeNote(note.id); refresh++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete note") }
                                }
                                Text(note.body, style = MaterialTheme.typography.bodyMedium)
                                if (note.page != null) Text("PDF page " + note.page, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
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
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            FloatingActionButton(onClick = { addOpen = true }) {
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
                            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, singleLine = true)
                            OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Answer") })
                        }
                        StudentSection.NOTES -> {
                            OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, label = { Text("Title") }, singleLine = true)
                            OutlinedTextField(value = noteBody, onValueChange = { noteBody = it }, label = { Text("Note") })
                            OutlinedTextField(value = notePage, onValueChange = { notePage = it.filter(Char::isDigit) }, label = { Text("PDF page (optional)") }, singleLine = true)
                        }
                        StudentSection.REVISION -> OutlinedTextField(value = revisionText, onValueChange = { revisionText = it }, label = { Text("Topic or question") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when (StudentSection.entries[section]) {
                        StudentSection.FLASHCARDS -> if (question.isNotBlank() && answer.isNotBlank()) store.addFlashcard(question, answer)
                        StudentSection.NOTES -> if (noteTitle.isNotBlank() && noteBody.isNotBlank()) store.addNote(noteTitle, noteBody, notePage.toIntOrNull()?.takeIf { it > 0 })
                        StudentSection.REVISION -> if (revisionText.isNotBlank()) store.addRevisionItem(revisionText)
                    }
                    question = ""; answer = ""; noteTitle = ""; noteBody = ""; notePage = ""; revisionText = ""
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
