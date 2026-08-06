with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    text = f.read()

if "import com.example.data.CategoryStat" not in text:
    text = text.replace("import com.example.data.CloudSyncItemEntity", "import com.example.data.CloudSyncItemEntity\nimport com.example.data.CategoryStat")

if "val categoryStats" not in text:
    text = text.replace("val allFiles: StateFlow<List<FileItemEntity>> = repository.activeFiles\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())", "val allFiles: StateFlow<List<FileItemEntity>> = repository.activeFiles\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\n\n    val categoryStats: StateFlow<List<CategoryStat>> = repository.categoryStats\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(text)
