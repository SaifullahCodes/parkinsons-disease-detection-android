package com.example.parkinsonsdiseasedetectionsystem.adapters.user;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<Report> reports;
    private OnItemClickListener listener;
    private OnDeleteClickListener deleteListener;

    public interface OnItemClickListener {
        void onItemClick(Report report);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Report report, int position);
    }

    public HistoryAdapter(List<Report> reports, OnItemClickListener listener) {
        this.reports = reports != null ? reports : new java.util.ArrayList<>();
        this.listener = listener;
    }

    public void setOnDeleteClickListener(OnDeleteClickListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_card, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        if (reports == null || position < 0 || position >= reports.size()) return;
        Report report = reports.get(position);
        if (report == null) return;
        holder.bind(report, listener, deleteListener);
    }

    @Override
    public int getItemCount() {
        return reports != null ? reports.size() : 0;
    }

    public void updateReports(List<Report> newReports) {
        this.reports = newReports != null ? newReports : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    public void removeRecord(int position) {
        if (position >= 0 && position < reports.size()) {
            reports.remove(position);
            notifyItemRemoved(position);
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        private View statusIndicator;
        private MaterialCardView iconContainerCard; // Changed to CardView
        private ImageView ivIcon;
        private TextView tvDate, tvTime, tvPrediction, tvConfidence, tvVerificationStatus;
        private ProgressBar progressConfidence;
        private MaterialButton btnDelete;
        private MaterialCardView mainCardView;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            iconContainerCard = itemView.findViewById(R.id.iconContainerCard);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvPrediction = itemView.findViewById(R.id.tvPrediction);
            progressConfidence = itemView.findViewById(R.id.progressConfidence);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            tvVerificationStatus = itemView.findViewById(R.id.tvVerificationStatus);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            mainCardView = (MaterialCardView) itemView;
        }

        public void bind(Report report, OnItemClickListener listener, OnDeleteClickListener deleteListener) {

            // 1. DATE & TIME
            Date date = new Date(report.getCreatedAt());
            tvDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date));
            tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date));

            // 2. DIAGNOSIS TEXT
            String diagnosis = "Unknown";
            if (report.getDiagnosisText() != null && !report.getDiagnosisText().isEmpty()) {
                diagnosis = report.getDiagnosisText();
            } else if (report.getAiResult() != null) {
                diagnosis = report.getAiResult();
            } else {
                diagnosis = (report.getAiPrediction() > 0.5) ? "Parkinson's Detected" : "Healthy";
            }

            // 3. COLOR LOGIC (Red for Parkinson, Green for Healthy)
            boolean isHealthy = diagnosis.contains("Healthy") || diagnosis.contains("Low");
            int statusColor = isHealthy ? Color.parseColor("#10B981") : Color.parseColor("#EF4444");

            // Apply Colors
            tvPrediction.setText(diagnosis);
            tvPrediction.setTextColor(statusColor);
            statusIndicator.setBackgroundColor(statusColor);
            iconContainerCard.setCardBackgroundColor(statusColor); // Circle Color
            progressConfidence.setProgressTintList(ColorStateList.valueOf(statusColor)); // Bar Color

            // 4. CONFIDENCE
            int confidence = Math.round(report.getAiPrediction() * 100f);
            if (report.getAiPrediction() < 0.5f) confidence = 100 - confidence; // Flip for healthy
            progressConfidence.setProgress(confidence);
            tvConfidence.setText(confidence + "%");

            // 5. ICON TYPE
            boolean isVideo = "video".equalsIgnoreCase(report.getRecordingType()) ||
                    (report.getVideoUrl() != null && !report.getVideoUrl().equals("No Video"));
            ivIcon.setImageResource(isVideo ? android.R.drawable.ic_menu_slideshow : android.R.drawable.ic_btn_speak_now);

            // 6. DOCTOR VERIFICATION STATUS
            // 6. DOCTOR VERIFICATION STATUS
            boolean isVerified = "Doctor Verified".equalsIgnoreCase(report.getDoctorVerification()) ||
                    "Verified".equalsIgnoreCase(report.getDoctorVerification());

            if (tvVerificationStatus != null) {
                if (isVerified) {
                    tvVerificationStatus.setText("Verified by Doctor");
                    tvVerificationStatus.setTextColor(Color.parseColor("#10B981")); // Green

                    // Set Icon
                    tvVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_verified, 0, 0, 0);

                    // OPTIONAL: Force the icon to be Green Programmatically
                    androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                            tvVerificationStatus,
                            ColorStateList.valueOf(Color.parseColor("#10B981"))
                    );

                } else {
                    tvVerificationStatus.setText("Pending Doctor Review");
                    tvVerificationStatus.setTextColor(Color.parseColor("#EF4444")); // Red

                    // Remove icon for pending
                    tvVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                }
            }
            // 7. CLICK LISTENERS
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    if (deleteListener != null) deleteListener.onDeleteClick(report, getAdapterPosition());
                });
            }

            mainCardView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(report);
            });
        }
    }
}