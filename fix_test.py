import re
with open("app/src/test/java/com/example/ui/MainViewModelTest.kt", "r") as f:
    text = f.read()

text = text.replace("viewModel = MainViewModel(application, repository)", "viewModel = MainViewModel(application)")

with open("app/src/test/java/com/example/ui/MainViewModelTest.kt", "w") as f:
    f.write(text)

with open("app/src/test/java/com/example/ExampleUnitTest.kt", "r") as f:
    text = f.read()

text = re.sub(r'fun videoDHashExtraction_returnsValidHex\(\) \{', r'fun videoDHashExtraction_returnsValidHex() = runBlocking {', text)
text = re.sub(r'fun documentFingerprint_returnsValidHash\(\) \{', r'fun documentFingerprint_returnsValidHash() = runBlocking {', text)

with open("app/src/test/java/com/example/ExampleUnitTest.kt", "w") as f:
    f.write(text)
