# Global Medical System - Gateway Service (Frontend Portal)

## Overview

The Gateway Service is the complete **Frontend Portal** for the Global Medical System. It provides a comprehensive web-based interface for managing healthcare operations across multiple hospitals with features for authentication, patient management, prescriptions, biometric identification, and more.

## Features

### 1. Authentication & Security
- **Login Page**: Secure user login with credentials validation
- **Forgot Password**: Email-based password recovery with OTP verification
- **OTP Verification**: One-time password verification for security
- **Reset Password**: Secure password reset functionality
- **Session Management**: JWT token-based authentication and session handling
- **Role-Based Access Control (RBAC)**: Different access levels for different user roles

### 2. Dashboard Module
- **Main Dashboard**: Overview with statistics cards and charts
- **Admin Dashboard**: Additional admin-specific metrics
- **Doctor Dashboard**: Doctor-focused information and appointments
- **Hospital Dashboard**: Hospital-wide statistics and metrics

Key Widgets:
- Total Patients Counter
- Today's Patients Counter
- Total Doctors Counter
- Reports Uploaded Counter
- Patient Growth Chart
- Monthly Prescriptions Chart
- Recent Activities Timeline

### 3. Patient Management
- **Patient List**: Search and view all patients
- **Register Patient**: Add new patient with personal and contact information
- **Edit Patient**: Update patient information
- **Patient Details**: Comprehensive patient profile with tabs for prescriptions, reports, biometric data, and audit history
- **Medical Timeline**: Chronological view of all patient medical events

### 4. Biometric Module
- **Biometric Registration**: Enroll patient fingerprints for identification
- **Biometric Verification**: Verify patient identity using biometrics
- **Emergency Identification**: Critical feature for quick patient identification in emergencies
- **Biometric History**: View historical biometric records

### 5. Prescription Module
- **Prescription List**: View all prescriptions with filters
- **Create Prescription**: Generate new prescriptions with dynamic medicine grid
- **Prescription Details**: View complete prescription information
- **Print Prescription**: Generate PDF for printing
- **Prescription History**: View historical prescriptions

### 6. Medical Reports Module
- **Report Upload**: Upload medical reports (PDF, images, documents)
- **Report List**: Browse all uploaded reports
- **Report Viewer**: View reports with PDF preview
- **Report Download**: Download reports to local storage
- **Report History**: Track historical reports

### 7. Doctor Management
- **Doctor List**: View all registered doctors
- **Register Doctor**: Add new doctor with specialization and credentials
- **Edit Doctor**: Update doctor information
- **Doctor Profile**: Detailed doctor information and statistics
- **Performance Analytics**: View doctor performance metrics

### 8. Hospital Management
- **Hospital List**: View all hospitals
- **Register Hospital**: Add new hospital
- **Hospital Details**: Complete hospital information
- **Branch Management**: Manage hospital branches
- **Department Management**: Manage hospital departments

### 9. User Management
- **User List**: View all system users
- **Create User**: Add new users with role assignment
- **Edit User**: Update user information
- **Assign Roles**: Manage user role assignments
- **User Activity**: Track user activities

### 10. Audit & Monitoring
- **Audit Logs**: Complete system activity audit trail
- **Login History**: Track user login events
- **System Activity**: Monitor system-wide activities

### 11. Profile & Settings
- **My Profile**: View and edit personal profile
- **Change Password**: Update account password securely
- **Notification Settings**: Configure notification preferences

### 12. Error Pages
- **403 Forbidden**: Access denied error page
- **404 Not Found**: Page not found error page
- **500 Server Error**: Internal server error page

## Technical Stack

### Backend Framework
- **Spring Boot 3.3.0**: REST API framework
- **Spring Security**: Authentication and authorization
- **Spring MVC**: Web application framework
- **Thymeleaf**: Server-side template engine
- **Spring Data JPA**: Data persistence layer

### Frontend Technologies
- **Thymeleaf**: Server-side HTML templating
- **Vue.js 3**: Dynamic UI components and interactivity
- **Bootstrap 5**: Responsive UI framework
- **Chart.js 3**: Data visualization and charts
- **Font Awesome 6.4**: Icon library

### Database
- **PostgreSQL**: Relational database
- **Spring Data JPA**: ORM framework

### Security
- **JWT (JSON Web Tokens)**: Token-based authentication
- **Spring Security**: Role-based access control

## Project Structure

```
gateway-service/
├── src/main/java/com/gms/gateway/
│   ├── controller/
│   │   ├── AuthViewController.java
│   │   ├── DashboardViewController.java
│   │   ├── PatientViewController.java
│   │   ├── BiometricViewController.java
│   │   ├── PrescriptionViewController.java
│   │   ├── ReportViewController.java
│   │   ├── DoctorViewController.java
│   │   ├── HospitalViewController.java
│   │   ├── UserViewController.java
│   │   ├── AuditViewController.java
│   │   ├── ProfileViewController.java
│   │   ├── ErrorViewController.java
│   │   └── HomeController.java
│   └── GatewayServiceApplication.java
├── src/main/resources/
│   ├── templates/
│   │   ├── layout.html (Base layout template)
│   │   ├── auth/
│   │   │   ├── login.html
│   │   │   ├── forgot-password.html
│   │   │   ├── verify-otp.html
│   │   │   ├── reset-password.html
│   │   │   └── session-expired.html
│   │   ├── dashboard/
│   │   │   ├── main.html
│   │   │   ├── admin.html
│   │   │   ├── doctor.html
│   │   │   └── hospital.html
│   │   ├── patient/
│   │   │   ├── list.html
│   │   │   ├── create.html
│   │   │   ├── edit.html
│   │   │   ├── view.html
│   │   │   └── history.html
│   │   ├── biometric/
│   │   │   ├── register.html
│   │   │   ├── verify.html
│   │   │   ├── emergency-identification.html
│   │   │   └── history.html
│   │   ├── prescription/
│   │   │   ├── list.html
│   │   │   ├── create.html
│   │   │   ├── view.html
│   │   │   ├── print.html
│   │   │   └── history.html
│   │   ├── report/
│   │   │   ├── list.html
│   │   │   ├── upload.html
│   │   │   ├── view.html
│   │   │   └── history.html
│   │   ├── doctor/
│   │   │   ├── list.html
│   │   │   ├── create.html
│   │   │   ├── edit.html
│   │   │   ├── view.html
│   │   │   └── performance.html
│   │   ├── hospital/
│   │   │   ├── list.html
│   │   │   ├── create.html
│   │   │   ├── edit.html
│   │   │   ├── view.html
│   │   │   ├── branches.html
│   │   │   └── departments.html
│   │   ├── user/
│   │   │   ├── list.html
│   │   │   ├── create.html
│   │   │   ├── edit.html
│   │   │   ├── view.html
│   │   │   ├── roles.html
│   │   │   └── activity.html
│   │   ├── audit/
│   │   │   ├── logs.html
│   │   │   ├── login-history.html
│   │   │   └── system.html
│   │   ├── profile/
│   │   │   ├── view.html
│   │   │   ├── change-password.html
│   │   │   └── settings.html
│   │   └── error/
│   │       ├── 403.html
│   │       ├── 404.html
│   │       └── 500.html
│   ├── static/
│   │   ├── css/
│   │   │   └── custom.css (Bootstrap customization and GMS styles)
│   │   ├── js/
│   │   │   ├── api-client.js (HTTP client for API calls)
│   │   │   └── utils.js (Utility functions)
│   │   └── images/
│   └── application.yml (Configuration)
└── pom.xml (Dependencies)
```

## Directory Structure

```
Frontend Portal Structure:
├── Sidebar Navigation
│   ├── Dashboard
│   ├── Patients
│   ├── Prescriptions
│   ├── Reports
│   ├── Biometric
│   ├── Doctors
│   ├── Hospitals
│   ├── Users
│   ├── Audit Logs
│   ├── Profile
│   └── Logout
├── Main Content Area
│   ├── Page Title
│   ├── User Info
│   └── Dynamic Content
└── Support
    ├── Charts
    ├── Tables
    ├── Modals
    └── Forms
```

## Installation & Setup

### Prerequisites
- Java 21 or later
- PostgreSQL 12 or later
- Maven 3.6+

### Steps

1. **Database Setup**
   ```sql
   CREATE DATABASE gms_gateway;
   CREATE DATABASE gms_backend;
   CREATE DATABASE gms_biometric;
   ```

2. **Clone & Navigate**
   ```bash
   cd gateway-service
   ```

3. **Update Configuration**
   Edit `src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/gms_gateway
       username: your_postgres_user
       password: your_postgres_password
   ```

4. **Build Project**
   ```bash
   mvn clean install
   ```

5. **Run Application**
   ```bash
   mvn spring-boot:run
   ```

6. **Access Portal**
   ```
   http://localhost:8080
   ```

## Default Credentials

| Username | Password | Role |
|----------|----------|------|
| admin | password | Super Admin |
| doctor1 | password | Doctor |
| hospital_admin | password | Hospital Admin |

## UI Color Scheme

- **Primary Color**: #0D6EFD (Medical Blue)
- **Secondary Color**: #198754 (Healthcare Green)
- **Danger**: #DC3545 (Alert Red)
- **Warning**: #FFC107 (Warning Yellow)
- **Background**: #F8F9FA (Light Gray)

## API Integration

The Gateway Service communicates with other microservices:

### Backend Service (Port 8081)
- `/api/patient` - Patient management
- `/api/doctor` - Doctor management
- `/api/prescription` - Prescription management
- `/api/report` - Medical reports
- `/api/hospital` - Hospital management

### Biometric Service (Port 8082)
- `/api/biometric/register` - Register biometrics
- `/api/biometric/verify` - Verify biometrics
- `/api/biometric/identify` - Identify patients

### File System Service (Port 8083)
- `/api/files/upload` - Upload files
- `/api/files/download` - Download files

## Security Features

- **JWT Authentication**: Secure token-based authentication
- **Role-Based Access Control**: Different views for different roles
- **Session Management**: Automatic session timeout
- **Password Encryption**: Secure password storage and reset
- **CORS Protection**: Cross-Origin Resource Sharing configuration
- **SQL Injection Prevention**: Parameterized queries

## Responsive Design

The portal is fully responsive and works on:
- Desktop browsers (1920x1080 and above)
- Tablets (768px and above)
- Mobile devices (320px and above)

## Support & Documentation

For more information, see:
- Backend Service Documentation
- Biometric Service Documentation
- File System Service Documentation
- Global Medical System Architecture Guide

## License

Global Medical System - Enterprise Healthcare Management Platform
© 2026 All Rights Reserved
