package database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

import datastructures.SongDescriptor;

@Database(entities = {SongDescriptor.class}, version=1)
public abstract class AppDatabase extends RoomDatabase
{
    private static AppDatabase INSTANCE;
    public abstract SongDao userDao();

    public static AppDatabase getAppDatabase(Context context)
    {
        if (INSTANCE == null)
        {
            INSTANCE = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "user-database")
                    .build();
        }
        return INSTANCE;
    }

    public static void destroyInstance()
    {
        INSTANCE = null;
    }

}