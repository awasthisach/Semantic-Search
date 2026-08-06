with open("app/src/main/java/com/example/VVFApplication.kt", "r") as f:
    text = f.read()

text = text.replace("    init {\n        instance = this\n    }\n", "")

with open("app/src/main/java/com/example/VVFApplication.kt", "w") as f:
    f.write(text)
