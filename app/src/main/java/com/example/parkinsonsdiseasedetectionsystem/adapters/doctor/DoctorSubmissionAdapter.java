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
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Submission;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DoctorSubmissionAdapter extends RecyclerView.Adapter<DoctorSubmissionAdapter.SubmissionViewHolder> {

    private final List<Submission> submissionList = new ArrayList<>();
    private final Context context;
    private final LocalRepository localRepository;

    public DoctorSubmissionAdapter(Context context) {
        this.context = context;
        this.localRepository = LocalRepository.getInstance(context);
    }

    public void updateData(List<Submission> newSubmissions) {
        submissionList.clear();
        if (newSubmissions != null) {
            submissionList.addAll(newSubmissions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubmissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_report, parent, false);
        return new SubmissionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubmissionViewHolder holder, int position) {
        Submission submission = submissionList.get(position);
        
        // Get patient name
        User patient = localRepository.getUserByIdSync(submission.getUserId());
        String patientName = patient != null ? patient.getName() : "Unknown Patient";
        
        holder.tvPatientName.setText(patientName);
        holder.tvRecordingType.setText("Submission");
        holder.tvStatus.setText("Pending Review");
        holder.tvResult.setText("Awaiting Doctor");
        
        String formattedDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(submission.getCreatedAt()));
        holder.tvDate.setText(formattedDate);
        
        holder.btnViewReport.setVisibility(View.VISIBLE);
        holder.btnViewReport.setText("Review");
        holder.btnViewReport.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReportDetailActivity.class);
            intent.putExtra("SUBMISSION_ID", submission.getSubmissionId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return submissionList.size();
    }

    public static class SubmissionViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientName, tvRecordingType, tvStatus, tvResult, tvDate;
        Button btnViewReport;

        public SubmissionViewHolder(@NonNull View itemView) {
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




