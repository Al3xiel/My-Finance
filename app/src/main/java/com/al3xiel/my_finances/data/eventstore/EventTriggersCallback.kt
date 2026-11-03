package com.al3xiel.my_finances.data.eventstore

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Triggers de inmutabilidad de `events` (docs/04 §2): ni UPDATE ni DELETE, ni por error de código. */
object EventTriggersCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER events_no_update BEFORE UPDATE ON events " +
                "BEGIN SELECT RAISE(ABORT, 'events is append-only'); END",
        )
        db.execSQL(
            "CREATE TRIGGER events_no_delete BEFORE DELETE ON events " +
                "BEGIN SELECT RAISE(ABORT, 'events is append-only'); END",
        )
    }
}
