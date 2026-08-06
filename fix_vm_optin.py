with open("app/src/main/java/com/example/ui/MainViewModel.kt", "r") as f:
    text = f.read()

text = text.replace("class MainViewModel", "@kotlinx.coroutines.ExperimentalCoroutinesApi\nclass MainViewModel")

with open("app/src/main/java/com/example/ui/MainViewModel.kt", "w") as f:
    f.write(text)
