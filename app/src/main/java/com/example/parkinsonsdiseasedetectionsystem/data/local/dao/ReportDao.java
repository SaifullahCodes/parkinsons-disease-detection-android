package com.example.parkinsonsdiseasedetectionsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.parkinsonsdiseasedetectionsystem.models.Report;

import java.util.List;

@Dao
public interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertReport(Report report);

    @Query("SELECT * FROM reports WHERE patientId = :userId ORDER BY createdAt DESC")
    LiveData<List<Report>> observeReportsForUser(String userId);

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    LiveData<List<Report>> observeAllReports();

    @Query("SELECT * FROM reports WHERE patientId = :userId ORDER BY createdAt DESC")
    List<Report> getReportsForUser(String userId);

    @Query("SELECT * FROM reports WHERE patientId = :userId ORDER BY createdAt DESC LIMIT 1")
    Report getLatestReport(String userId);

    @Query("SELECT * FROM reports WHERE id = :reportId LIMIT 1")
    Report getReportById(String reportId);

    @Query("SELECT * FROM reports WHERE submissionId = :submissionId LIMIT 1")
    Report getReportBySubmissionId(String submissionId);

    @Query("DELETE FROM reports WHERE patientId = :userId")
    void deleteReportsForUser(String userId);

    @Query("DELETE FROM reports WHERE id = :reportId")
    void deleteReportById(String reportId);

    @Query("SELECT COUNT(*) FROM reports")
    int getReportsCount();

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    List<Report> getAllReportsSync();
}
