import re
with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    text = f.read()

if "val ocrScannedFiles" not in text:
    text = text.replace("val categoryStats: StateFlow<List<CategoryStat>> = repository.categoryStats\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())", "val categoryStats: StateFlow<List<CategoryStat>> = repository.categoryStats\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\n\n    val ocrScannedFiles: StateFlow<List<FileItemEntity>> = repository.ocrScannedFiles\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\n\n    val semanticSearchResults: StateFlow<List<FileItemEntity>> = _semanticQuery\n        .flatMapLatest { query -> repository.searchSemanticFiles(query) }\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())")

text = text.replace("import kotlinx.coroutines.flow.combine", "import kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.flow.flatMapLatest")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(text)
