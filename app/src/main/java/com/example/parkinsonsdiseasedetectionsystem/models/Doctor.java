package com.example.parkinsonsdiseasedetectionsystem.models;

public class Doctor {
    private String name;
    private String specialty;
    private String email;
    private String status;

    public Doctor(String name, String specialty, String email, String status) {
        this.name = name;
        this.specialty = specialty;
        this.email = email;
        this.status = status;
    }

    // Getters
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(String status) { this.status = status; }
}
