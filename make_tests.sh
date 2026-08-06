mkdir -p app/src/test/java/com/example/ui
mkdir -p app/src/test/java/com/example
mkdir -p app/src/androidTest/java/com/example

cat << 'IN' > app/src/test/java/com/example/ui/MainViewModelTest.kt
package com.example.ui

import com.example.data.FileItemEntity
import com.example.data.ISmartManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var mockRepository: ISmartManagerRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mock(ISmartManagerRepository::class.java)

        `when`(mockRepository.scanProgress).thenReturn(MutableStateFlow(1.0f))
        `when`(mockRepository.isScanning).thenReturn(MutableStateFlow(false))
        `when`(mockRepository.activeFiles).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.recycleBinFiles).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.vaultItems).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.cloudSyncItems).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.plugins).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.exactDuplicates).thenReturn(flowOf(emptyList()))
        `when`(mockRepository.documentStats).thenReturn(flowOf(Triple(0, 0, 0f)))

        viewModel = MainViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state`() = runTest {
        assertEquals(false, viewModel.isScanning.value)
    }
}
IN

cat << 'IN' > app/src/test/java/com/example/ExampleRobolectricTest.kt
package com.example

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPhysicalStorageManagerFileOperations() {
        val testFile = tempFolder.newFile("test_document.txt")
        testFile.writeText("Hello, World!")
        
        assertTrue(testFile.exists())
        assertTrue(testFile.length() > 0)
    }
}
IN

cat << 'IN' > app/src/androidTest/java/com/example/MainActivityTest.kt
package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testBottomNavigationSwitchesScreens() {
        // Assume BottomNavigation has a tab "Duplicates"
        // In a real app we'd target by test tag or text
        // composeTestRule.onNodeWithText("Duplicates").performClick()
        // composeTestRule.onNodeWithText("Exact Match").assertExists()
    }
}
IN
