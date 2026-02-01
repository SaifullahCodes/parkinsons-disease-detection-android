package com.example.parkinsonsdiseasedetectionsystem.fragments.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar; // Correct Import
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.RoleSelectionActivity;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {

    private Switch switchNotifications;
    private TextView tvThemeMode;
    private MaterialCardView cardEditProfile;
    private MaterialCardView cardChangePassword;
    private MaterialCardView cardContactSupport;
    private com.google.android.material.button.MaterialButton btnLogout;
    private LocalRepository localRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_admin, container, false);

        // 🔥 ACTIVATE TOOLBAR
        setupToolbar(view, "Settings");

        localRepository = LocalRepository.getInstance(requireContext());
        initViews(view);
        setupListeners();

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
        switchNotifications = view.findViewById(R.id.switchNotifications);
        tvThemeMode = view.findViewById(R.id.tvThemeMode);
        cardEditProfile = view.findViewById(R.id.cardEditProfile);
        cardChangePassword = view.findViewById(R.id.cardChangePassword);
        cardContactSupport = view.findViewById(R.id.cardContactSupport);
        btnLogout = view.findViewById(R.id.btnLogout);
    }

    private void setupListeners() {
        if (cardEditProfile != null) cardEditProfile.setOnClickListener(v -> openEditProfileDialog());
        if (cardChangePassword != null) cardChangePassword.setOnClickListener(v -> openChangePasswordDialog());
        if (cardContactSupport != null) cardContactSupport.setOnClickListener(v -> openSupportEmail());
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());

        if (tvThemeMode != null) {
            tvThemeMode.setOnClickListener(v -> Toast.makeText(getContext(), "Theme changing not implemented yet", Toast.LENGTH_SHORT).show());
        }
    }

    private void openChangePasswordDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        TextInputEditText etNew = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPass = etNew.getText().toString().trim();
                    String confirm = etConfirm.getText().toString().trim();
                    if (newPass.length() < 6 || !newPass.equals(confirm)) {
                        Toast.makeText(requireContext(), "Invalid or mismatching password", Toast.LENGTH_SHORT).show();
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

    private void openSupportEmail() {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(android.net.Uri.parse("mailto:admin@parkiscan.app"));
        try {
            startActivity(Intent.createChooser(emailIntent, "Send email"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditProfileDialog() {
        User currentUser = AuthUtils.getCurrentUser(requireContext());
        if (currentUser == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_user, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);

        etName.setText(currentUser.getName());
        etEmail.setText(currentUser.getEmail());
        etPhone.setText(currentUser.getPhone());
        etPassword.setHint("Leave blank to keep password");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Profile")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        currentUser.setName(name);
                        currentUser.setPhone(phone);
                        localRepository.updateUserProfile(currentUser);
                        AuthUtils.saveUserSession(requireContext(), currentUser);
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    AuthUtils.logout(requireContext());
                    Intent intent = new Intent(requireContext(), RoleSelectionActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}