package com.example.parkinsonsdiseasedetectionsystem.fragments.user;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.RoleSelectionActivity;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UserProfileFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView imgProfile;
    private FloatingActionButton btnEditPhoto;
    private TextView tvUserName, tvUserEmail, tvStage, tvTotalTests, tvPendingReviews, tvLastTest;
    private TextView tvCareStatus, tvDiagnosisSummary, tvAdviceSummary;
    private TextInputEditText etFullName, etEmail, etPhone, etDateOfBirth;
    private AutoCompleteTextView actvGender;
    private MaterialButton btnUpdateProfile, btnLogout;
    private LinearLayout btnChangePassword, btnPrivacySettings, btnHelpSupport;
    private Chip chipStage;

    private LocalRepository localRepository;
    private String currentUserId;
    private String currentUserName;
    private LiveData<List<Report>> reportsLiveData;

    private Uri imageUri;
    private SharedPreferences profilePrefs;

    private static final String PREF_PROFILE = "profile_prefs";
    private static final String KEY_FULL_NAME = "profile_full_name";
    private static final String KEY_EMAIL = "profile_email";
    private static final String KEY_PHONE = "profile_phone";
    private static final String KEY_GENDER = "profile_gender";
    private static final String KEY_DOB = "profile_dob";
    private static final String KEY_PHOTO = "profile_photo";
    private static final String KEY_ALLOW_DATA = "privacy_allow_data";
    private static final String KEY_ALLOW_EMAIL = "privacy_allow_email";

    public UserProfileFragment() {
        // Required empty constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            localRepository = LocalRepository.getInstance(requireContext());
            currentUserId = AuthUtils.getUserId(requireContext());
            currentUserName = AuthUtils.getUserName(requireContext());
            localRepository.ensureUserRecord(requireContext());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.user_fragment_profile, container, false);

        initViews(view);
        loadUserData();
        setupGenderDropdown();
        setupDatePicker();
        setupProfilePhotoPicker();
        setupUpdateProfile();
        setupLogout();
        setupActions();
        observeReportStream();

        return view;
    }

    @SuppressLint("WrongViewCast")
    private void initViews(View view) {
        if (getContext() == null) return;
        profilePrefs = requireContext().getSharedPreferences(PREF_PROFILE, android.content.Context.MODE_PRIVATE);

        imgProfile = view.findViewById(R.id.imgProfile);
        btnEditPhoto = view.findViewById(R.id.btnEditPhoto);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvStage = view.findViewById(R.id.tvStage);
        chipStage = view.findViewById(R.id.chipStage);

        etFullName = view.findViewById(R.id.etFullName);
        etEmail = view.findViewById(R.id.etEmail);
        etPhone = view.findViewById(R.id.etPhone);
        actvGender = view.findViewById(R.id.actvGender);
        etDateOfBirth = view.findViewById(R.id.etDateOfBirth);

        btnUpdateProfile = view.findViewById(R.id.btnUpdateProfile);
        btnLogout = view.findViewById(R.id.btnLogout);

        btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnPrivacySettings = view.findViewById(R.id.btnPrivacySettings);
        btnHelpSupport = view.findViewById(R.id.btnHelpSupport);

        tvTotalTests = view.findViewById(R.id.tvTotalTests);
        tvPendingReviews = view.findViewById(R.id.tvPendingReviews);
        tvLastTest = view.findViewById(R.id.tvLastTest);
        tvCareStatus = view.findViewById(R.id.tvCareStatus);
        tvDiagnosisSummary = view.findViewById(R.id.tvDiagnosisSummary);
        tvAdviceSummary = view.findViewById(R.id.tvAdviceSummary);
    }

    private void loadUserData() {
        if (getContext() == null) return;

        String userId = AuthUtils.getUserId(requireContext());
        if (userId == null || userId.isEmpty()) {
            loadBasicUserData();
            return;
        }

        // Use FirebaseRealtimeRepository (Corrected)
        FirebaseRealtimeRepository firebaseRepo = FirebaseRealtimeRepository.getInstance();

        firebaseRepo.getUserById(userId, new FirebaseRealtimeRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                // 🔥 CRITICAL FIX: Check if fragment is active before UI updates
                if (!isAdded() || getContext() == null) return;

                if (user != null) {
                    updateUIWithUserData(user);
                } else {
                    loadBasicUserData();
                }
            }

            @Override
            public void onFailure(Exception e) {
                // 🔥 CRITICAL FIX: Check if fragment is active
                if (!isAdded() || getContext() == null) return;

                // Fallback to Room database
                loadBasicUserData();
            }
        });
    }

    private void loadBasicUserData() {
        // 🔥 CRITICAL FIX: Ensure context exists
        if (!isAdded() || getContext() == null) return;

        User currentUser = AuthUtils.getCurrentUser(requireContext());

        String defaultName = currentUser != null && currentUser.getName() != null
                ? currentUser.getName() : AuthUtils.getUserName(requireContext());
        String defaultEmail = currentUser != null && !TextUtils.isEmpty(currentUser.getEmail())
                ? currentUser.getEmail() : AuthUtils.getUserEmail(requireContext());
        String defaultPhone = currentUser != null && currentUser.getPhone() != null
                ? currentUser.getPhone() : "";

        updateUIWithUserData(defaultName, defaultEmail, defaultPhone);
    }

    private void updateUIWithUserData(User user) {
        if (!isAdded() || getContext() == null || user == null) return;

        String name = user.getName() != null ? user.getName() : AuthUtils.getUserName(requireContext());
        String email = !TextUtils.isEmpty(user.getEmail()) ? user.getEmail() : AuthUtils.getUserEmail(requireContext());
        String phone = user.getPhone() != null ? user.getPhone() : "";

        updateUIWithUserData(name, email, phone);
    }

    private void updateUIWithUserData(String defaultName, String defaultEmail, String defaultPhone) {
        if (!isAdded() || getContext() == null) return;

        String email = defaultEmail;
        if (TextUtils.isEmpty(email)) {
            email = profilePrefs.getString(KEY_EMAIL, "");
        }
        if (TextUtils.isEmpty(email)) {
            email = "Email not set";
        }

        String fullName = profilePrefs.getString(KEY_FULL_NAME, defaultName);
        String phone = profilePrefs.getString(KEY_PHONE, defaultPhone);
        String gender = profilePrefs.getString(KEY_GENDER, "Male");
        String dob = profilePrefs.getString(KEY_DOB, "");
        String photo = profilePrefs.getString(KEY_PHOTO, null);

        if (tvUserName != null) tvUserName.setText(fullName);
        if (tvUserEmail != null) tvUserEmail.setText(email);
        if (etFullName != null) etFullName.setText(fullName);
        if (etEmail != null) etEmail.setText(email);
        if (etPhone != null) etPhone.setText(phone);
        if (actvGender != null) actvGender.setText(gender, false);
        if (etDateOfBirth != null) etDateOfBirth.setText(dob);

        if (!TextUtils.isEmpty(photo) && imgProfile != null) {
            try {
                imageUri = Uri.parse(photo);
                imgProfile.setImageURI(imageUri);
            } catch (Exception e) {
                // Ignore image error
            }
        }

        if (!TextUtils.isEmpty(email) && !email.equals("Email not set")) {
            profilePrefs.edit().putString(KEY_EMAIL, email).apply();
        }
    }

    private String getRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < TimeUnit.MINUTES.toMillis(1)) return "Just now";
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            return minutes + "m ago";
        }
        if (diff < TimeUnit.DAYS.toMillis(1)) {
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            return hours + "h ago";
        }
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        return days + "d ago";
    }

    private void setupGenderDropdown() {
        if (getContext() == null) return;
        String[] genders = {"Male", "Female", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, genders);
        actvGender.setAdapter(adapter);
    }

    private void setupDatePicker() {
        etDateOfBirth.setOnClickListener(v -> {
            if (getContext() == null) return;
            final Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                    (DatePicker view, int selectedYear, int selectedMonth, int selectedDay) -> {
                        String dob = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                        etDateOfBirth.setText(dob);
                    }, year, month, day);
            dialog.show();
        });
    }

    private void setupProfilePhotoPicker() {
        btnEditPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
            imageUri = data.getData();
            imgProfile.setImageURI(imageUri);
            if (getContext() != null) {
                profilePrefs.edit().putString(KEY_PHOTO, imageUri.toString()).apply();
                Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupUpdateProfile() {
        btnUpdateProfile.setOnClickListener(v -> {
            if (getContext() == null) return;
            String fullName = etFullName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String gender = actvGender.getText().toString();
            String dob = etDateOfBirth.getText().toString();
            String email = etEmail.getText().toString().trim();

            if (fullName.isEmpty()) {
                Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                User currentUser = AuthUtils.getCurrentUser(requireContext());
                if (currentUser != null && !TextUtils.isEmpty(currentUser.getEmail())) {
                    email = currentUser.getEmail();
                } else {
                    email = AuthUtils.getUserEmail(requireContext());
                }
            }

            profilePrefs.edit()
                    .putString(KEY_FULL_NAME, fullName)
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_PHONE, phone)
                    .putString(KEY_GENDER, gender)
                    .putString(KEY_DOB, dob)
                    .apply();

            AuthUtils.updateCurrentUserProfile(requireContext(), fullName, email, phone);
            if (localRepository != null) {
                User currentUser = AuthUtils.getCurrentUser(requireContext());
                if (currentUser != null) {
                    currentUser.setName(fullName);
                    currentUser.setPhone(phone);
                    localRepository.updateUserProfile(currentUser);
                }
            }

            tvUserName.setText(fullName);
            tvUserEmail.setText(email);
            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupLogout() {
        btnLogout.setOnClickListener(v -> {
            if (getContext() == null) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes", (DialogInterface dialog, int which) -> {
                        if (getContext() == null) return;

                        // Clear Firebase and Local Prefs
                        AuthUtils.logout(requireContext());

                        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

                        // Clear activity stack and go to RoleSelection
                        Intent intent = new Intent(requireContext(), RoleSelectionActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", (DialogInterface dialog, int which) -> dialog.dismiss())
                    .show();
        });
    }

    private void setupActions() {
        btnChangePassword.setOnClickListener(v -> openChangePasswordDialog());
        btnPrivacySettings.setOnClickListener(v -> openPrivacyDialog());
        btnHelpSupport.setOnClickListener(v -> openSupportEmail());
    }

    private void observeReportStream() {
        if (TextUtils.isEmpty(currentUserId) || getContext() == null) return;

        FirebaseRealtimeRepository firebaseRepo = FirebaseRealtimeRepository.getInstance();

        firebaseRepo.getReportsForUser(currentUserId, new FirebaseRealtimeRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                if (!isAdded() || getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    updateStatsFromReports(reports);
                    if (localRepository != null && reports != null) {
                        for (Report report : reports) {
                            localRepository.insertReport(report);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded() || getActivity() == null) return;

                if (localRepository != null) {
                    reportsLiveData = localRepository.observeReportsForUser(currentUserId);
                    if (reportsLiveData != null) {
                        reportsLiveData.observe(getViewLifecycleOwner(), UserProfileFragment.this::updateStatsFromReports);
                    }
                }
            }
        });
    }

    private void updateStatsFromReports(List<Report> reports) {
        if (!isAdded() || getContext() == null) return;

        int totalTests = reports != null ? reports.size() : 0;
        int pending = 0;
        Report latestReport = null;
        if (reports != null && !reports.isEmpty()) {
            latestReport = reports.get(0);
            for (Report report : reports) {
                String verification = report.getDoctorVerification();
                if (verification != null && "Pending".equalsIgnoreCase(verification)) {
                    pending++;
                }
            }
        }

        if (tvTotalTests != null) tvTotalTests.setText(String.valueOf(totalTests));
        if (tvPendingReviews != null) tvPendingReviews.setText(String.valueOf(pending));

        if (latestReport != null) {
            String severity = latestReport.getSeverityText();
            String stageLabel = "Stage: " + (severity != null ? severity : "--");
            if (tvStage != null) tvStage.setText(stageLabel);
            if (chipStage != null) {
                chipStage.setText(stageLabel);
            }
            if (tvLastTest != null) tvLastTest.setText(getRelativeTime(latestReport.getCreatedAt()));
            String verification = latestReport.getDoctorVerification();
            if (tvCareStatus != null) tvCareStatus.setText("Doctor status: " + (verification != null ? verification : "Not available"));
            String diagnosis = latestReport.getDiagnosisText();
            if (tvDiagnosisSummary != null) {
                tvDiagnosisSummary.setText(TextUtils.isEmpty(diagnosis)
                        ? "Diagnosis will appear after doctor review."
                        : diagnosis);
            }
            String advice = latestReport.getAdviceText();
            if (tvAdviceSummary != null) {
                tvAdviceSummary.setText(TextUtils.isEmpty(advice)
                        ? "Doctor recommendations will show up here once available."
                        : advice);
            }
        } else {
            if (tvStage != null) tvStage.setText("Stage: --");
            if (chipStage != null) chipStage.setText("Stage: --");
            if (tvLastTest != null) tvLastTest.setText("No tests yet");
            if (tvCareStatus != null) tvCareStatus.setText("Doctor status: Not available");
            if (tvDiagnosisSummary != null) tvDiagnosisSummary.setText("Complete an assessment to view AI and doctor insights.");
            if (tvAdviceSummary != null) tvAdviceSummary.setText("Care guidance will appear once a doctor reviews your submissions.");
        }
    }

    private void openChangePasswordDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);
        TextInputEditText etNew = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPass = etNew.getText() != null ? etNew.getText().toString().trim() : "";
                    String confirm = etConfirm.getText() != null ? etConfirm.getText().toString().trim() : "";
                    if (newPass.length() < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (AuthUtils.updateCurrentUserPassword(requireContext(), newPass)) {
                        Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Unable to update password", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openPrivacyDialog() {
        if (getContext() == null) return;
        boolean[] selections = {
                profilePrefs.getBoolean(KEY_ALLOW_DATA, false),
                profilePrefs.getBoolean(KEY_ALLOW_EMAIL, true)
        };
        CharSequence[] options = {"Share anonymized data", "Receive email updates"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Privacy & Permissions")
                .setMultiChoiceItems(options, selections, (dialog, which, isChecked) -> selections[which] = isChecked)
                .setPositiveButton("Save", (dialog, which) -> {
                    profilePrefs.edit()
                            .putBoolean(KEY_ALLOW_DATA, selections[0])
                            .putBoolean(KEY_ALLOW_EMAIL, selections[1])
                            .apply();
                    Toast.makeText(requireContext(), "Preferences updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openSupportEmail() {
        if (getContext() == null) return;
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:support@parkiscan.app"));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "ParkiScan Support");
        emailIntent.putExtra(Intent.EXTRA_TEXT,
                "Hi ParkiScan team,\nI need help with...");
        try {
            startActivity(Intent.createChooser(emailIntent, "Send email"));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
        }
    }
}