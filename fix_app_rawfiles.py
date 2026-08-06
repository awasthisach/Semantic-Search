with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "r") as f:
    text = f.read()

text = text.replace("val rawFiles by viewModel.allFiles.collectAsStateWithLifecycle()\n", "")

with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "w") as f:
    f.write(text)
