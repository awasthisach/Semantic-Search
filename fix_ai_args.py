with open("app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt", "r") as f:
    text = f.read()

text = text.replace("OcrEngineSection(allFiles = allFiles)", "OcrEngineSection(ocrScannedFiles = ocrScannedFiles)")
text = text.replace("allFiles = allFiles", "semanticSearchResults = semanticSearchResults")

with open("app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt", "w") as f:
    f.write(text)
