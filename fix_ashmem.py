with open("app/src/main/java/com/example/VVFApplication.kt", "r") as f:
    text = f.read()

# Disable strict mode VM policy for leaked SQLite objects, which triggers the ashmem warning on Android Q+
text = text.replace("                    .detectLeakedSqlLiteObjects()\n", "")

with open("app/src/main/java/com/example/VVFApplication.kt", "w") as f:
    f.write(text)
