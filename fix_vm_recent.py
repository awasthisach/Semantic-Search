with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    text = f.read()

if "val recentFiles:" not in text:
    text = text.replace("val categoryStats: StateFlow<List<CategoryStat>>", "val recentFiles: StateFlow<List<FileItemEntity>> = repository.recentFiles\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\n\n    val categoryStats: StateFlow<List<CategoryStat>>")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(text)
