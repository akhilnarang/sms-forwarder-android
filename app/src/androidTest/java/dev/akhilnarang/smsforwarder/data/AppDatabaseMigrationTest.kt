package dev.akhilnarang.smsforwarder.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule

class AppDatabaseMigrationTest {
    private val DB_NAME = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // Add @Test methods like `migrate4to5()` whenever a new migration is added.
    // Pattern:
    //   val db = helper.createDatabase(DB_NAME, 4).apply { /* insert sample data */; close() }
    //   helper.runMigrationsAndValidate(DB_NAME, 5, true, AppDatabase.MIGRATION_4_5)
    //   /* re-open via Room and assert data integrity */
}
