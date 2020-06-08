package com.findyourvenue.findyourvenue.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.findyourvenue.findyourvenue.data.Venue

/**
 * Classe MyDatabase: implementazione del database utilizzando Room di Jetpack
 */
@Database(entities = [Venue::class],version = 1)
abstract class MyDatabase : RoomDatabase() {


    abstract fun venueDao() : VenueDao

    companion object {
        /** The only instance  */
        private var sInstance: MyDatabase? = null

        /**
         * Gets the singleton instance of SampleDatabase.
         *
         * @param context The context.
         * @return The singleton instance of SampleDatabase.
         */
        @Synchronized
        fun getInstance(context: Context): MyDatabase? {
            if (sInstance == null) {
                sInstance = Room
                    .databaseBuilder(
                        context.applicationContext,
                        MyDatabase::class.java, "ex"
                    )
                    .build()
            }
            return sInstance
        }
    }


}