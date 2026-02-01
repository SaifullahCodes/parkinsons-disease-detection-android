package com.example.parkinsonsdiseasedetectionsystem.adapters.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    private final List<Report> reportList;
    private OnItemClickListener itemClickListener;

    public interface OnItemClickListener {
        void onItemClick(Report report);
    }

    public ReportAdapter(List<Report> reportList) {
        this.reportList = reportList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Report report = reportList.get(position);
        
        holder.tvPatientName.setText(report.getPatientName() != null ? report.getPatientName() : "Unknown Patient");
        holder.tvReportDate.setText(formatDate(report.getCreatedAt()));
        
        // Set recording type
        String recordingType = report.getRecordingType() != null ? report.getRecordingType() : "voice";
        holder.tvRecordingType.setText(recordingType.substring(0, 1).toUpperCase() + recordingType.substring(1) + " Test");
        
        // Set AI result
        String aiResult = report.getAiResult() != null ? report.getAiResult() : 
                (report.getAiPrediction() >= 0.5f ? "Parkinson's Detected" : "Healthy");
        holder.tvResult.setText("AI Result: " + aiResult);
        
        // Set status
        String status = report.getDoctorVerification() != null ? report.getDoctorVerification() : "Pending";
        holder.tvStatus.setText("Status: " + status);
        
        // Set status color
        if ("Doctor Verified".equalsIgnoreCase(status) || "Verified".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        } else if ("Pending".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.darker_gray));
        }
        
        // Set click listener on entire item
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(report);
            }
        });
        
        // Set click listener on button
        if (holder.btnViewReport != null) {
            holder.btnViewReport.setOnClickListener(v -> {
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(report);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientName, tvReportDate, tvStatus, tvRecordingType, tvResult;
        com.google.android.material.button.MaterialButton btnViewReport;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvReportDate = itemView.findViewById(R.id.tvDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvRecordingType = itemView.findViewById(R.id.tvRecordingType);
            tvResult = itemView.findViewById(R.id.tvResult);
            btnViewReport = itemView.findViewById(R.id.btnViewReport);
        }
    }
}

