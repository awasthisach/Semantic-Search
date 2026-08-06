with open("app/src/main/java/com/example/storage/StorageScanner.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if line.strip() == "}" and i > 0 and lines[i-1].strip() == "}":
        print(f"Skipping extra brace at line {i+1}")
        continue
    new_lines.append(line)

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "w") as f:
    f.writelines(new_lines)
