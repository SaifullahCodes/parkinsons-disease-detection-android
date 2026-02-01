package com.example.parkinsonsdiseasedetectionsystem.fragments.doctor;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.parkinsonsdiseasedetectionsystem.adapters.doctor.DoctorPatientAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DoctorPatientsFragment extends Fragment {

    private RecyclerView recyclerViewPatients;
    private DoctorPatientAdapter adapter;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private TextView tvPatientsCount;
    private EditText etSearchPatients;

    private final List<User> allPatients = new ArrayList<>();
    private LocalRepository localRepository;
    private String currentQuery = "";
    private String currentDoctorId;

    public DoctorPatientsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.doctor_fragment_patients, container, false);

        localRepository = LocalRepository.getInstance(requireContext());

        // Get Firebase Auth UID
        com.google.firebase.auth.FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            currentDoctorId = firebaseUser.getUid();
        } else {
            currentDoctorId = com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils.getUserId(requireContext());
        }

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        etSearchPatients = view.findViewById(R.id.etSearchPatients);
        tvPatientsCount = view.findViewById(R.id.tvPatientsCount);
        recyclerViewPatients = view.findViewById(R.id.recyclerViewPatients);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);

        recyclerViewPatients.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DoctorPatientAdapter(new ArrayList<>());
        recyclerViewPatients.setAdapter(adapter);

        setupSearchBar();

        // 1. Observe Local Data (Updates if data exists locally)
        observePatients();

        // 2. Fetch Fresh Data from Firebase (Fixes "No Users" issue)
        loadPatientsFromFirebase();

        return view;
    }

    private void setupSearchBar() {
        etSearchPatients.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence query, int start, int before, int count) {
                filterPatients(query.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void observePatients() {
        // This observes the local Room database. It's good for offline,
        // but might be empty initially.
        localRepository.observePatientsCloud().observe(getViewLifecycleOwner(), patients -> {
            if (patients != null && !patients.isEmpty()) {
                updateList(patients);
            }
        });
    }

    // 🔥 NEW METHOD: Fetches patients directly from Firebase
    private void loadPatientsFromFirebase() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        FirebaseRealtimeRepository.getInstance().getUsersByRole("patient", new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> users) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    updateList(users);
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load patients", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateList(List<User> users) {
        allPatients.clear();
        if (users != null) {
            allPatients.addAll(users);
        }
        filterPatients(currentQuery);
    }

    private void filterPatients(String query) {
        currentQuery = query;
        List<User> filtered = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            filtered.addAll(allPatients);
        } else {
            String lower = query.toLowerCase(Locale.US);
            for (User user : allPatients) {
                boolean matchesName = user.getName() != null && user.getName().toLowerCase(Locale.US).contains(lower);
                boolean matchesEmail = user.getEmail() != null && user.getEmail().toLowerCase(Locale.US).contains(lower);

                if (matchesName || matchesEmail) {
                    filtered.add(user);
                }
            }
        }

        adapter.updateList(filtered);

        if (tvPatientsCount != null) {
            tvPatientsCount.setText(String.valueOf(filtered.size()));
        }

        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (recyclerViewPatients != null) {
            recyclerViewPatients.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}