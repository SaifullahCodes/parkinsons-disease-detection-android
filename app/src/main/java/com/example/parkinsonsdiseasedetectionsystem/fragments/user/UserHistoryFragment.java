package com.example.parkinsonsdiseasedetectionsystem.fragments.user;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.HistoryDetailsActivity;
import com.example.parkinsonsdiseasedetectionsystem.adapters.user.HistoryAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserHistoryFragment extends Fragment {

    private MaterialToolbar toolbar;
    private TextView tvTotalTests, tvDetected, tvNormal;
    private RecyclerView recyclerViewHistory;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;
    private MaterialButton btnSort;

    private HistoryAdapter historyAdapter;
    private final List<Report> reportHistory = new ArrayList<>();
    private LocalRepository localRepository;
    private String currentUserId;
    private boolean sortDescending = true;
    private boolean shouldRefreshOnResume = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localRepository = LocalRepository.getInstance(requireContext());
        currentUserId = AuthUtils.getUserId(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.user_fragment_history, container, false);

        toolbar = view.findViewById(R.id.toolbar);
        tvTotalTests = view.findViewById(R.id.tvTotalTests);
        tvDetected = view.findViewById(R.id.tvDetected);
        tvNormal = view.findViewById(R.id.tvNormal);
        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        progressBar = view.findViewById(R.id.progressBar);
        btnSort = view.findViewById(R.id.btnSort);

        setupRecyclerView();
        setupListeners();
        loadReports();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (shouldRefreshOnResume) {
            shouldRefreshOnResume = false;
            loadReports();
        }
    }

    private void setupRecyclerView() {
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(getContext()));

        // Pass click listener for Details and Delete
        historyAdapter = new HistoryAdapter(reportHistory, this::openDetailsActivity);
        historyAdapter.setOnDeleteClickListener(this::confirmDelete);

        recyclerViewHistory.setAdapter(historyAdapter);
    }

    private void setupListeners() {
        btnSort.setOnClickListener(v -> {
            sortDescending = !sortDescending;
            sortHistoryData();
        });

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_history) {
                // Add clear all logic here if needed
                return true;
            }
            return false;
        });
    }

    private void loadReports() {
        if (currentUserId == null) return;
        showLoading(true);

        // 1. Fetch from Firebase
        FirebaseRealtimeRepository.getInstance().getReportsForUser(currentUserId, new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (getActivity() == null) return;
                showLoading(false);
                processReports(reports);
            }

            @Override
            public void onFailure(Exception e) {
                // 2. Fallback to Local DB
                if (localRepository != null) {
                    localRepository.getReportsForUser(currentUserId, reports -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                showLoading(false);
                                processReports(reports);
                            });
                        }
                    });
                } else {
                    showLoading(false);
                }
            }
        });
    }

    private void processReports(List<Report> reports) {
        reportHistory.clear();
        if (reports != null) {
            Map<String, Report> unique = new HashMap<>();
            for (Report r : reports) {
                unique.put(r.getId(), r); // Deduplicate by ID
                if(localRepository != null) localRepository.insertReport(r); // Cache
            }
            reportHistory.addAll(unique.values());
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                sortHistoryData();
                updateUI();
            });
        }
    }

    private void sortHistoryData() {
        Collections.sort(reportHistory, (a, b) -> sortDescending
                ? Long.compare(b.getCreatedAt(), a.getCreatedAt())
                : Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        historyAdapter.notifyDataSetChanged();
    }

    private void updateUI() {
        if (reportHistory.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerViewHistory.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerViewHistory.setVisibility(View.VISIBLE);
            updateStats();
        }
    }

    private void updateStats() {
        int total = reportHistory.size();
        int detected = 0;
        for (Report r : reportHistory) {
            if (r.getAiPrediction() >= 0.5f) detected++;
        }
        tvTotalTests.setText(String.valueOf(total));
        tvDetected.setText(String.valueOf(detected));
        tvNormal.setText(String.valueOf(total - detected));
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    // --- DELETE LOGIC ---
    private void confirmDelete(Report report, int position) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Report")
                .setMessage("Are you sure? This will permanently delete the report.")
                .setPositiveButton("Delete", (dialog, which) -> performDelete(report, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete(Report report, int position) {
        showLoading(true);
        // 1. Delete from Cloud
        FirebaseRealtimeRepository.getInstance().deleteReport(report.getId(), new FirebaseRealtimeRepository.ReportCallback() {
            @Override
            public void onSuccess(Report r) {
                // 2. Delete from Local
                deleteLocal(report, position, "Deleted Successfully");
            }
            @Override
            public void onFailure(Exception e) {
                // 2. Delete from Local (Fallback)
                deleteLocal(report, position, "Deleted Locally (Offline)");
            }
        });
    }

    private void deleteLocal(Report report, int position, String message) {
        localRepository.deleteReport(report.getId(), () -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    historyAdapter.removeRecord(position);
                    updateUI();
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void openDetailsActivity(Report report) {
        Intent intent = new Intent(getContext(), HistoryDetailsActivity.class);
        intent.putExtra("REPORT_ID", report.getId());
        shouldRefreshOnResume = true; // Refresh list when coming back
        startActivity(intent);
    }
}