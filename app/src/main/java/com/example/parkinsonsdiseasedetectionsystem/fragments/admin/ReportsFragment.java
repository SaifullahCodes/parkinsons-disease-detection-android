package com.example.parkinsonsdiseasedetectionsystem.fragments.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar; // Correct Import
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.HistoryDetailsActivity;
import com.example.parkinsonsdiseasedetectionsystem.adapters.admin.ReportAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class ReportsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private List<Report> reportList;
    private List<Report> filteredList;
    private ChipGroup chipGroupTestType, chipGroupStatus;
    private TextView tvTotalReports, tvVerifiedReports, tvPendingReports;
    private LinearLayout emptyStateLayout;
    private LocalRepository localRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports_admin, container, false);

        // 🔥 ACTIVATE TOOLBAR
        setupToolbar(view, "Reports Overview");

        localRepository = LocalRepository.getInstance(requireContext());

        initViews(view);
        setupRecyclerView();
        setupFilters();
        observeReports();

        return view;
    }

    // 🔥 TOOLBAR HELPER
    private void setupToolbar(View view, String title) {
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvToolbarTitle);
        if (toolbar != null) {
            if (tvTitle != null) tvTitle.setText(title);
            toolbar.setTitle("");
            toolbar.setNavigationOnClickListener(v -> {
                if (getActivity() instanceof AdminActivity) {
                    ((AdminActivity) getActivity()).openDrawer();
                }
            });
        }
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewReports);
        chipGroupTestType = view.findViewById(R.id.chipGroupTestType);
        chipGroupStatus = view.findViewById(R.id.chipGroupStatus);
        tvTotalReports = view.findViewById(R.id.tvTotalReports);
        tvVerifiedReports = view.findViewById(R.id.tvVerifiedReports);
        tvPendingReports = view.findViewById(R.id.tvPendingReports);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        reportList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new ReportAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(report -> {
            Intent intent = new Intent(requireContext(), HistoryDetailsActivity.class);
            intent.putExtra("REPORT_ID", report.getId());
            startActivity(intent);
        });
    }

    private void setupFilters() {
        chipGroupTestType.setOnCheckedChangeListener((group, checkedId) -> applyFilters());
        chipGroupStatus.setOnCheckedChangeListener((group, checkedId) -> applyFilters());
    }

    private void observeReports() {
        FirebaseRealtimeRepository.getInstance().getAllReports(new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (getActivity() == null) return;
                reportList.clear();
                if (reports != null) {
                    reportList.addAll(reports);
                }
                getActivity().runOnUiThread(() -> {
                    updateStats();
                    applyFilters();
                });
            }

            @Override
            public void onFailure(Exception e) {
                if(getActivity()!=null) Toast.makeText(getContext(), "Failed to load reports", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStats() {
        int total = reportList.size();
        int verified = 0;
        int pending = 0;
        for (Report report : reportList) {
            String status = report.getDoctorVerification();
            if (status != null) {
                if (status.toLowerCase().contains("verified")) verified++;
                else if (status.equalsIgnoreCase("Pending")) pending++;
            }
        }
        tvTotalReports.setText(String.valueOf(total));
        tvVerifiedReports.setText(String.valueOf(verified));
        tvPendingReports.setText(String.valueOf(pending));
    }

    private void applyFilters() {
        filteredList.clear();
        String testTypeFilter = "all";
        if (chipGroupTestType.getCheckedChipId() == R.id.chipVoice) testTypeFilter = "voice";
        else if (chipGroupTestType.getCheckedChipId() == R.id.chipVideo) testTypeFilter = "video";

        String statusFilter = "all";
        if (chipGroupStatus.getCheckedChipId() == R.id.chipVerifiedStatus) statusFilter = "verified";
        else if (chipGroupStatus.getCheckedChipId() == R.id.chipPendingStatus) statusFilter = "pending";
        else if (chipGroupStatus.getCheckedChipId() == R.id.chipRejectedStatus) statusFilter = "rejected";

        for (Report report : reportList) {
            boolean matchesType = testTypeFilter.equals("all") || (report.getRecordingType() != null && report.getRecordingType().equalsIgnoreCase(testTypeFilter));
            boolean matchesStatus = statusFilter.equals("all") ||
                    (statusFilter.equals("verified") && report.getDoctorVerification().toLowerCase().contains("verified")) ||
                    (statusFilter.equals("pending") && report.getDoctorVerification().equalsIgnoreCase("pending"));

            if (matchesType && matchesStatus) filteredList.add(report);
        }

        adapter.notifyDataSetChanged();
        emptyStateLayout.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filteredList.isEmpty() ? View.GONE : View.VISIBLE);
    }
}