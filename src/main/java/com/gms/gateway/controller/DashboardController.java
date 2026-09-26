package com.gms.gateway.controller;

import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.dto.AuditLogDto;
import com.gms.gateway.dto.DoctorDto;
import com.gms.gateway.dto.PatientDto;
import com.gms.gateway.dto.PrescriptionDto;
import com.gms.gateway.dto.ReportDto;
import com.gms.gateway.entity.User;
import com.gms.gateway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Powers /dashboard, /admin-dashboard, /doctor-dashboard, /hospital-dashboard -
 * all four currently render the same template (dashboard/main.html) but with
 * data scoped to the caller's role: SUPER_ADMIN/HOSPITAL_ADMIN/RECEPTIONIST
 * see system-wide totals, DOCTOR sees only their own patients/prescriptions/
 * reports (mirrors the doctor-scoped pattern already used for /patients).
 */
@Controller
public class DashboardController {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private UserRepository userRepository;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    private boolean hasRole(String roleName) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String target = "ROLE_" + roleName;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (target.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        return renderDashboard(model, "Dashboard");
    }

    @GetMapping("/admin-dashboard")
    public String adminDashboard(Model model) {
        return renderDashboard(model, "Admin Dashboard");
    }

    @GetMapping("/doctor-dashboard")
    public String doctorDashboard(Model model) {
        return renderDashboard(model, "Doctor Dashboard");
    }

    @GetMapping("/hospital-dashboard")
    public String hospitalDashboard(Model model) {
        return renderDashboard(model, "Hospital Dashboard");
    }

    private String renderDashboard(Model model, String pageTitle) {
        model.addAttribute("title", pageTitle);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("username", currentUsername());

        if (hasRole("DOCTOR")) {
            populateDoctorStats(model);
        } else {
            populateGlobalStats(model);
        }

        model.addAttribute("view", "dashboard/main");
        return "layout";
    }

    // SUPER_ADMIN / HOSPITAL_ADMIN / RECEPTIONIST - system-wide totals.
    // RECEPTIONIST isn't linked to a single hospital anywhere in this
    // codebase yet (only DOCTOR has a hospital-scoping field), so it gets
    // the same global view as admin rather than a half-built scoped one.
    private void populateGlobalStats(Model model) {
        List<PatientDto> patients = backendServiceClient.getAllPatients();
        List<DoctorDto> doctors = backendServiceClient.getAllDoctors();
        List<ReportDto> reports = backendServiceClient.getAllReports();
        List<PrescriptionDto> prescriptions = backendServiceClient.getAllPrescriptions();
        List<AuditLogDto> auditLogs = backendServiceClient.getAllAuditLogs();

        LocalDate today = LocalDate.now();

        model.addAttribute("totalPatients", patients.size());
        model.addAttribute("todaysPatients", countCreatedOn(patients.stream().map(PatientDto::getCreatedAt).toList(), today));
        model.addAttribute("totalDoctors", doctors.size());
        model.addAttribute("reportsUploaded", reports.size());

        addMonthlyChart(model, "patientGrowth", patients.stream().map(PatientDto::getCreatedAt).toList());
        addMonthlyChart(model, "monthlyPrescriptions", prescriptions.stream().map(PrescriptionDto::getCreatedAt).toList());

        model.addAttribute("recentActivities", recentActivities(auditLogs, 10));
    }

    // DOCTOR - only their own patient panel, their own prescriptions, and
    // reports they filed; "Total Doctors" becomes colleague count at their
    // own hospital instead of a system-wide number that wouldn't mean much
    // to them; "Recent Activities" is filtered to actions they performed.
    private void populateDoctorStats(Model model) {
        Long doctorId = userRepository.findByUsername(currentUsername())
                .map(User::getDoctorId)
                .orElse(null);

        if (doctorId == null) {
            model.addAttribute("totalPatients", 0);
            model.addAttribute("todaysPatients", 0);
            model.addAttribute("totalDoctors", 0);
            model.addAttribute("reportsUploaded", 0);
            addMonthlyChart(model, "patientGrowth", List.of());
            addMonthlyChart(model, "monthlyPrescriptions", List.of());
            model.addAttribute("recentActivities", List.of());
            return;
        }

        List<PatientDto> myPatients = backendServiceClient.getPatientsByDoctorId(doctorId);
        List<PrescriptionDto> myPrescriptions = backendServiceClient.getPrescriptionsByDoctor(doctorId);
        List<ReportDto> myReports = backendServiceClient.getAllReports().stream()
                .filter(r -> doctorId.equals(r.getDoctorId()))
                .toList();

        LocalDate today = LocalDate.now();
        Set<Long> todaysPatientIds = new HashSet<>();
        myPrescriptions.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().toLocalDate().equals(today))
                .forEach(p -> todaysPatientIds.add(p.getPatientId()));
        myReports.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().toLocalDate().equals(today))
                .forEach(r -> todaysPatientIds.add(r.getPatientId()));

        int colleagueCount = backendServiceClient.getDoctorById(doctorId)
                .map(d -> backendServiceClient.getDoctorsByHospital(d.getHospitalId()).size())
                .orElse(0);

        model.addAttribute("totalPatients", myPatients.size());
        model.addAttribute("todaysPatients", todaysPatientIds.size());
        model.addAttribute("totalDoctors", colleagueCount);
        model.addAttribute("reportsUploaded", myReports.size());

        addMonthlyChart(model, "patientGrowth", myPatients.stream().map(PatientDto::getCreatedAt).toList());
        addMonthlyChart(model, "monthlyPrescriptions", myPrescriptions.stream().map(PrescriptionDto::getCreatedAt).toList());

        List<AuditLogDto> myActivity = backendServiceClient.getAllAuditLogs().stream()
                .filter(a -> currentUsername().equalsIgnoreCase(a.getUsername()))
                .toList();
        model.addAttribute("recentActivities", recentActivities(myActivity, 10));
    }

    private long countCreatedOn(List<LocalDateTime> dates, LocalDate day) {
        return dates.stream().filter(d -> d != null && d.toLocalDate().equals(day)).count();
    }

    /** Last 6 calendar months (oldest to newest), counting how many timestamps fall in each. */
    private void addMonthlyChart(Model model, String attributePrefix, List<LocalDateTime> timestamps) {
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        List<String> labels = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = firstOfThisMonth.minusMonths(i);
            labels.add(month.format(MONTH_LABEL));
            long count = timestamps.stream()
                    .filter(d -> d != null && d.getYear() == month.getYear() && d.getMonthValue() == month.getMonthValue())
                    .count();
            counts.add(count);
        }
        model.addAttribute(attributePrefix + "Labels", labels);
        model.addAttribute(attributePrefix + "Data", counts);
    }

    private List<AuditLogDto> recentActivities(List<AuditLogDto> auditLogs, int limit) {
        return auditLogs.stream()
                .filter(a -> a.getCreatedAt() != null)
                .sorted(Comparator.comparing(AuditLogDto::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }
}
