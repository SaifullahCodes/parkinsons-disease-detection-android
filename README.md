# 🏥 Parkinson’s Disease Detection System (Android + Hybrid AI)

[![Status](https://img.shields.io/badge/Status-Completed-success?style=for-the-badge)](https://github.com/SaifullahCodes)
[![Tech](https://img.shields.io/badge/Tech-Android%20%7C%20Gemini%201.5%20%7C%20MediaPipe-blue?style=for-the-badge)](https://github.com/SaifullahCodes)

> **A Native Android Application designed for early detection of Parkinson’s Disease using non-invasive multi-modal analysis (Gait + Voice).**

---

## 🎥 Watch The Demo
Click the link below to see the app in action:
### [▶️ Watch Full Video Demo on LinkedIn]https://www.linkedin.com/posts/saifullahnaseerdev_finalyearproject-androiddev-roomdatabase-activity-7425179953442308097-Z6CJ?utm_source=share&utm_medium=member_desktop&rcm=ACoAAGHBEjwBPASBFcFFcSGa7o5LZRBOj-kC90M

---

## 📸 App Interface
*(Visual showcase of the application flow)*

| Dashboard (Home) | AI Analysis Result | Risk Report (Result) |
|:---:|:---:|:---:|
| <img src="dashboard.jpeg" width="200"> | <img src="Ai_Result.jpeg" width="200"> | <img src="Report_Result.jpeg" width="200"> |

---

## 🏗️ System Architecture
The system follows a hybrid approach where the Android app communicates with a Python Flask backend for ML inference.

![System Architecture](architecture.png)

### 🔗 Backend & AI Logic
The core AI processing logic is decoupled from the mobile app.
👉 **[View Python Backend Repository (Flask + Gemini + ML)](https://github.com/SaifullahCodes/parkinson-api)**

---

## 📄 Project Documentation
For deep technical details, research methodology, and diagrams, refer to the case study:
[👉 **Download Project Showcase PDF**](https://github.com/SaifullahCodes/parkinsons-disease-detection-android/raw/master/Parkinson_Detection_System_Showcase.pdf)

---

## 🛠️ Tech Stack & Constraints
- **Frontend:** Native Android (Java, XML), MVVM Architecture, Room Database.
- **AI Models:** Google Gemini 1.5 Pro (Vision), MediaPipe Pose, Custom MLP (Audio).
- **Backend:** Flask API hosted on **Render** (Free Tier).

> **⚠️ Performance Note:** Since the backend is hosted on Render's Free Tier, the initial request may take **30-60 seconds** to wake up the server (Cold Start). Subsequent requests are instant.

---

## ⚙️ How to Run (Locally)
1. **Clone the Repo:** `git clone https://github.com/SaifullahCodes/parkinsons-disease-detection-android.git`
2. **Open in Android Studio:** File > Open > Select Project Folder.
3. **Sync Gradle:** Allow dependencies to download.
4. **Setup Firebase:** Add your `google-services.json` in the `app/` folder.
5. **Run:** Connect device and click Play ▶️.

---

## ⚖️ COPYRIGHT & LICENSE

**© 2026 Saifullah Naseer. All Rights Reserved.**

This project is the intellectual property of the author.
1. **Viewing Only:** Recruiters and students may view this code for educational/evaluation purposes.
2. **No Commercial Use:** Usage for paid projects, startups, or freelance work is strictly prohibited.
3. **Academic Integrity:** Cloning this repository for submission as your own Final Year Project (FYP) is a violation of academic integrity.

**Contact:** saifullah.naseer.dev@gmail.com
