package com.example.parkinsonsdiseasedetectionsystem.fragments.doctor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.RoleSelectionActivity;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DoctorProfileFragment extends Fragment {

    private TextView tvDoctorName, tvSpecialization, tvLicenseNumber, tvEmail, tvPhone, tvHospital;
    private TextView tvStatTotalPatients, tvStatVerifiedReports, tvStatResponseTime;
    private MaterialButton btnEditProfile, btnChangePassword, btnLogout;

    private LocalRepository localRepository;
    private String currentDoctorId;

    // Preferences to save doctor-specific details locally
    private SharedPreferences doctorPrefs;
    private static final String PREF_DOC = "doctor_prefs";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.doctor_fragment_profile, container, false);

        localRepository = LocalRepository.getInstance(requireContext());
        currentDoctorId = AuthUtils.getUserId(requireContext());
        doctorPrefs = requireContext().getSharedPreferences(PREF_DOC, Context.MODE_PRIVATE);

        bindViews(view);
        populateDoctorInfo();
        setupListeners();
        loadStatsFromFirebase(); // 🔥 Load Real Data

        return view;
    }

    private void bindViews(View view) {
        tvDoctorName = view.findViewById(R.id.tvDoctorName);
        tvSpecialization = view.findViewById(R.id.tvSpecialization);
        tvLicenseNumber = view.findViewById(R.id.tvLicenseNumber);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvPhone = view.findViewById(R.id.tvPhone);
        tvHospital = view.findViewById(R.id.tvHospital);
        tvStatTotalPatients = view.findViewById(R.id.tvStatTotalPatients);
        tvStatVerifiedReports = view.findViewById(R.id.tvStatVerifiedReports);
        tvStatResponseTime = view.findViewById(R.id.tvStatResponseTime);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnLogout = view.findViewById(R.id.btnLogout);
    }

    private void populateDoctorInfo() {
        User currentUser = AuthUtils.getCurrentUser(requireContext());

        String name = currentUser != null ? currentUser.getName() : AuthUtils.getUserName(requireContext());
        String email = currentUser != null ? currentUser.getEmail() : AuthUtils.getUserEmail(requireContext());
        String phone = currentUser != null && !TextUtils.isEmpty(currentUser.getPhone())
                ? currentUser.getPhone() : "Not provided";

        // Load extra details from SharedPreferences
        String spec = doctorPrefs.getString("specialization", "Neurologist");
        String license = doctorPrefs.getString("license", "License: Pending");
        String hospital = doctorPrefs.getString("hospital", "ParkiScan Medical Center");

        tvDoctorName.setText(name);
        tvEmail.setText(email);
        tvPhone.setText(phone);
        tvSpecialization.setText(spec);
        tvLicenseNumber.setText(license);
        tvHospital.setText(hospital);
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v -> openEditProfileDialog());
        btnChangePassword.setOnClickListener(v -> openChangePasswordDialog());

        btnLogout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        AuthUtils.logout(requireContext());
                        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(requireContext(), RoleSelectionActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    // 🔥 LOAD REAL STATS FROM FIREBASE
    private void loadStatsFromFirebase() {
        FirebaseRealtimeRepository firebaseRepo = FirebaseRealtimeRepository.getInstance();

        // 1. Get Total Patients Count
        firebaseRepo.getUsersByRole("patient", new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        tvStatTotalPatients.setText(String.valueOf(users != null ? users.size() : 0))
                );
            }
            @Override
            public void onFailure(Exception e) { /* Ignore */ }
        });

        // 2. Get Report Stats (Verified Count & Avg Time)
        firebaseRepo.getAllReports(new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (!isAdded() || getActivity() == null) return;
                calculateReportStats(reports);
            }
            @Override
            public void onFailure(Exception e) { /* Ignore */ }
        });
    }

    private void calculateReportStats(List<Report> allReports) {
        if (allReports == null) return;

        int verifiedCount = 0;
        long totalResponseTimeMs = 0;
        int responseCount = 0;

        for (Report report : allReports) {
            // Check if verified by ANY doctor (or filter by currentDoctorId if needed)
            // Ideally check: if (report.getDoctorId().equals(currentDoctorId))

            String status = report.getDoctorVerification();
            if (status != null && (status.contains("Verified") || status.contains("verified"))) {

                // Only count stats for THIS doctor
                if (currentDoctorId != null && currentDoctorId.equals(report.getDoctorId())) {
                    verifiedCount++;

                    if (report.getVerifiedAt() > report.getCreatedAt()) {
                        totalResponseTimeMs += (report.getVerifiedAt() - report.getCreatedAt());
                        responseCount++;
                    }
                }
            }
        }

        final int finalVerified = verifiedCount;
        final double avgHours = responseCount > 0
                ? (double) totalResponseTimeMs / (responseCount * 3600000.0) // ms to hours
                : 0.0;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvStatVerifiedReports.setText(String.valueOf(finalVerified));
                tvStatResponseTime.setText(avgHours > 0 ? String.format(Locale.US, "%.1fh", avgHours) : "--");
            });
        }
    }

    private void openEditProfileDialog() {
        User currentUser = AuthUtils.getCurrentUser(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_doctor_profile, null, false);

        TextInputEditText etName = dialogView.findViewById(R.id.etDoctorName);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etDoctorPhone);
        TextInputEditText etSpecialization = dialogView.findViewById(R.id.etSpecialization);
        TextInputEditText etLicense = dialogView.findViewById(R.id.etLicense);
        TextInputEditText etHospital = dialogView.findViewById(R.id.etHospital);

        // Fill Current Data
        if (currentUser != null) {
            etName.setText(currentUser.getName());
            etPhone.setText(currentUser.getPhone());
        }
        etSpecialization.setText(doctorPrefs.getString("specialization", ""));
        etLicense.setText(doctorPrefs.getString("license", ""));
        etHospital.setText(doctorPrefs.getString("hospital", ""));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Profile")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String spec = etSpecialization.getText().toString().trim();
                    String lic = etLicense.getText().toString().trim();
                    String hosp = etHospital.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) return;

                    // 1. Save User Core Info to Firebase/Local
                    if (currentUser != null) {
                        currentUser.setName(name);
                        currentUser.setPhone(phone);
                        // Save to Firebase (You might need to add updateUser to your Repo or just use AuthUtils helper)
                        AuthUtils.updateCurrentUserProfile(requireContext(), name, currentUser.getEmail(), phone);

                        // Also update Firebase Realtime DB directly to be safe
                        FirebaseRealtimeRepository.getInstance().saveUserToRealtimeDatabase(currentUser, null);
                    }

                    // 2. Save Doctor Specifics to Local Prefs
                    doctorPrefs.edit()
                            .putString("specialization", spec)
                            .putString("license", lic)
                            .putString("hospital", hosp)
                            .apply();

                    populateDoctorInfo();
                    Toast.makeText(requireContext(), "Profile Updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openChangePasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);
        TextInputEditText etNew = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPass = etNew.getText().toString().trim();
                    String confirm = etConfirm.getText().toString().trim();

                    if (newPass.length() < 6) {
                        Toast.makeText(requireContext(), "Password too short", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (AuthUtils.updateCurrentUserPassword(requireContext(), newPass)) {
                        Toast.makeText(requireContext(), "Password updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}