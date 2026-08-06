import os
import re

ui_dir = "app/src/main/java/com/example/ui/theme/"

for root, _, files in os.walk(ui_dir):
    for f in files:
        if f.endswith(".kt"):
            file_path = os.path.join(root, f)
            with open(file_path, "r") as f_in:
                content = f_in.read()
            
            content = re.sub(r'^import androidx\.compose\.ui\.res\.stringResource\.theme.*$', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import androidx\.compose\.ui\.res\.stringResource\n', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import com\.example\.R\n', '', content, flags=re.MULTILINE)
            
            # remove blank lines at start
            content = re.sub(r'\n{3,}', '\n\n', content)
            
            with open(file_path, "w") as f_out:
                f_out.write(content)

