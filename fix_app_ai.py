with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "r") as f:
    text = f.read()

text = text.replace("val cloudSyncItems by viewModel.cloudSyncItems.collectAsStateWithLifecycle()", "val cloudSyncItems by viewModel.cloudSyncItems.collectAsStateWithLifecycle()\n    val ocrScannedFiles by viewModel.ocrScannedFiles.collectAsStateWithLifecycle()\n    val semanticSearchResults by viewModel.semanticSearchResults.collectAsStateWithLifecycle()")

text = text.replace("semanticQuery = semanticQuery,\n                    allFiles = rawFiles", "semanticQuery = semanticQuery,\n                    ocrScannedFiles = ocrScannedFiles,\n                    semanticSearchResults = semanticSearchResults")

with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "w") as f:
    f.write(text)
