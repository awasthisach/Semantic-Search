import re

with open("app/src/test/java/com/example/ExampleUnitTest.kt", "r") as f:
    text = f.read()

before_method = """  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("vvf_vault_prefs", Context.MODE_PRIVATE).edit().clear().commit()
  }

"""

if "fun setUp()" not in text:
    text = text.replace("class ExampleUnitTest {", "class ExampleUnitTest {\n" + before_method)

with open("app/src/test/java/com/example/ExampleUnitTest.kt", "w") as f:
    f.write(text)
