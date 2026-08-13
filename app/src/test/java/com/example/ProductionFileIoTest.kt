package com.example

import com.example.storage.ProductionFileIo
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionFileIoTest {

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
