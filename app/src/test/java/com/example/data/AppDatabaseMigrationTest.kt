package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("test_migration_db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    private fun createVersion2Database(helper: SupportSQLiteOpenHelper) {
        val db = helper.writableDatabase
        
        // 1. Create files table at version 2 (prior to adding MIGRATION_2_3 columns)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `files` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `name` TEXT NOT NULL, 
                `path` TEXT NOT NULL, 
                `originalPath` TEXT NOT NULL DEFAULT '', 
                `category` TEXT NOT NULL, 
                `sizeBytes` INTEGER NOT NULL, 
                `dateModifiedMs` INTEGER NOT NULL DEFAULT 0, 
                `md5Hash` TEXT NOT NULL DEFAULT '', 
                `ocrText` TEXT NOT NULL DEFAULT '', 
                `tags` TEXT NOT NULL DEFAULT '', 
                `isVault` INTEGER NOT NULL DEFAULT 0, 
                `isRecycleBin` INTEGER NOT NULL DEFAULT 0, 
                `deletedTimestampMs` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // 2. Create vault_items table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `vault_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `originalName` TEXT NOT NULL, 
                `encryptedName` TEXT NOT NULL, 
                `encryptedFilePath` TEXT NOT NULL, 
                `ivBase64` TEXT NOT NULL, 
                `category` TEXT NOT NULL, 
                `sizeBytes` INTEGER NOT NULL, 
                `encryptedAtMs` INTEGER NOT NULL, 
                `isBiometricProtected` INTEGER NOT NULL
            )
        """.trimIndent())

        // 3. Create cloud_sync table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cloud_sync` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                `provider` TEXT NOT NULL, 
                `fileName` TEXT NOT NULL, 
                `fileSize` INTEGER NOT NULL, 
                `status` TEXT NOT NULL, 
                `lastSyncedMs` INTEGER NOT NULL, 
                `isCore` INTEGER NOT NULL
            )
        """.trimIndent())

        // Insert a sample file entry
        db.execSQL("""
            INSERT INTO `files` (id, name, path, category, sizeBytes) 
            VALUES (101, 'existing_photo.jpg', '/storage/emulated/0/DCIM/existing_photo.jpg', 'IMAGES', 2048)
        """.trimIndent())

        db.close()
    }

    @Test
    fun testMigrationFrom2To3PreservesDataAndAddsColumns() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        
        // 1. Setup version 2 schema and insert initial row
        createVersion2Database(helper)

        // 2. Open DB and apply migration 2 -> 3
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(db)

        // 3. Verify that the previous data is completely intact (Correctness requirement A)
        val cursor = db.query("SELECT * FROM files WHERE id = 101")
        assertTrue("Migrated database should contain the pre-existing record", cursor.moveToFirst())
        assertEquals("existing_photo.jpg", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("/storage/emulated/0/DCIM/existing_photo.jpg", cursor.getString(cursor.getColumnIndexOrThrow("path")))
        assertEquals(2048L, cursor.getLong(cursor.getColumnIndexOrThrow("sizeBytes")))

        // 4. Verify that new columns were successfully added with default values (Correctness requirement B)
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("visualSimilarityHash")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("semanticEmbeddingVersion")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("semanticIndexed")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("semanticEmbeddingString")))
        cursor.close()

        // 5. Verify that new plugins table was created successfully
        val pluginsCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='plugins'")
        assertTrue("Plugins table should be created as part of v3 migration", pluginsCursor.moveToFirst())
        pluginsCursor.close()

        db.close()
    }

    @Test
    fun testMissingMigrationWithoutFallbackThrowsException() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        
        // Setup version 2 database on disk
        createVersion2Database(helper)

        // Now, try to open the database using Room at version 3 without providing migration paths
        // and with fallbackToDestructiveMigration disabled. This should throw IllegalStateException.
        try {
            val roomDb = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "test_migration_db"
            )
            // Explicitly do not add migration MIGRATION_2_3, and do not call fallbackToDestructiveMigration.
            // Room builder has fallbackToDestructiveMigration disabled by default.
            .build()

            // Trigger database opening by accessing any DAO or query
            kotlinx.coroutines.runBlocking {
                roomDb.fileDao().getFileById(101)
            }
            fail("Expected IllegalStateException due to missing migration path without fallback fallbackToDestructiveMigration")
        } catch (e: IllegalStateException) {
            // Success: expected exception thrown
            assertTrue(e.message?.contains("migration") == true || e.message?.contains("Migration") == true)
        }
    }
}
