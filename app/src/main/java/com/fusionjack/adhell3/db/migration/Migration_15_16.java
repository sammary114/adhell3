package com.fusionjack.adhell3.db.migration;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;

public class Migration_15_16 extends Migration {

    public Migration_15_16(int startVersion, int endVersion) {
        super(startVersion, endVersion);
    }

    @Override
    public void migrate(SupportSQLiteDatabase supportSQLiteDatabase) {
        supportSQLiteDatabase.execSQL("ALTER TABLE AppInfo ADD COLUMN disabled INTEGER DEFAULT 0");
    }
}
