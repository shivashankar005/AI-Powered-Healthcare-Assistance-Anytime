# AI-Powered Healthcare Chat Assistant

A full-stack healthcare platform with AI chat, voice interaction, bilingual (English + Telugu) responses, nearby doctor/hospital search, OCR medical report analysis, and role-based dashboards for Patients, Doctors, and Admins.

---

## Table of Contents
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Setup Instructions](#setup-instructions)
- [Running the Application](#running-the-application)
- [Backend API](#backend-api)
- [Sample Accounts](#sample-accounts)
- [Project Structure](#project-structure)
- [License](#license)

---

## Features

### Patient
- **AI Chat** — Multi-session chat with Ollama (phi3:mini); bilingual responses in English + Telugu
- **Voice Input / TTS** — Mic button for speech-to-text input; speaker button for text-to-speech on AI messages
- **Find Nearby Doctors & Hospitals** — Location-based search via OpenStreetMap Overpass API; falls back to Hyderabad when GPS is denied
- **Medical Report Upload** — Upload PDF/PNG/JPG; Tesseract OCR extracts text; AI generates a plain-language explanation
- **Health Profile** — Manage personal health data
- **Appointments** — Book and cancel appointments with doctors

### Doctor
- Patient list with health profiles and uploaded reports
- SOAP note generation via AI
- Appointment management

### Admin
- User management (create doctors, toggle status, delete users)
- Dashboard statistics and system overview

---

## Architecture

```
[React + Vite  :5175]  <-->  [Spring Boot REST API  :8080]  <-->  [MySQL  healthcare_db]
                                          |
                          [Ollama phi3:mini  :11434]   (local AI)
                          [Tesseract OCR v5.5]         (local)
                          [OpenStreetMap Overpass]     (hospital search)
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, Vite, Tailwind CSS, Axios, Recharts, React Router v6 |
| Backend | Java 17, Spring Boot 3.2, Spring Security, JPA/Hibernate, JWT |
| AI | Ollama (phi3:mini) local; OpenAI GPT-4 configurable fallback |
| OCR | Tesseract v5 via Tess4J, PDFBox (PDF page rendering) |
| Database | MySQL 8 |
| Location | Web Geolocation API + OpenStreetMap Overpass API |

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 17+ | |
| Maven | 3.9+ | |
| Node.js | 18+ | |
| MySQL | 8+ | Database name: `healthcare_db` |
| Tesseract OCR | 5+ | https://github.com/UB-Mannheim/tesseract/wiki — tessdata at `C:/Program Files/Tesseract-OCR/tessdata` |
| Ollama | latest | https://ollama.com — then run `ollama pull phi3:mini` |

---

## Setup Instructions

### 1. Clone the Repository
```sh
git clone <your-repo-url>
cd capstone
```

### 2. Configure the Database
```sh
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS healthcare_db;"
mysql -u root -p healthcare_db < database/schema.sql
```

### 3. Configure Backend
Edit `backend/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/healthcare_db
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

upload.directory=C:/path/to/capstone/uploads/
tesseract.data.path=C:/Program Files/Tesseract-OCR/tessdata

ollama.enabled=true
ollama.base.url=http://localhost:11434
ollama.model=phi3:mini
```

### 4. Install Frontend Dependencies
```sh
cd frontend
npm install
```

---

## Running the Application

### 1. Start Ollama
```sh
ollama serve
# First time only: ollama pull phi3:mini
```

### 2. Start Backend
```sh
cd backend
mvn package -DskipTests
java -jar target/healthcare-chat-assistant-1.0.0.jar
# API at http://localhost:8080
```

### 3. Start Frontend
```sh
cd frontend
npm run dev
# App at http://localhost:5175
```

---

## Backend API

All routes prefixed with `/api/`. JWT Bearer token required for protected routes.

| Group | Method | Endpoint | Roles |
|-------|--------|----------|-------|
| Auth | POST | `/api/auth/register` | Public |
| Auth | POST | `/api/auth/login` | Public |
| Chat | POST | `/api/chat/message` | PATIENT |
| Chat | GET | `/api/chat/sessions` | PATIENT |
| Chat | POST | `/api/chat/sessions` | PATIENT |
| Chat | POST | `/api/chat/location` | PATIENT, DOCTOR, ADMIN |
| Reports | POST | `/api/reports/upload` | PATIENT |
| Reports | GET | `/api/reports` | PATIENT |
| Reports | DELETE | `/api/reports/{id}` | PATIENT |
| Patient | GET | `/api/patient/profile` | PATIENT |
| Patient | GET | `/api/patient/appointments` | PATIENT |
| Doctor | GET | `/api/doctor/patients` | DOCTOR |
| Doctor | POST | `/api/doctor/soap-note` | DOCTOR |
| Admin | GET | `/api/admin/dashboard` | ADMIN |
| Admin | GET | `/api/admin/users` | ADMIN |
| Admin | POST | `/api/admin/users/create-doctor` | ADMIN |

---

## Sample Accounts

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `Admin@123` |
| Patient | Register via `/register` | — |
| Doctor | Created by Admin from dashboard | — |

---

## Project Structure

```
capstone/
├── backend/                          # Spring Boot application
│   └── src/main/java/com/healthcare/
│       ├── controller/               # REST controllers
│       ├── service/                  # ChatService, OCRService, OpenAIService, LocationChatService
│       ├── model/                    # JPA entities
│       ├── repository/               # Spring Data JPA
│       ├── security/                 # JWT filter + SecurityConfig
│       └── dto/                      # Request/Response DTOs
├── frontend/                         # React + Vite SPA
│   └── src/
│       ├── components/patient/       # PatientChat (AI + voice + location), PatientReports (OCR viewer)
│       ├── pages/                    # Login, Register, PatientDashboard, DoctorDashboard, AdminDashboard
│       ├── context/                  # AuthContext (JWT state)
│       └── services/api.js           # Axios API client
├── database/
│   └── schema.sql                    # Full DB schema + seed data (roles, admin, 10 sample doctors)
└── uploads/                          # Uploaded medical reports (auto-created at runtime)
```

---

## License
MIT License
