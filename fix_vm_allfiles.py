with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    text = f.read()

text = text.replace("val allFiles: StateFlow<List<FileItemEntity>> = repository.activeFiles\n        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())", "")

text = text.replace("val toDelete = allFiles.value.filter { ids.contains(it.id) }", "val toDelete = ids.mapNotNull { repository.getFileById(it) }")

text = text.replace("val originalFile = allFiles.value.find { it.name == vaultItem.originalName }", "val originalFile = repository.getFileByName(vaultItem.originalName)")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(text)
