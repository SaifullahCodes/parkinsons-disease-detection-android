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

// 🔥 CORRECT IMPORT for Dialogs
import androidx.appcompat.app.AlertDialog;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.adapters.admin.DoctorAdapter;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class DoctorsFragment extends Fragment {

    private RecyclerView recyclerView;
    private DoctorAdapter doctorAdapter;
    private List<User> doctorList, filteredList;
    private ExtendedFloatingActionButton fabAddDoctor;
    private ChipGroup chipGroupFilter;
    private android.widget.TextView tvDoctorCount;
    private LocalRepository localRepository;
    private FirebaseRealtimeRepository firebaseRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_doctors_admin, container, false);

        setupToolbar(view, "Manage Doctors");

        localRepository = LocalRepository.getInstance(requireContext());
        firebaseRepository = FirebaseRealtimeRepository.getInstance();

        recyclerView = view.findViewById(R.id.rvDoctors);
        fabAddDoctor = view.findViewById(R.id.fabAddDoctor);
        chipGroupFilter = view.findViewById(R.id.chipGroupFilter);
        tvDoctorCount = view.findViewById(R.id.tvDoctorCount);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        doctorList = new ArrayList<>();
        filteredList = new ArrayList<>();

        doctorAdapter = new DoctorAdapter(requireContext(), new ArrayList<>(), new DoctorAdapter.DoctorActionListener() {
            @Override
            public void onViewDoctor(User doctor) {
                String createdDate = "Not available";
                if (doctor.getCreatedAt() > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                    createdDate = sdf.format(new java.util.Date(doctor.getCreatedAt()));
                }

                String statusText = doctor.isBlocked() ? "Pending Approval" : "✓ Approved";
                String details = "Name: " + doctor.getName() + "\n" +
                        "Email: " + doctor.getEmail() + "\n" +
                        "Phone: " + (TextUtils.isEmpty(doctor.getPhone()) ? "Not provided" : doctor.getPhone()) + "\n" +
                        "Role: Doctor\n" +
                        "Status: " + statusText + "\n" +
                        "Account Created: " + createdDate;

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Doctor Details")
                        .setMessage(details)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onApproveDoctor(User doctor) {
                // Check if doctor is already approved or pending
                if (doctor.isBlocked()) {
                    // Approve pending doctor
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Approve Doctor")
                            .setMessage("Approve " + doctor.getName() + "? They will be able to login after approval.")
                            .setPositiveButton("Approve", (dialog, which) -> {
                                firebaseRepository.updateUserBlockStatus(doctor.getId(), false, new FirebaseRealtimeRepository.UserCallback() {
                                    @Override
                                    public void onSuccess(User user) {
                                        localRepository.approveDoctor(user.getId(), null);
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                Toast.makeText(requireContext(), "✓ Doctor approved successfully", Toast.LENGTH_SHORT).show();
                                                observeDoctors();
                                            });
                                        }
                                    }
                                    @Override
                                    public void onFailure(Exception e) {
                                        if(getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                String errorMsg = e.getMessage();
                                                Toast.makeText(getContext(), "Failed to approve: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    // Doctor is already approved - option to block/unapprove
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Block Doctor")
                            .setMessage("Block " + doctor.getName() + "? They will not be able to login until unblocked.")
                            .setPositiveButton("Block", (dialog, which) -> {
                                firebaseRepository.updateUserBlockStatus(doctor.getId(), true, new FirebaseRealtimeRepository.UserCallback() {
                                    @Override
                                    public void onSuccess(User user) {
                                        localRepository.blockUser(user.getId(), null);
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                Toast.makeText(requireContext(), "✓ Doctor blocked successfully", Toast.LENGTH_SHORT).show();
                                                observeDoctors();
                                            });
                                        }
                                    }
                                    @Override
                                    public void onFailure(Exception e) {
                                        if(getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                String errorMsg = e.getMessage();
                                                Toast.makeText(getContext(), "Failed to block: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }

            @Override
            public void onEditDoctor(User doctor) {
                showEditDoctorDialog(doctor);
            }

            @Override
            public void onRemoveDoctor(User doctor) {
                // If doctor is approved, offer to block instead of delete
                if (!doctor.isBlocked()) {
                    // Block approved doctor
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Block Doctor")
                            .setMessage("Block " + doctor.getName() + "? They will not be able to login until unblocked.")
                            .setPositiveButton("Block", (dialog, which) -> {
                                firebaseRepository.updateUserBlockStatus(doctor.getId(), true, new FirebaseRealtimeRepository.UserCallback() {
                                    @Override
                                    public void onSuccess(User user) {
                                        localRepository.blockUser(user.getId(), null);
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                Toast.makeText(requireContext(), "✓ Doctor blocked successfully", Toast.LENGTH_SHORT).show();
                                                observeDoctors();
                                            });
                                        }
                                    }
                                    @Override
                                    public void onFailure(Exception e) {
                                        if(getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                String errorMsg = e.getMessage();
                                                Toast.makeText(getContext(), "Failed to block: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    // Delete pending/rejected doctor
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Remove Doctor")
                            .setMessage("Remove " + doctor.getName() + "? This cannot be undone.")
                            .setPositiveButton("Remove", (dialog, which) -> {
                                firebaseRepository.deleteUser(doctor.getId(), doctor.getEmail(), new FirebaseRealtimeRepository.UserCallback() {
                                    @Override
                                    public void onSuccess(User user) {
                                        // Delete from local database
                                        localRepository.deleteUserFromLocalDB(user.getId(), () -> {
                                            if (getActivity() != null) {
                                                getActivity().runOnUiThread(() -> {
                                                    Toast.makeText(requireContext(), "✓ Doctor removed successfully", Toast.LENGTH_SHORT).show();
                                                    observeDoctors();
                                                });
                                            }
                                        });
                                    }
                                    @Override
                                    public void onFailure(Exception e) {
                                        if(getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                String errorMsg = e.getMessage();
                                                Toast.makeText(getContext(), "Failed to remove: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        });

        recyclerView.setAdapter(doctorAdapter);
        fabAddDoctor.setOnClickListener(v -> showAddDoctorDialog());

        // Default Filter
        if (chipGroupFilter.getChildCount() > 0) {
            Chip allChip = view.findViewById(R.id.chipAll);
            if(allChip != null) allChip.setChecked(true);
        }

        setupChipFilter();
        observeDoctors();

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

    private void observeDoctors() {
        firebaseRepository.getUsersByRole("doctor", new FirebaseRealtimeRepository.UserListCallback() {
            @Override
            public void onSuccess(List<User> doctors) {
                if (getActivity() == null) return;
                doctorList.clear();
                if (doctors != null) doctorList.addAll(doctors);
                updateDoctorCount();
                applyFilter(getCurrentFilter());
            }
            @Override
            public void onFailure(Exception e) {
                if(getActivity()!=null) Toast.makeText(getContext(), "Failed to load doctors", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDoctorCount() {
        if (tvDoctorCount == null) return;
        int total = doctorList.size();
        int approved = 0;
        int pending = 0;
        for (User doctor : doctorList) {
            if (doctor.isBlocked()) {
                pending++; // Blocked = pending approval
            } else {
                approved++; // Not blocked = approved/active
            }
        }
        tvDoctorCount.setText(String.format("Total Doctors: %d | Approved: %d | Pending: %d", total, approved, pending));
    }

    private void setupChipFilter() {
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            String filter = "all";
            if (checkedId == R.id.chipVerified) filter = "approved";
            else if (checkedId == R.id.chipPending) filter = "pending";
            else if (checkedId == R.id.chipRejected) filter = "rejected";
            applyFilter(filter);
        });
    }

    private String getCurrentFilter() {
        int checkedId = chipGroupFilter.getCheckedChipId();
        if (checkedId == R.id.chipVerified) return "approved";
        if (checkedId == R.id.chipPending) return "pending";
        if (checkedId == R.id.chipRejected) return "rejected";
        return "all";
    }

    private void applyFilter(String filter) {
        filteredList.clear();
        if (filter.equals("all")) {
            filteredList.addAll(doctorList);
        } else {
            for (User doctor : doctorList) {
                // 🔹 CRITICAL: blocked=true means pending approval (for new doctors) or blocked (for approved doctors)
                // For filter purposes: pending = blocked (waiting for first approval)
                // approved = !blocked (already approved and active)
                // rejected = blocked (can be used for blocking approved doctors later)
                if (filter.equals("approved") && !doctor.isBlocked()) {
                    filteredList.add(doctor);
                } else if (filter.equals("pending") && doctor.isBlocked()) {
                    filteredList.add(doctor);
                } else if (filter.equals("rejected") && doctor.isBlocked()) {
                    // For now, rejected = blocked (same as pending)
                    // In future, could add a separate "rejected" field
                    filteredList.add(doctor);
                }
            }
        }
        if (doctorAdapter != null) doctorAdapter.updateData(new ArrayList<>(filteredList));
    }

    private void showAddDoctorDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);

        // 🔥 FIX: Explicitly use androidx.appcompat.app.AlertDialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add New Doctor")
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
                Toast.makeText(requireContext(), "Adding doctor...", Toast.LENGTH_SHORT).show();

                User newDoctor = new User("", name, email, phone, "doctor");
                newDoctor.setPassword(password);
                // 🔹 CRITICAL: Admin-created doctors are automatically approved
                // Only self-signup doctors need approval
                newDoctor.setBlocked(false);
                newDoctor.setCreatedAt(System.currentTimeMillis());

                // 🔹 CRITICAL: Pass context to use secondary Firebase App instance
                // This preserves the admin's session
                firebaseRepository.createUserWithEmailAndPassword(requireContext(), email, password, newDoctor, new FirebaseRealtimeRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        localRepository.insertUser(user);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "✓ Doctor added successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                observeDoctors();
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
                                    Toast.makeText(getContext(), "Failed to add doctor: " + (errorMsg != null ? errorMsg : "Unknown error"), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
            });
        });
        dialog.show();
    }

    private void showEditDoctorDialog(User doctor) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);

        etName.setText(doctor.getName());
        etEmail.setText(doctor.getEmail());
        etPhone.setText(doctor.getPhone());
        etPassword.setHint("Leave blank to keep password");

        // 🔥 FIX: Explicitly use androidx.appcompat.app.AlertDialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Doctor")
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

                doctor.setName(name);
                doctor.setEmail(email);
                doctor.setPhone(phone != null ? phone : "");

                // Update password if provided (stored in local DB only, Firebase Auth requires Admin SDK)
                if (!TextUtils.isEmpty(password)) {
                    if (password.length() < 6) {
                        etPassword.setError("Password must be at least 6 characters");
                        etPassword.requestFocus();
                        return;
                    }
                    doctor.setPassword(password);
                }

                // Disable button to prevent multiple submissions
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                Toast.makeText(requireContext(), "Updating doctor...", Toast.LENGTH_SHORT).show();

                firebaseRepository.updateUser(doctor, new FirebaseRealtimeRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        localRepository.updateUserProfile(user);
                        // Update password in local DB if provided
                        if (!TextUtils.isEmpty(password)) {
                            localRepository.updateUserPassword(user.getId(), password);
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "✓ Doctor updated successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                observeDoctors();
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