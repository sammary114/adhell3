package com.fusionjack.adhell3.db.migration;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;

public class Migration_17_18 extends Migration {

    public Migration_17_18(int startVersion, int endVersion) {
        super(startVersion, endVersion);
    }

    @Override
    public void migrate(SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE PolicyPackage ADD COLUMN active INTEGER DEFAULT 1");
    }
}
