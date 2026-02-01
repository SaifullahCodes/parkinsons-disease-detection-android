package com.example.parkinsonsdiseasedetectionsystem.adapters.doctor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.List;

public class DoctorPatientAdapter extends RecyclerView.Adapter<DoctorPatientAdapter.PatientViewHolder> {

    private List<User> patientList;

    public DoctorPatientAdapter(List<User> patientList) {
        this.patientList = patientList != null ? patientList : new java.util.ArrayList<>();
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient_card, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        if (patientList == null || position < 0 || position >= patientList.size()) {
            return;
        }
        User patient = patientList.get(position);
        if (patient == null) {
            return;
        }
        holder.tvName.setText(patient.getName() != null ? patient.getName() : "Unknown");
        holder.tvEmail.setText(patient.getEmail() != null ? patient.getEmail() : "No email");
        holder.tvPhone.setText("📞 " + (patient.getPhone() != null ? patient.getPhone() : "No phone"));
        holder.tvRole.setText("Role: " + (patient.getRole() != null ? patient.getRole() : "Unknown"));
    }

    @Override
    public int getItemCount() {
        return patientList != null ? patientList.size() : 0;
    }

    public void updateList(List<User> updatedList) {
        this.patientList = updatedList != null ? updatedList : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    public static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvPhone, tvRole;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvRole = itemView.findViewById(R.id.tvRole);
        }
    }
}
