package com.example.btchat.database

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter fun dateToLong(date: Date?): Long? = date?.time
    @TypeConverter fun longToDate(time: Long?): Date? = time?.let { Date(it) }
}
