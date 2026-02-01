package com.example.parkinsonsdiseasedetectionsystem.adapters.doctor;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.ReportDetailActivity;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DoctorReportAdapter extends RecyclerView.Adapter<DoctorReportAdapter.ReportViewHolder> {

    private final ArrayList<Report> reportList = new ArrayList<>();
    private final Context context;
    private final boolean showViewButton;

    public DoctorReportAdapter(Context context, boolean showViewButton) {
        this.context = context;
        this.showViewButton = showViewButton;
    }

    public void updateData(List<Report> newReports) {
        reportList.clear();
        if (newReports != null) {
            reportList.addAll(newReports);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reportList.get(position);

        holder.tvPatientName.setText(report.getPatientName() != null ? report.getPatientName() : "Unknown Patient");
        holder.tvRecordingType.setText(report.getRecordingType() != null ? report.getRecordingType() : "Unknown");
        holder.tvStatus.setText(report.getDoctorVerification() != null ? report.getDoctorVerification() : "Pending");
        holder.tvResult.setText(report.getSeverityText() != null ? report.getSeverityText() : "N/A");

        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(report.getCreatedAt()));
        holder.tvDate.setText(formattedDate);

        // 🔹 Hide or show the "View" button based on flag
        if (showViewButton) {
            holder.btnViewReport.setVisibility(View.VISIBLE);
            holder.btnViewReport.setOnClickListener(v -> {
                Intent intent = new Intent(context, ReportDetailActivity.class);
                intent.putExtra("REPORT_ID", report.getId());
                context.startActivity(intent);
            });
        } else {
            holder.btnViewReport.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientName, tvRecordingType, tvStatus, tvResult, tvDate;
        Button btnViewReport;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvRecordingType = itemView.findViewById(R.id.tvRecordingType);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvResult = itemView.findViewById(R.id.tvResult);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnViewReport = itemView.findViewById(R.id.btnViewReport);
        }
    }
}
