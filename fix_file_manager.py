import re

filepath = "app/src/main/java/com/example/ui/screens/FileManagerScreen.kt"
with open(filepath, "r") as f:
    content = f.read()

# Fix the Card replacements
content = content.replace(
    "Card(modifier = modifier.fillMaxWidth(), \n            modifier = Modifier\n                .fillMaxWidth()",
    "Card(\n            modifier = Modifier\n                .fillMaxWidth()"
)

content = content.replace(
    "Card(modifier = modifier.fillMaxWidth(), \n                            modifier = Modifier\n                                .fillMaxWidth()",
    "Card(\n                            modifier = Modifier\n                                .fillMaxWidth()"
)

content = content.replace(
    "Card(modifier = modifier.fillMaxWidth(), \n         \n        shape = RoundedCornerShape(12.dp),",
    "Card(\n        modifier = modifier.fillMaxWidth(),\n        shape = RoundedCornerShape(12.dp),"
)

# Add animateItemPlacement to items(files) list items
items_block_old = """                items(files, key = { it.id }) { file ->
                    FileManagerItemRow(
                        file = file,
                        onRename = {"""

items_block_new = """                items(files, key = { it.id }) { file ->
                    FileManagerItemRow(
                        modifier = Modifier.animateItem(),
                        file = file,
                        onRename = {"""
content = content.replace(items_block_old, items_block_new)

with open(filepath, "w") as f:
    f.write(content)
