package com.example.parkinsonsdiseasedetectionsystem.fragments.doctor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.parkinsonsdiseasedetectionsystem.adapters.doctor.DoctorReportAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DoctorReportsFragment extends Fragment {

    private RecyclerView recyclerViewReports;
    private DoctorReportAdapter reportAdapter;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private TextView tvReportsCount;
    private ChipGroup chipGroupFilter;
    private MaterialButton btnSort;

    private final List<Report> allReports = new ArrayList<>();
    private final List<Report> filteredReports = new ArrayList<>();
    private LocalRepository localRepository;
    private FirebaseRealtimeRepository realtimeRepo;
    private String currentFilter = "All Reports";
    private boolean sortDescending = true;

    public DoctorReportsFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.doctor_fragment_reports, container, false);
        localRepository = LocalRepository.getInstance(requireContext());
        realtimeRepo = FirebaseRealtimeRepository.getInstance();

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        recyclerViewReports = view.findViewById(R.id.recyclerViewReports);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        tvReportsCount = view.findViewById(R.id.tvReportsCount);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);
        btnSort = view.findViewById(R.id.btnSort);

        toolbar.setTitle("Reports & Submissions");

        recyclerViewReports.setLayoutManager(new LinearLayoutManager(getContext()));

        // Pass Context and listener (if needed)
        reportAdapter = new DoctorReportAdapter(requireContext(), true);
        recyclerViewReports.setAdapter(reportAdapter);

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int chipId = checkedIds.get(0);
            Chip selectedChip = view.findViewById(chipId);
            if (selectedChip != null) {
                currentFilter = selectedChip.getText().toString();
                applyFilter();
            }
        });

        btnSort.setOnClickListener(v -> {
            sortDescending = !sortDescending;
            applyFilter();
        });

        progressBar.setVisibility(View.VISIBLE);
        observeReports();
        loadReportsFromRealtime();

        return view;
    }

    private void observeReports() {
        localRepository.observeAllReportsCloud().observe(getViewLifecycleOwner(), reports -> {
            progressBar.setVisibility(View.GONE);
            mergeReports(reports);
            applyFilter();
        });
    }

    private void loadReportsFromRealtime() {
        realtimeRepo.getAllReports(new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (!isAdded()) return;
                mergeReports(reports);
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    applyFilter();
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    applyFilter();
                });
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

    private void applyFilter() {
        String filter = currentFilter;
        filteredReports.clear();

        if ("All Reports".equals(filter)) {
            filteredReports.addAll(allReports);
        } else if ("Pending".equals(filter)) {
            for (Report r : allReports) {
                String verification = r.getDoctorVerification();
                if (verification != null && "Pending".equalsIgnoreCase(verification)) filteredReports.add(r);
            }
        } else if ("Verified".equals(filter)) {
            for (Report r : allReports) {
                String verification = r.getDoctorVerification();
                if (verification != null && ("Verified".equalsIgnoreCase(verification) || "Doctor Verified".equalsIgnoreCase(verification))) filteredReports.add(r);
            }
        }
        // ... (Keep existing voice/video filters) ...

        filteredReports.sort((a, b) -> sortDescending
                ? Long.compare(b.getCreatedAt(), a.getCreatedAt())
                : Long.compare(a.getCreatedAt(), b.getCreatedAt()));

        updateUI();
    }

    private void updateUI() {
        reportAdapter.updateData(filteredReports);

        int pendingCount = 0;
        for (Report r : allReports) {
            String v = r.getDoctorVerification();
            if (v != null && "Pending".equalsIgnoreCase(v)) pendingCount++;
        }

        tvReportsCount.setText(filteredReports.size() + " Reports (Pending: " + pendingCount + ")");

        if (filteredReports.isEmpty()) {
            recyclerViewReports.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerViewReports.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
        }
    }
}