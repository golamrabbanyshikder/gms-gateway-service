package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.FileServiceClient;
import com.gms.gateway.dto.ReportDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Controller
@RequestMapping("/reports")
public class ReportViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private FileServiceClient fileServiceClient;

    @Autowired
    private AuditClient auditClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String reportList(Model model) {
        model.addAttribute("title", "Medical Reports");
        model.addAttribute("pageTitle", "Medical Report Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("reports", backendServiceClient.getAllReports());
        model.addAttribute("view", "report/list");
        return "layout";
    }

    @GetMapping("/upload")
    public String uploadReport(@RequestParam(required = false) Long patientId, Model model) {
        model.addAttribute("title", "Upload Report");
        model.addAttribute("pageTitle", "Upload Medical Report");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("view", "report/upload");
        return "layout";
    }

    @PostMapping("/upload")
    public String submitUploadReport(@RequestParam Long patientId,
                                      @RequestParam Long doctorId,
                                      @RequestParam String reportType,
                                      @RequestParam("reportFile") MultipartFile reportFile,
                                      HttpServletRequest request,
                                      Model model) throws IOException {
        model.addAttribute("title", "Upload Report");
        model.addAttribute("pageTitle", "Upload Medical Report");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("preselectedPatientId", patientId);

        var uploaded = fileServiceClient.upload(reportFile, null, "Report", patientId);
        if (uploaded.isEmpty()) {
            model.addAttribute("error", "Failed to upload file. Please try again.");
            model.addAttribute("view", "report/upload");
            return "layout";
        }

        ReportDto report = new ReportDto();
        report.setPatientId(patientId);
        report.setDoctorId(doctorId);
        report.setReportType(reportType);
        report.setFileReference(String.valueOf(uploaded.get().getId()));
        report.setFileName(reportFile.getOriginalFilename());

        var created = backendServiceClient.createReport(report);
        if (created.isEmpty()) {
            model.addAttribute("error", "File uploaded but failed to save report record.");
            model.addAttribute("view", "report/upload");
            return "layout";
        }

        auditClient.log(null, currentUsername(), "REPORT_CREATED", "Report", created.get().getReportId(),
                "Uploaded report for patient " + patientId, request.getRemoteAddr());
        return "redirect:/patients/history/" + patientId;
    }

    @GetMapping("/view/{id}")
    public String viewReport(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Report Details");
        model.addAttribute("pageTitle", "Report Details");
        model.addAttribute("username", currentUsername());
        model.addAttribute("reportId", id);
        backendServiceClient.getReportById(id).ifPresent(r -> model.addAttribute("report", r));
        model.addAttribute("view", "report/view");
        return "layout";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id, HttpServletRequest request) {
        var downloadOpt = fileServiceClient.download(id);
        if (downloadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var download = downloadOpt.get();
        auditClient.log(null, currentUsername(), "REPORT_DOWNLOADED", "File", id,
                "Downloaded file " + download.getFileName(), request.getRemoteAddr());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .body(download.getData());
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<byte[]> previewReport(@PathVariable Long id) {
        var reportOpt = backendServiceClient.getReportById(id);
        if (reportOpt.isEmpty() || reportOpt.get().getFileReference() == null) {
            return ResponseEntity.notFound().build();
        }

        Long fileId;
        try {
            fileId = Long.valueOf(reportOpt.get().getFileReference());
        } catch (NumberFormatException e) {
            return ResponseEntity.notFound().build();
        }

        var downloadOpt = fileServiceClient.download(fileId);
        if (downloadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var download = downloadOpt.get();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + download.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .body(download.getData());
    }

    @GetMapping("/history")
    public String reportHistory(Model model) {
        model.addAttribute("title", "Report History");
        model.addAttribute("pageTitle", "Report History");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "report/history");
        return "layout";
    }
}
