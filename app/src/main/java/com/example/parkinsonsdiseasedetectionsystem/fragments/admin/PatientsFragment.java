package com.example.parkinsonsdiseasedetectionsystem.fragments.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// 🔥 CRITICAL FIX: Correct Import for Dialogs
import androidx.appcompat.app.AlertDialog;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.adapters.admin.PatientAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class PatientsFragment extends Fragment implements PatientAdapter.PatientActionListener {

    private RecyclerView recyclerView;
    private PatientAdapter adapter;
    private List<User> patientList;
    private List<User> filteredList;
    private ExtendedFloatingActionButton fabAddPatient;
    private TextInputEditText etSearchPatients;
    private android.widget.TextView tvPatientCount;
    private LocalRepository localRepository;
    private FirebaseRealtimeRepository firebaseRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_patients_admin, container, false);

        // Setup Toolbar
        setupToolbar(view, "Manage Patients");

        localRepository = LocalRepository.getInstance(requireContext());
        firebaseRepository = FirebaseRealtimeRepository.getInstance();

        recyclerView = view.findViewById(R.id.rvPatients);
        fabAddPatient = view.findViewById(R.id.fabAddPatient);
        etSearchPatients = view.findViewById(R.id.etSearchPatients);
        tvPatientCount = view.findViewById(R.id.tvPatientCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        patientList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new PatientAdapter(filteredList, this);
        recyclerView.setAdapter(adapter);

        etSearchPatients.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                filterPatients(etSearchPatients.getText().toString());
            }
        });

        fabAddPatient.setOnClickListener(v -> showAddPatientDialog());

        observePatients();

        return view;
    }

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

    private void observePatients() {
        firebaseRepository.getUsersByRole("patient", new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> patients) {
                if (getActivity() == null) return;
                patientList.clear();
                if (patients != null) {
                    patientList.addAll(patients);
                }
                updatePatientCount();
                filterPatients(etSearchPatients != null ? etSearchPatients.getText().toString() : "");
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("PatientsFragment", "Failed to load patients", e);
                if (getActivity() != null) Toast.makeText(getContext(), "Failed to load patients", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePatientCount() {
        if (tvPatientCount == null) return;
        int total = patientList.size();
        int active = 0;
        int blocked = 0;
        for (User patient : patientList) {
            if (patient.isBlocked()) blocked++;
            else active++;
        }
        tvPatientCount.setText(String.format("Total Patients: %d | Active: %d | Blocked: %d", total, active, blocked));
    }

    private void filterPatients(String query) {
        filteredList.clear();
        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(patientList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (User patient : patientList) {
                if (patient.getName().toLowerCase().contains(lowerQuery) ||
                        patient.getEmail().toLowerCase().contains(lowerQuery)) {
                    filteredList.add(patient);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updatePatientCount();
    }

    @Override
    public void onViewPatient(User patient) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Patient Details")
                .setMessage("Name: " + patient.getName() + "\nEmail: " + patient.getEmail() + "\nStatus: " + (patient.isBlocked() ? "Blocked" : "Active"))
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onEditPatient(User patient) {
        showEditPatientDialog(patient);
    }

    @Override
    public void onBlockPatient(User patient) {
        boolean newState = !patient.isBlocked();
        String action = newState ? "Block" : "Unblock";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(action + " Patient")
                .setMessage("Are you sure you want to " + action.toLowerCase() + " " + patient.getName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    firebaseRepository.updateUserBlockStatus(patient.getId(), newState, new FirebaseRealtimeRepository.UserCallback() {
                        @Override
                        public void onSuccess(User user) {
                            if (newState) localRepository.blockUser(user.getId(), null);
                            else localRepository.unblockUser(user.getId(), null);

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "✓ " + action + " successful", Toast.LENGTH_SHORT).show();
                                    observePatients();
                                });
                            }
                        }
                        @Override
                        public void onFailure(Exception e) {
                            if(getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    String errorMsg = e.getMessage();
                                    Toast.makeText(getContext(), "Failed to " + action.toLowerCase() + ": " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDeletePatient(User patient) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Patient")
                .setMessage("Delete " + patient.getName() + "? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    firebaseRepository.deleteUser(patient.getId(), patient.getEmail(), new FirebaseRealtimeRepository.UserCallback() {
                        @Override
                        public void onSuccess(User user) {
                            // Delete from local database
                            localRepository.deleteUserFromLocalDB(patient.getId(), () -> {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        Toast.makeText(requireContext(), "✓ Patient deleted successfully", Toast.LENGTH_SHORT).show();
                                        observePatients();
                                    });
                                }
                            });
                        }
                        @Override
                        public void onFailure(Exception e) {
                            if(getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    String errorMsg = e.getMessage();
                                    Toast.makeText(getContext(), "Failed to delete: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddPatientDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);

        // 🔥 Fixed Type: androidx.appcompat.app.AlertDialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add New Patient")
                .setView(dialogView)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                // Validation
                if (TextUtils.isEmpty(name)) {
                    etName.setError("Name is required");
                    etName.requestFocus();
                    return;
                }
                if (TextUtils.isEmpty(email)) {
                    etEmail.setError("Email is required");
                    etEmail.requestFocus();
                    return;
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    etEmail.setError("Invalid email format");
                    etEmail.requestFocus();
                    return;
                }
                if (TextUtils.isEmpty(password)) {
                    etPassword.setError("Password is required");
                    etPassword.requestFocus();
                    return;
                }
                if (password.length() < 6) {
                    etPassword.setError("Password must be at least 6 characters");
                    etPassword.requestFocus();
                    return;
                }

                // Disable button to prevent multiple submissions
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                Toast.makeText(requireContext(), "Adding patient...", Toast.LENGTH_SHORT).show();

                User newUser = new User("", name, email, phone, "patient");
                newUser.setPassword(password);
                newUser.setBlocked(false);
                newUser.setCreatedAt(System.currentTimeMillis());

                // 🔹 CRITICAL: Pass context to use secondary Firebase App instance
                // This preserves the admin's session
                firebaseRepository.createUserWithEmailAndPassword(requireContext(), email, password, newUser, new FirebaseRealtimeRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        localRepository.insertUser(user);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "✓ Patient added successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                observePatients();
                            });
                        }
                    }
                    @Override
                    public void onFailure(Exception e) {
                        if(getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                String errorMsg = e.getMessage();
                                if (errorMsg != null && errorMsg.contains("email-already-in-use")) {
                                    etEmail.setError("Email already exists");
                                } else {
                                    Toast.makeText(getContext(), "Failed to add patient: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            });
        });
        dialog.show();
    }

    private void showEditPatientDialog(User patient) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);

        etName.setText(patient.getName());
        etEmail.setText(patient.getEmail());
        etPhone.setText(patient.getPhone());
        etPassword.setHint("Leave blank to keep password");

        // 🔥 Fixed Type: androidx.appcompat.app.AlertDialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Patient")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                // Validation
                if (TextUtils.isEmpty(name)) {
                    etName.setError("Name is required");
                    etName.requestFocus();
                    return;
                }
                if (TextUtils.isEmpty(email)) {
                    etEmail.setError("Email is required");
                    etEmail.requestFocus();
                    return;
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    etEmail.setError("Invalid email format");
                    etEmail.requestFocus();
                    return;
                }

                patient.setName(name);
                patient.setEmail(email);
                patient.setPhone(phone != null ? phone : "");

                // Update password if provided (stored in local DB only, Firebase Auth requires Admin SDK)
                if (!TextUtils.isEmpty(password)) {
                    if (password.length() < 6) {
                        etPassword.setError("Password must be at least 6 characters");
                        etPassword.requestFocus();
                        return;
                    }
                    patient.setPassword(password);
                }

                // Disable button to prevent multiple submissions
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                Toast.makeText(requireContext(), "Updating patient...", Toast.LENGTH_SHORT).show();

                firebaseRepository.updateUser(patient, new FirebaseRealtimeRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        localRepository.updateUserProfile(user);
                        // Update password in local DB if provided
                        if (!TextUtils.isEmpty(password)) {
                            localRepository.updateUserPassword(user.getId(), password);
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "✓ Patient updated successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                observePatients();
                            });
                        }
                    }
                    @Override
                    public void onFailure(Exception e) {
                        if(getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                String errorMsg = e.getMessage();
                                Toast.makeText(getContext(), "Failed to update: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            });
        });
        dialog.show();
    }
}