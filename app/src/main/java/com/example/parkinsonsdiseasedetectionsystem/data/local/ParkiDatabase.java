package com.example.parkinsonsdiseasedetectionsystem.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.ReportDao;
import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.SubmissionDao;
import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.UserDao;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.Submission;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

@Database(
        entities = {User.class, Submission.class, Report.class},
        version = 4, // <--- CHANGED FROM 3 TO 4
        exportSchema = false
)
public abstract class ParkiDatabase extends RoomDatabase {

    private static volatile ParkiDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract SubmissionDao submissionDao();
    public abstract ReportDao reportDao();

    public static ParkiDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ParkiDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    ParkiDatabase.class,
                                    "parki_scan.db"
                            )
                            // This line wipes the old database so the new one (with videoUrl) can be created
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}