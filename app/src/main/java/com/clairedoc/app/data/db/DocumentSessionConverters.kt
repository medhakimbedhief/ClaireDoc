package com.clairedoc.app.data.db

import androidx.room.TypeConverter
import com.clairedoc.app.data.model.SessionStatus
import com.clairedoc.app.data.model.SourceType

class DocumentSessionConverters {

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)
}
