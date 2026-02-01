package com.example.parkinsonsdiseasedetectionsystem.adapters.admin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.List;

public class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.ViewHolder> {

    private final Context context;
    private final List<User> doctorList;
    private final DoctorActionListener listener;

    public interface DoctorActionListener {
        void onViewDoctor(User doctor);
        void onApproveDoctor(User doctor);
        void onEditDoctor(User doctor);
        void onRemoveDoctor(User doctor);
    }

    public DoctorAdapter(Context context, List<User> doctorList, DoctorActionListener listener) {
        this.context = context;
        // Create a new list to avoid reference issues
        this.doctorList = doctorList != null ? new java.util.ArrayList<>(doctorList) : new java.util.ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_doctor_admin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User doctor = doctorList.get(position);

        // Bind doctor data
        holder.tvDoctorName.setText("Dr. " + doctor.getName());
        holder.tvDoctorSpecialty.setText("Neurologist"); // Default specialty
        holder.tvDoctorEmail.setText(doctor.getEmail());
        
        // Set status text only (no "Status:" prefix, no icons) - just show text
        if (doctor.isBlocked()) {
            holder.tvDoctorStatus.setText("Pending Approval");
            holder.tvDoctorStatus.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.tvDoctorStatus.setText("Approved");
            holder.tvDoctorStatus.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
        }
        // Remove any icons from status text
        holder.tvDoctorStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        // 🔹 CRITICAL: Always show approve and remove buttons
        // Approve icon: Red when not approved (blocked), Green/Blue when approved
        holder.btnApprove.setVisibility(View.VISIBLE);
        holder.btnRemove.setVisibility(View.VISIBLE);
        
        if (doctor.isBlocked()) {
            // Not approved - show red approve icon
            holder.btnApprove.setImageResource(android.R.drawable.checkbox_off_background);
            holder.btnApprove.setColorFilter(context.getResources().getColor(android.R.color.holo_red_dark));
            if (holder.tvApproveLabel != null) {
                holder.tvApproveLabel.setText("Approve");
            }
        } else {
            // Already approved - show blue/green approve icon with checkmark
            holder.btnApprove.setImageResource(R.drawable.ic_check_circle);
            // Use blue (colorPrimary - teal blue) for approved status
            holder.btnApprove.setColorFilter(context.getResources().getColor(R.color.colorPrimary));
            if (holder.tvApproveLabel != null) {
                holder.tvApproveLabel.setText("Approved");
            }
        }

        // Button Clicks
        holder.btnView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewDoctor(doctor);
            }
        });

        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApproveDoctor(doctor);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditDoctor(doctor);
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveDoctor(doctor);
            }
        });
    }

    @Override
    public int getItemCount() {
        return doctorList.size();
    }

    public void updateData(List<User> newList) {
        doctorList.clear();
        if (newList != null) {
            doctorList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDoctorAvatar;
        TextView tvDoctorName, tvDoctorSpecialty, tvDoctorEmail, tvDoctorStatus, tvApproveLabel;
        ImageButton btnView, btnApprove, btnEdit, btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDoctorAvatar = itemView.findViewById(R.id.ivDoctorAvatar);
            tvDoctorName = itemView.findViewById(R.id.tvDoctorName);
            tvDoctorSpecialty = itemView.findViewById(R.id.tvDoctorSpecialty);
            tvDoctorEmail = itemView.findViewById(R.id.tvDoctorEmail);
            tvDoctorStatus = itemView.findViewById(R.id.tvDoctorStatus);

            btnView = itemView.findViewById(R.id.btnView);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            tvApproveLabel = itemView.findViewById(R.id.tvApproveLabel);
        }
    }
}
