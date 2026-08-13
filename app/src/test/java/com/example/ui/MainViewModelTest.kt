package com.example.ui

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.example.VVFApplication
import com.example.data.SmartManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeSmartManagerRepository(context: Context) : SmartManagerRepository(context) {
    var verifyPinResult = true
    var changePinResult = true
    var lastVerifiedPin: String? = null
    var lastChangedOldPin: String? = null
    var lastChangedNewPin: String? = null
    val insertedFiles = mutableListOf<com.example.data.FileItemEntity>()
    var backgroundIndexWorkEnqueued = false
    override fun verifyVaultPin(inputPin: String, storedHash: String): Boolean { lastVerifiedPin = inputPin; return verifyPinResult }
    override fun changeVaultPin(oldPin: String, newPin: String): Boolean { lastChangedOldPin = oldPin; lastChangedNewPin = newPin; return changePinResult }
    override suspend fun insertFiles(files: List<com.example.data.FileItemEntity>) { insertedFiles.addAll(files) }
    override fun enqueueBackgroundIndexWork() { backgroundIndexWorkEnqueued = true }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = VVFApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeSmartManagerRepository
    private lateinit var viewModel: MainViewModel
    @Before fun setup() { Dispatchers.setMain(testDispatcher); val app = ApplicationProvider.getApplicationContext<Application>() as VVFApplication; fakeRepository = FakeSmartManagerRepository(app); app.repository = fakeRepository; viewModel = MainViewModel(app) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun appendPinDigit_collectsDigitsWithoutSecureStorage() { viewModel.appendPinDigit("1"); viewModel.appendPinDigit("2"); viewModel.appendPinDigit("3"); assertEquals("123", viewModel.enteredPin.value); assertNull(viewModel.pinError.value); assertFalse(viewModel.isVaultUnlocked.value); assertNull(fakeRepository.lastVerifiedPin) }
    @Test fun appendPinDigit_rejectsInvalidInput() { viewModel.appendPinDigit("x"); viewModel.appendPinDigit("12"); assertEquals("", viewModel.enteredPin.value); viewModel.appendPinDigit("9"); assertEquals("9", viewModel.enteredPin.value) }
    @Test fun changeVaultPin_success_returnsTrueAndClearsError() { fakeRepository.changePinResult = true; val result = viewModel.changeVaultPin("1234", "5678"); assertTrue(result); assertNull(viewModel.pinError.value); assertEquals("1234", fakeRepository.lastChangedOldPin); assertEquals("5678", fakeRepository.lastChangedNewPin) }
    @Test fun changeVaultPin_failure_returnsFalseAndSetsPinError() { fakeRepository.changePinResult = false; val result = viewModel.changeVaultPin("1234", "0000"); assertFalse(result); assertEquals("Failed to update PIN. Check current PIN.", viewModel.pinError.value); assertEquals("1234", fakeRepository.lastChangedOldPin); assertEquals("0000", fakeRepository.lastChangedNewPin) }
    @Test fun setSearchQuery_updatesSearchQueryState() { viewModel.setSearchQuery("invoice"); assertEquals("invoice", viewModel.searchQuery.value) }
    @Test fun selectTab_updatesSelectedTabIndexState() { viewModel.selectTab(2); assertEquals(2, viewModel.selectedTabIndex.value) }
    @Test fun persistedSafUri_saveAndLoad_works() { val uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"; viewModel.savePersistedFolderUri(uri); assertTrue(viewModel.getPersistedFolderUris().contains(uri)); assertTrue(viewModel.persistedFolderUris.value.contains(uri)) }
    @Test fun persistedSafUri_preventsDuplicates() { val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownloads"; viewModel.savePersistedFolderUri(uri); viewModel.savePersistedFolderUri(uri); assertEquals(1, viewModel.getPersistedFolderUris().count { it == uri }) }
    @Test fun persistedSafUri_removeUpdatesStateAndDoesNotCrash() { val uri = "content://com.android.externalstorage.documents/tree/primary%3APictures"; viewModel.savePersistedFolderUri(uri); viewModel.removePersistedFolderUri(uri); assertFalse(viewModel.persistedFolderUris.value.contains(uri)); assertFalse(viewModel.getPersistedFolderUris().contains(uri)) }
    @Test fun persistedSafUri_invalidUriHandledSafely() { val uri = ":::invalid_uri_string:::"; viewModel.savePersistedFolderUri(uri); assertTrue(viewModel.persistedFolderUris.value.contains(uri)); viewModel.removePersistedFolderUri(uri); assertFalse(viewModel.persistedFolderUris.value.contains(uri)) }
    @Test fun persistedSafUri_rescanAndProcessHandledSafely() { val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic"); viewModel.processPickedDirectoryUri(uri); testDispatcher.scheduler.advanceUntilIdle(); viewModel.rescanPersistedFolders(); testDispatcher.scheduler.advanceUntilIdle() }
}
