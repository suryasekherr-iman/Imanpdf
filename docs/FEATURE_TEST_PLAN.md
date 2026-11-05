# ImAn — Feature & Manual Test Plan

This plan separates what is present in the current repository from the larger PRD roadmap. A green build or emulator smoke test is not proof that every feature works on a physical phone.

## Changes covered by this maintenance pass

| Area | Expected behavior | How to test on phone | Pass condition |
|---|---|---|---|
| Offline/privacy | App does not request INTERNET permission; core app can run offline | Install APK, enable Airplane mode, open a local PDF, scroll, search and use local tools | App opens and local PDF actions work without network |
| Storage access | On-device list is limited to a folder explicitly selected with Android's Storage Access Framework | Open Home > On device or Files > On this device; tap Choose folder; select a folder containing PDFs and a nested folder | PDFs in selected folder and subfolders appear; unrelated folders are not scanned; reopen app and confirm folder access remains |
| Storage cancellation | Folder selection is optional | Open Choose folder and cancel | App remains usable; no crash or false permission state |
| Minimum Android | App requires Android 10 / API 29 or newer | Check APK install compatibility on Android 10+; verify manifest/build config | API 29 is the minimum; older Android is not targeted |
| Last page | Reading position is saved independently for each document | Open PDF A, go to page 4, back out; open PDF B, go to page 2; reopen A and B | A returns to page 4; B returns to page 2 |
| Zoom | Zoom factor is saved independently for each document | Pinch PDF A to about 2x, leave and reopen; open PDF B at default zoom | A returns near 2x; B remains at its own zoom |
| Same-name documents | Position, zoom and bookmarks must not collide when two different PDFs share a filename | Open two different PDFs both named sample.pdf; set different pages/zoom/bookmarks | Each document restores its own state |

## Existing screen and behavior checks

### 1. Launch and Home
1. Launch the app.
2. Confirm splash ends and Home appears.
3. Tap Home tabs: Recent, Starred, On device.
4. Tap the + action and open a valid PDF.
5. Star a recent PDF, close/reopen Home, confirm it remains Starred.
6. Remove an item from Recent and confirm the source PDF is not deleted.

**Pass:** no crash; navigation and list state match the action.

### 2. Files and document opening
1. Go to Files.
2. Choose a folder containing at least two PDFs, including one inside a subfolder.
3. Search by part of a filename.
4. Change sorting by date, name and size.
5. Tap a PDF to open it.
6. Reopen Files after leaving the app.

**Pass:** selected-folder PDFs are listed, search/sort work, and the chosen PDF opens. Cloud provider rows should only be treated as document-picker entry points unless a provider integration is explicitly implemented.

### 3. PDF Viewer
Use a test PDF with at least 5 pages.
1. Scroll down and up; verify page counter changes.
2. Pinch in and out; verify zoom changes smoothly.
3. Double-tap; verify zoom toggles.
4. Tap the page indicator / navigation control and move to another page.
5. Leave the PDF and reopen it; verify last page and zoom restore.
6. Rotate the phone between portrait and landscape.

**Pass:** page rendering remains stable, navigation does not jump unexpectedly, and app does not crash.

### 4. Search
1. Open a text-based PDF containing a known word multiple times.
2. Open Search and enter that word.
3. Check match results/count and choose a result.
4. Search a word that does not exist.
5. Repeat with a scanned/image-only PDF.

**Pass:** text PDF matches navigate to the correct page. Scanned PDFs may return no text until OCR exists; do not mark this as an OCR test.

### 5. Bookmarks and document outline
1. On page 2, add a bookmark.
2. Open Bookmarks & TOC and select the bookmark.
3. Remove it and confirm it disappears.
4. Repeat with another PDF, including a same-named PDF.

**Pass:** bookmark opens the correct page and does not leak to another document.

### 6. Read Aloud
1. Open a PDF with selectable English text and start Read Aloud.
2. Pause, resume, move to next/previous page.
3. Try Hindi and Bengali only if the phone has those TTS voices installed.
4. Try a page with no extractable text.

**Pass:** available voice reads extracted text; unavailable voice gives a clear message; blank pages do not crash the app. Voice availability and quality depend on installed Android language packs.

### 7. Annotation / Fill & Sign
1. Open a sample PDF and enter Highlight mode; highlight a text region.
2. Try drawing/freehand if offered.
3. Open Fill & Sign and add a signature.
4. Save/export a copy and open that saved PDF in another reader.

**Pass:** the saved PDF contains the expected annotation/signature. If changes only appear inside ImAn and disappear in another reader, report as a defect.

### 8. Export, share and print
1. Use Save / Save a copy and select a destination.
2. Export text from a text PDF.
3. Export pages as images to a folder.
4. Open Share and choose a target app.
5. Open Print and verify Android's print preview appears.

**Pass:** saved files exist and open; exported text/images correspond to the source pages; share/print intents launch.

### 9. Tools and error handling
1. Open the existing Edit PDF, Pages/Organize, Compress and Password tools from the viewer menu.
2. Test with a normal PDF, a password-protected PDF and a deliberately damaged/invalid file.
3. Cancel file pickers and dialogs at each step.

**Pass:** valid files reach the relevant tool, invalid files show a clear error, cancellation returns safely to the previous screen.

## Automatic test coverage

- GitHub Actions: APK assemble, lint and emulator instrumentation tests.
- Existing viewer smoke tests cover opening a generated multi-page PDF and opening major viewer sheets/tools (view settings, comments, export, bookmarks, compress, password, pages, edit).
- New LastPage tests cover per-document page restoration, page clamping, per-document zoom restoration and zoom clamping.
- Storage folder selection and full end-to-end feature behavior still require manual physical-device testing.

## Not claimed as complete

The PRD includes larger capabilities such as full OCR, advanced PDF page operations, form-field detection, background TTS controls, flashcards, Student Spaces, biometric lock and full annotation undo/redo. These must be marked “not yet verified / not yet implemented” unless the current app build exposes and passes their dedicated tests. Do not infer completion from this test plan.
