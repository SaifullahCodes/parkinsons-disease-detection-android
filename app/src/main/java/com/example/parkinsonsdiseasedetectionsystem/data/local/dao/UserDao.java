package com.example.parkinsonsdiseasedetectionsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.List;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Update
    void updateUser(User user);

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    LiveData<User> observeUserById(String userId);

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    User getUserById(String userId);

    @Query("SELECT * FROM users WHERE role = 'patient' ORDER BY name ASC")
    LiveData<List<User>> observePatients();

    @Query("SELECT * FROM users WHERE role = 'patient' ORDER BY name ASC")
    List<User> getAllPatients();

    @Query("SELECT COUNT(*) FROM users WHERE role = 'patient'")
    int getPatientCount();

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User getUserByEmail(String email);

    @Query("SELECT * FROM users WHERE role = 'doctor' AND isBlocked = 0 ORDER BY name ASC")
    LiveData<List<User>> observeDoctors();

    @Query("SELECT * FROM users WHERE role = 'doctor' AND isBlocked = 0 ORDER BY name ASC")
    List<User> getAllDoctors();

    @Query("SELECT * FROM users WHERE role = 'doctor' AND isBlocked = 0 LIMIT 1")
    User getFirstAvailableDoctor();

    // Admin queries
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    LiveData<List<User>> observeAllUsers();

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    List<User> getAllUsers();

    @Query("SELECT COUNT(*) FROM users")
    int getTotalUsersCount();

    @Query("SELECT COUNT(*) FROM users WHERE role = 'doctor'")
    int getDoctorCount();



    @Query("SELECT * FROM users WHERE role = 'doctor' ORDER BY name ASC")
    LiveData<List<User>> observeAllDoctors();

    @Query("DELETE FROM users WHERE id = :userId")
    void deleteUser(String userId);
}

