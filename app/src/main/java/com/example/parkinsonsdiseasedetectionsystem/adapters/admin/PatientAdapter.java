package com.example.parkinsonsdiseasedetectionsystem.adapters.admin;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.List;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.ViewHolder> {

    private final List<User> patientList;
    private final PatientActionListener listener;

    public interface PatientActionListener {
        void onViewPatient(User patient);
        void onEditPatient(User patient);
        void onBlockPatient(User patient);
        void onDeletePatient(User patient);
    }

    public PatientAdapter(List<User> list, PatientActionListener listener) {
        this.patientList = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User patient = patientList.get(position);
        
        holder.tvPatientName.setText(patient.getName());
        holder.tvPatientEmail.setText(patient.getEmail());
        
        // Set status
        if (patient.isBlocked()) {
            holder.tvPatientStatus.setText("Status: Blocked");
            holder.tvPatientStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        } else {
            holder.tvPatientStatus.setText("Status: Active");
            holder.tvPatientStatus.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        }
        
        // Update block button text and icon based on status
        if (patient.isBlocked()) {
            holder.btnBlock.setImageResource(android.R.drawable.ic_menu_revert);
            if (holder.tvBlockLabel != null) {
                holder.tvBlockLabel.setText("Unblock");
            }
        } else {
            holder.btnBlock.setImageResource(R.drawable.ic_delete);
            if (holder.tvBlockLabel != null) {
                holder.tvBlockLabel.setText("Block");
            }
        }

        // Set up button listeners
        holder.btnView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewPatient(patient);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditPatient(patient);
            }
        });

        holder.btnBlock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBlockPatient(patient);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeletePatient(patient);
            }
        });
    }

    @Override
    public int getItemCount() {
        return patientList.size();
    }

    public void updateData(List<User> newList) {
        patientList.clear();
        if (newList != null) {
            patientList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientName, tvPatientEmail, tvPatientStatus, tvBlockLabel;
        ImageView ivPatientAvatar;
        ImageButton btnView, btnEdit, btnBlock, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPatientAvatar = itemView.findViewById(R.id.ivPatientAvatar);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientEmail = itemView.findViewById(R.id.tvPatientEmail);
            tvPatientStatus = itemView.findViewById(R.id.tvPatientStatus);
            btnView = itemView.findViewById(R.id.btnView);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnBlock = itemView.findViewById(R.id.btnBlock);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            // Find the TextView for block button label
            tvBlockLabel = itemView.findViewById(R.id.tvBlockLabel);
        }
    }
}
