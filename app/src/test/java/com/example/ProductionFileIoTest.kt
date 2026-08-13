package com.example

import android.app.Application
import android.content.Context
import com.example.storage.ProductionFileIo
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProductionFileIoTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun validateFileNameRejectsUnsafeNames() {
        assertTrue(ProductionFileIo.validateFileName("photo.jpg").isSuccess)
        assertTrue(ProductionFileIo.validateFileName("../photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("folder/photo.jpg").isFailure)
        assertTrue(ProductionFileIo.validateFileName("bad\u0000name.jpg").isFailure)
    }

    @Test
    fun validateFileNameAcceptsNormalNames() {
        assertTrue(ProductionFileIo.validateFileName("invoice-2026.pdf").isSuccess)
        assertTrue(ProductionFileIo.validateFileName("holiday_photo.webp").isSuccess)
    }
}
