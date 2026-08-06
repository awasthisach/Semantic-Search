import re
with open("app/src/test/java/com/example/ExampleUnitTest.kt", "r") as f:
    text = f.read()

text = text.replace("import org.junit.Assert.assertTrue", "import org.junit.Assert.assertTrue\nimport kotlinx.coroutines.runBlocking")

text = text.replace("fun videoDHashExtraction_returnsValidHex() {", "fun videoDHashExtraction_returnsValidHex() = runBlocking {")
text = text.replace("fun documentFingerprint_returnsValidHash() {", "fun documentFingerprint_returnsValidHash() = runBlocking {")
# If it has @Test fun videoDHash... make sure we use runBlocking
text = text.replace("fun documentFingerprint_returnsValidHash() {", "fun documentFingerprint_returnsValidHash() = runBlocking {")

with open("app/src/test/java/com/example/ExampleUnitTest.kt", "w") as f:
    f.write(text)
