package com.materialy.music.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesTheDeclaredSchema() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, Migrations.MIGRATION_1_2).close()
    }

    companion object { private const val DB_NAME = "migration-test" }
}
