import re

filepath = "app/src/main/java/com/example/ui/VVFSmartManagerApp.kt"
with open(filepath, "r") as f:
    content = f.read()

imports = """
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
"""
if "AnimatedContent" not in content:
    content = content.replace("import androidx.compose.ui.Modifier", imports + "\nimport androidx.compose.ui.Modifier")

old_box = """        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {"""

new_box = """        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300)))
                    }
                },
                label = "Tab Transition"
            ) { targetTab ->
                when (targetTab) {"""

content = content.replace(old_box, new_box)

old_close = """                4 -> CloudPluginsScreen(
                    viewModel = viewModel,
                    cloudSyncItems = cloudSyncItems,
                    plugins = plugins
                )
            }
        }
    }
}"""

new_close = """                4 -> CloudPluginsScreen(
                    viewModel = viewModel,
                    cloudSyncItems = cloudSyncItems,
                    plugins = plugins
                )
            }
            }
        }
    }
}"""

if "AnimatedContent" in new_box:
    content = content.replace(old_close, new_close)

with open(filepath, "w") as f:
    f.write(content)
