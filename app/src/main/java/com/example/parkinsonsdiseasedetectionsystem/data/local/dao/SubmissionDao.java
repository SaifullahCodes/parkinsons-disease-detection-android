package com.example.parkinsonsdiseasedetectionsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.parkinsonsdiseasedetectionsystem.models.Submission;

import java.util.List;

@Dao
public interface SubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSubmission(Submission submission);

    @Update
    void updateSubmission(Submission submission);

    @Query("SELECT * FROM submissions WHERE submissionId = :submissionId")
    Submission getSubmissionById(String submissionId);

    @Query("SELECT * FROM submissions WHERE userId = :userId ORDER BY createdAt DESC")
    LiveData<List<Submission>> getSubmissionsForUser(String userId);

    @Query("SELECT * FROM submissions WHERE userId = :userId ORDER BY createdAt DESC")
    List<Submission> getSubmissionsForUserSync(String userId);

    @Query("SELECT * FROM submissions WHERE doctorId = :doctorId AND status = 'pending' ORDER BY createdAt DESC")
    LiveData<List<Submission>> getPendingSubmissionsForDoctor(String doctorId);

    @Query("SELECT * FROM submissions WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    LiveData<List<Submission>> getAllSubmissionsForDoctor(String doctorId);

    @Query("SELECT * FROM submissions WHERE status = 'pending' AND sendTo = 'Doctor' ORDER BY createdAt DESC")
    LiveData<List<Submission>> getPendingDoctorSubmissions();

    @Query("SELECT COUNT(*) FROM submissions WHERE status = 'pending' AND sendTo = 'Doctor'")
    LiveData<Integer> getPendingDoctorSubmissionsCount();

    @Query("SELECT * FROM submissions WHERE userId = :userId AND sendTo = :sendTo ORDER BY createdAt DESC")
    LiveData<List<Submission>> getSubmissionsBySendTo(String userId, String sendTo);

    @Query("DELETE FROM submissions WHERE submissionId = :submissionId")
    void deleteSubmissionById(String submissionId);

    @Query("DELETE FROM submissions WHERE userId = :userId")
    void deleteSubmissionsForUser(String userId);

    // Admin queries
    @Query("SELECT * FROM submissions ORDER BY createdAt DESC")
    LiveData<List<Submission>> observeAllSubmissions();

    @Query("SELECT * FROM submissions ORDER BY createdAt DESC")
    List<Submission> getAllSubmissions();

    @Query("SELECT COUNT(*) FROM submissions")
    int getTotalSubmissionsCount();
}

