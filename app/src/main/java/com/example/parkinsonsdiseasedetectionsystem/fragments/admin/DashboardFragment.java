package com.example.parkinsonsdiseasedetectionsystem.fragments.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

// 🔥 CRITICAL FIX: Use the standard Toolbar to match your XML
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.adapters.admin.ReportAdapter;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private TextView tvWelcomeAdmin, tvCurrentDate;
    private TextView tvTotalPatients, tvTotalDoctors, tvTotalReports, tvPendingVerifications;
    private RecyclerView rvRecentReports;
    private MaterialButton btnViewAllReports;

    private ReportAdapter reportAdapter;
    private final List<Report> recentReports = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard_admin, container, false);

        initViews(view);
        setupRecyclerView();

        // Check and fix admin account role if needed
        checkAndFixAdminAccount();

        return view;
    }

    private void initViews(View view) {
        tvWelcomeAdmin = view.findViewById(R.id.tvWelcomeAdmin);
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate);
        tvTotalPatients = view.findViewById(R.id.tvTotalPatients);
        tvTotalDoctors = view.findViewById(R.id.tvTotalDoctors);
        tvTotalReports = view.findViewById(R.id.tvTotalReports);
        tvPendingVerifications = view.findViewById(R.id.tvPendingVerifications);
        rvRecentReports = view.findViewById(R.id.rvRecentReports);
        btnViewAllReports = view.findViewById(R.id.btnViewAllReports);

        String adminName = AuthUtils.getUserName(requireContext());
        tvWelcomeAdmin.setText("Welcome, " + (adminName.isEmpty() ? "Admin" : adminName));

        String currentDate = new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(new Date());
        tvCurrentDate.setText(currentDate);

        // 🔥 FIX: Use 'Toolbar' (Parent class) instead of 'MaterialToolbar'
        // This works with ANY toolbar type in your XML.
        Toolbar toolbar = view.findViewById(R.id.toolbar);

        if (toolbar != null && getActivity() instanceof AdminActivity) {
            toolbar.setNavigationOnClickListener(v -> ((AdminActivity) getActivity()).openDrawer());
        }

        btnViewAllReports.setOnClickListener(v -> {
            if (getActivity() instanceof AdminActivity) {
                ((AdminActivity) getActivity()).loadFragment(new ReportsFragment(), true);
            }
        });
    }

    private void setupRecyclerView() {
        rvRecentReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        reportAdapter = new ReportAdapter(recentReports);
        rvRecentReports.setAdapter(reportAdapter);
        
        // 🔹 CRITICAL: Set click listener for View button to work
        reportAdapter.setOnItemClickListener(report -> {
            android.content.Intent intent = new android.content.Intent(requireContext(), 
                com.example.parkinsonsdiseasedetectionsystem.activities.HistoryDetailsActivity.class);
            intent.putExtra("REPORT_ID", report.getId());
            startActivity(intent);
        });
    }

    private void checkAndFixAdminAccount() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Check if role is missing or wrong
                        String currentRole = snapshot.child("role").getValue(String.class);

                        if (!snapshot.exists() || !"admin".equals(currentRole)) {
                            if (getActivity() != null) {
                                Toast.makeText(getContext(), "Configuring Admin Access...", Toast.LENGTH_SHORT).show();
                            }

                            Map<String, Object> adminUpdates = new HashMap<>();
                            adminUpdates.put("role", "admin"); // Force role to admin

                            // If user doesn't exist at all, add basic fields
                            if (!snapshot.exists()) {
                                adminUpdates.put("id", uid);
                                adminUpdates.put("email", currentUser.getEmail());
                                adminUpdates.put("name", "Admin User");
                            }

                            FirebaseDatabase.getInstance().getReference("users").child(uid).updateChildren(adminUpdates)
                                    .addOnSuccessListener(aVoid -> {
                                        if (getActivity() != null) Toast.makeText(getContext(), "Admin Access Granted!", Toast.LENGTH_SHORT).show();
                                        loadDashboardData();
                                    });
                        } else {
                            loadDashboardData();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (getActivity() != null) Toast.makeText(getContext(), "DB Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadDashboardData() {
        if (!isAdded()) return;

        FirebaseRealtimeRepository firebaseRepo = FirebaseRealtimeRepository.getInstance();

        // 1. LOAD USERS
        firebaseRepo.getAllUsers(new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                if (!isAdded() || getActivity() == null) return;

                int patients = 0;
                int doctors = 0;
                if (users != null) {
                    for (User user : users) {
                        if ("patient".equalsIgnoreCase(user.getRole())) patients++;
                        else if ("doctor".equalsIgnoreCase(user.getRole())) doctors++;
                    }
                }

                final int fPatients = patients;
                final int fDoctors = doctors;

                getActivity().runOnUiThread(() -> {
                    tvTotalPatients.setText(String.valueOf(fPatients));
                    tvTotalDoctors.setText(String.valueOf(fDoctors));
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("AdminDashboard", "User Load Failed", e);
            }
        });

        // 2. LOAD REPORTS
        firebaseRepo.getAllReports(new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (!isAdded() || getActivity() == null) return;

                int total = 0;
                int pending = 0;
                recentReports.clear();

                if (reports != null) {
                    total = reports.size();
                    for (Report r : reports) {
                        if (r.getDoctorVerification() == null || "Pending".equalsIgnoreCase(r.getDoctorVerification())) {
                            pending++;
                        }
                    }

                    reports.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                    for (int i = 0; i < Math.min(5, reports.size()); i++) {
                        recentReports.add(reports.get(i));
                    }
                }

                final int fTotal = total;
                final int fPending = pending;

                getActivity().runOnUiThread(() -> {
                    tvTotalReports.setText(String.valueOf(fTotal));
                    tvPendingVerifications.setText(String.valueOf(fPending));
                    reportAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("AdminDashboard", "Report Load Failed", e);
            }
        });
    }
}