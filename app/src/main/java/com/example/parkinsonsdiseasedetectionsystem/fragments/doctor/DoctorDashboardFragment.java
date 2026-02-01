package com.example.parkinsonsdiseasedetectionsystem.fragments.doctor;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.DoctorActivity;
import com.example.parkinsonsdiseasedetectionsystem.adapters.doctor.DoctorReportAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DoctorDashboardFragment extends Fragment {

    private TextView tvDoctorName, tvCurrentDate, tvTodayPatients, tvPendingReports,
            tvTotalUsers, tvTotalReports, tvPendingCount, tvVerifiedCount;
    private ImageView ivNotification;
    private MaterialButton btnViewAllReports;
    private RecyclerView recyclerViewRecentReports;

    private DoctorReportAdapter adapter;
    private final List<Report> allReports = new ArrayList<>();
    private final List<User> allPatients = new ArrayList<>();
    private LocalRepository localRepository;
    private String currentDoctorId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.doctor_fragment_dashboard, container, false);

        // Bind views
        tvDoctorName = view.findViewById(R.id.tvDoctorName);
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate);
        tvTodayPatients = view.findViewById(R.id.tvTodayPatients);
        tvPendingReports = view.findViewById(R.id.tvPendingReports);
        tvTotalUsers = view.findViewById(R.id.tvTotalUsers);
        tvTotalReports = view.findViewById(R.id.tvTotalReports);
        tvPendingCount = view.findViewById(R.id.tvPendingCount);
        tvVerifiedCount = view.findViewById(R.id.tvVerifiedCount);
        ivNotification = view.findViewById(R.id.ivNotification);
        btnViewAllReports = view.findViewById(R.id.btnViewAllReports);
        recyclerViewRecentReports = view.findViewById(R.id.recyclerViewRecentReports);

        localRepository = LocalRepository.getInstance(requireContext());
        localRepository.ensureUserRecord(requireContext());

        // Get Firebase Auth UID
        com.google.firebase.auth.FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentDoctorId = firebaseUser.getUid();
        } else {
            currentDoctorId = AuthUtils.getUserId(requireContext());
        }

        tvDoctorName.setText(AuthUtils.getUserName(requireContext()));

        String currentDate = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(new Date());
        tvCurrentDate.setText(currentDate);

        recyclerViewRecentReports.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DoctorReportAdapter(requireContext(), false);
        recyclerViewRecentReports.setAdapter(adapter);

        btnViewAllReports.setOnClickListener(v -> {
            if (getActivity() instanceof DoctorActivity) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_reports);
                }
            }
        });

        ivNotification.setOnClickListener(v ->
                Toast.makeText(getContext(), "Notifications Clicked", Toast.LENGTH_SHORT).show());

        observeData();
        loadRealtimeFallback();

        return view;
    }

    private void observeData() {
        // Observe Reports
        localRepository.observeAllReportsCloud().observe(getViewLifecycleOwner(), reports -> {
            mergeReports(reports);
            updateRecentList();
            calculateAndUpdateStats();
        });

        // Observe Patients (Local DB)
        localRepository.observePatientsCloud().observe(getViewLifecycleOwner(), patients -> {
            // We don't clear here immediately to avoid flickering if local DB is empty
            if (patients != null && !patients.isEmpty()) {
                allPatients.clear();
                allPatients.addAll(patients);
                calculateAndUpdateStats();
            }
        });
    }

    private void loadRealtimeFallback() {
        FirebaseRealtimeRepository firebaseRepo = FirebaseRealtimeRepository.getInstance();

        // 1. Fetch Reports
        firebaseRepo.getAllReports(new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                mergeReports(reports);
                if (isAdded()) {
                    updateRecentList();
                    calculateAndUpdateStats();
                }
            }
            @Override
            public void onFailure(Exception e) { /* Ignore */ }
        });

        // 2. Fetch Patients (Fixes "Total Users" count)
        loadTotalPatients();
    }

    // 🔥 NEW METHOD: Directly fetches users with role "patient"
    private void loadTotalPatients() {
        FirebaseRealtimeRepository.getInstance().getUsersByRole("patient", new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    allPatients.clear();
                    if (users != null) {
                        allPatients.addAll(users);
                    }
                    // Update the UI counter directly
                    tvTotalUsers.setText(String.valueOf(allPatients.size()));

                    // Update other stats that rely on patient data
                    calculateAndUpdateStats();

                    Log.d("DoctorDashboard", "Loaded " + allPatients.size() + " patients from Firebase");
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("DoctorDashboard", "Failed to load patients", e);
            }
        });
    }

    private void mergeReports(List<Report> incoming) {
        if (incoming == null) return;
        java.util.Map<String, Report> map = new java.util.HashMap<>();
        for (Report r : allReports) {
            if (r != null && r.getId() != null) map.put(r.getId(), r);
        }
        for (Report r : incoming) {
            if (r != null && r.getId() != null) map.put(r.getId(), r);
        }
        allReports.clear();
        allReports.addAll(map.values());
        allReports.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
    }

    private void updateRecentList() {
        List<Report> recent = new ArrayList<>();
        List<Report> sortedReports = new ArrayList<>(allReports);
        sortedReports.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

        for (int i = 0; i < Math.min(5, sortedReports.size()); i++) {
            recent.add(sortedReports.get(i));
        }
        adapter.updateData(recent);
    }

    private void calculateAndUpdateStats() {
        if (TextUtils.isEmpty(currentDoctorId)) return;

        int totalReports = 0;
        int pendingReports = 0;
        int verifiedReports = 0;
        long startOfDay = getStartOfDayMillis();
        Set<String> todayPatientIds = new HashSet<>();

        for (Report report : allReports) {
            totalReports++;

            if (report.getPatientId() != null && !report.getPatientId().isEmpty()) {
                if (report.getCreatedAt() >= startOfDay) {
                    todayPatientIds.add(report.getPatientId());
                }
            }

            String verification = report.getDoctorVerification();
            if (verification != null) {
                if ("Pending".equalsIgnoreCase(verification) || "pending".equalsIgnoreCase(verification)) {
                    pendingReports++;
                } else if (verification.toLowerCase().contains("verified")) {
                    verifiedReports++;
                }
            }
        }

        // Use the size of the list we populated from Firebase
        int totalPatients = allPatients.size();

        final int finalTotalReports = totalReports;
        final int finalPendingReports = pendingReports;
        final int finalVerifiedReports = verifiedReports;
        final int finalTotalPatients = totalPatients;
        final int finalTodayPatients = todayPatientIds.size();

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                tvTotalReports.setText(String.valueOf(finalTotalReports));
                tvPendingReports.setText(String.valueOf(finalPendingReports));
                tvPendingCount.setText(String.valueOf(finalPendingReports));
                tvVerifiedCount.setText(String.valueOf(finalVerifiedReports));
                tvTodayPatients.setText(String.valueOf(finalTodayPatients));

                // Ensure Total Users is updated here too
                tvTotalUsers.setText(String.valueOf(finalTotalPatients));
            });
        }
    }

    private long getStartOfDayMillis() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}