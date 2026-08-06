import re

with open("app/src/main/res/values/strings.xml", "r") as f:
    text = f.read()

text = text.replace("'", r"\'")
text = text.replace("70%", "70%%")
text = text.replace("95%", "95%%")

with open("app/src/main/res/values/strings.xml", "w") as f:
    f.write(text)
