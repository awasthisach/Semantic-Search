import re

with open("app/build.gradle.kts", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    stripped = line.strip()
    if stripped.startswith("//") and ("implementation" in stripped or "Uncomment" in stripped or "Sign-In via Credential Manager" in stripped):
        continue
    new_lines.append(line)

with open("app/build.gradle.kts", "w") as f:
    f.writelines(new_lines)

