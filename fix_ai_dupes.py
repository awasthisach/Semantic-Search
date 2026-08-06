with open("app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt", "r") as f:
    text = f.read()

text = text.replace("fun AiDuplicatesScreen(\n    viewModel: MainViewModel,\n    level1Duplicates: List<DuplicateGroup>,\n    level3Duplicates: List<DuplicateGroup>,\n    similarityThreshold: Float,\n    selectedDuplicateIds: Set<Long>,\n    semanticQuery: String,\n    allFiles: List<FileItemEntity>\n)", "fun AiDuplicatesScreen(\n    viewModel: MainViewModel,\n    level1Duplicates: List<DuplicateGroup>,\n    level3Duplicates: List<DuplicateGroup>,\n    similarityThreshold: Float,\n    selectedDuplicateIds: Set<Long>,\n    semanticQuery: String,\n    ocrScannedFiles: List<FileItemEntity>,\n    semanticSearchResults: List<FileItemEntity>\n)")

text = text.replace("fun OcrEngineSection(allFiles: List<FileItemEntity>)", "fun OcrEngineSection(ocrScannedFiles: List<FileItemEntity>)")

text = text.replace("val ocrScannedFiles = remember(allFiles) { allFiles.filter { it.ocrText.isNotBlank() } }", "")

text = text.replace("fun SemanticSearchSection(\n    viewModel: MainViewModel,\n    semanticQuery: String,\n    allFiles: List<FileItemEntity>\n)", "fun SemanticSearchSection(\n    viewModel: MainViewModel,\n    semanticQuery: String,\n    semanticSearchResults: List<FileItemEntity>\n)")

text = text.replace("""    val results = remember(allFiles, semanticQuery) {
        if (semanticQuery.isBlank()) allFiles else allFiles.filter {
            it.name.contains(semanticQuery, ignoreCase = true) ||
            it.ocrText.contains(semanticQuery, ignoreCase = true) ||
            it.tags.contains(semanticQuery, ignoreCase = true)
        }
    }""", "val results = semanticSearchResults")

text = text.replace("OcrEngineSection(allFiles)", "OcrEngineSection(ocrScannedFiles)")
text = text.replace("SemanticSearchSection(viewModel, semanticQuery, allFiles)", "SemanticSearchSection(viewModel, semanticQuery, semanticSearchResults)")

with open("app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt", "w") as f:
    f.write(text)
