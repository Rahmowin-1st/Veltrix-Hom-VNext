package com.veltrix.hom.vnext

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMigrationInstrumentedTest {
    @Test
    fun v1ProfileMigratesToV2WithoutDataLoss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "veltrix-migration-smoke.db"
        context.deleteDatabase(name)
        val factory = FrameworkSQLiteOpenHelperFactory()

        val v1 = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE local_profile (
                            accountId TEXT NOT NULL PRIMARY KEY,
                            displayName TEXT NOT NULL,
                            preferredLanguage TEXT NOT NULL,
                            timezone TEXT NOT NULL,
                            onboardingComplete INTEGER NOT NULL,
                            memoryEnabled INTEGER NOT NULL,
                            revision INTEGER NOT NULL,
                            updatedAtEpochMs INTEGER NOT NULL
                        )""".trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        v1.writableDatabase.execSQL(
            "INSERT INTO local_profile(accountId,displayName,preferredLanguage,timezone,onboardingComplete,memoryEnabled,revision,updatedAtEpochMs) VALUES ('migration-user','Before migration','en','UTC',1,1,7,12345)"
        )
        v1.close()

        val v2 = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertEquals(1, oldVersion)
                        assertEquals(2, newVersion)
                        VeltrixLocalDatabase.MIGRATION_1_2.migrate(db)
                    }
                }).build()
        )
        val db = v2.writableDatabase
        db.query("SELECT displayName, revision, accessibilityJson FROM local_profile WHERE accountId='migration-user'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Before migration", cursor.getString(0))
            assertEquals(7L, cursor.getLong(1))
            assertEquals("{}", cursor.getString(2))
        }
        db.query("PRAGMA table_info(local_profile)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "accessibilityJson") found = true
            assertTrue("v2 accessibility column missing", found)
        }
        v2.close()
        context.deleteDatabase(name)
    }
}
