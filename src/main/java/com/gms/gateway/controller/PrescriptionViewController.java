package com.gms.gateway.controller;

import com.gms.gateway.client.AuditClient;
import com.gms.gateway.client.BackendServiceClient;
import com.gms.gateway.client.FileServiceClient;
import com.gms.gateway.dto.PrescriptionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/prescriptions")
public class PrescriptionViewController {

    @Autowired
    private BackendServiceClient backendServiceClient;

    @Autowired
    private AuditClient auditClient;

    @Autowired
    private FileServiceClient fileServiceClient;

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "User";
    }

    @GetMapping
    public String prescriptionList(Model model) {
        model.addAttribute("title", "Prescriptions");
        model.addAttribute("pageTitle", "Prescription Management");
        model.addAttribute("username", currentUsername());
        model.addAttribute("prescriptions", backendServiceClient.getAllPrescriptions());
        model.addAttribute("view", "prescription/list");
        return "layout";
    }

    @GetMapping("/create")
    public String createPrescription(@RequestParam(required = false) Long patientId, Model model) {
        model.addAttribute("title", "Create Prescription");
        model.addAttribute("pageTitle", "Create New Prescription");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("diseases", backendServiceClient.getAllDiseases());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("view", "prescription/create");
        return "layout";
    }

    @PostMapping("/create")
    public String submitCreatePrescription(@RequestParam Long patientId,
                                            @RequestParam Long doctorId,
                                            @RequestParam Long hospitalId,
                                            @RequestParam String medicineList,
                                            @RequestParam String instructions,
                                            @RequestParam String followUpDate,
                                            @RequestParam(required = false) java.util.List<String> diseases,
                                            HttpServletRequest request,
                                            Model model) {
        PrescriptionDto prescription = new PrescriptionDto();
        prescription.setPatientId(patientId);
        prescription.setDoctorId(doctorId);
        prescription.setHospitalId(hospitalId);
        prescription.setMedicineList(medicineList);
        prescription.setInstructions(instructions);
        prescription.setFollowUpDate(LocalDate.parse(followUpDate));
        prescription.setDiseases(diseases != null ? String.join(",", diseases) : null);

        var created = backendServiceClient.createPrescription(prescription);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "PRESCRIPTION_CREATED", "Prescription",
                    created.get().getPrescriptionId(), "Created prescription for patient " + patientId, request.getRemoteAddr());
            return "redirect:/patients/history/" + patientId;
        }
        model.addAttribute("title", "Create Prescription");
        model.addAttribute("pageTitle", "Create New Prescription");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("diseases", backendServiceClient.getAllDiseases());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("error", "Failed to create prescription. Please try again.");
        model.addAttribute("view", "prescription/create");
        return "layout";
    }

    // "Upload Prescription" - the OCR of the scanned/image prescription is done
    // in the browser (Tesseract.js, fully offline - see static/vendor/tesseract)
    // and fills the medicines/instructions fields the same create form uses.
    // On submit the original image is stored via file-system-service and its id
    // kept on the prescription (fileReference), so the scan stays previewable
    // alongside the extracted structured data.
    @GetMapping("/upload")
    public String uploadPrescription(@RequestParam(required = false) Long patientId, Model model) {
        model.addAttribute("title", "Upload Prescription");
        model.addAttribute("pageTitle", "Upload & Extract Prescription");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("diseases", backendServiceClient.getAllDiseases());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("view", "prescription/upload");
        return "layout";
    }

    @PostMapping("/upload")
    public String submitUploadPrescription(@RequestParam Long patientId,
                                            @RequestParam Long doctorId,
                                            @RequestParam Long hospitalId,
                                            @RequestParam String medicineList,
                                            @RequestParam String instructions,
                                            @RequestParam String followUpDate,
                                            @RequestParam(required = false) java.util.List<String> diseases,
                                            @RequestParam("prescriptionFile") MultipartFile prescriptionFile,
                                            HttpServletRequest request,
                                            Model model) throws IOException {
        PrescriptionDto prescription = new PrescriptionDto();
        prescription.setPatientId(patientId);
        prescription.setDoctorId(doctorId);
        prescription.setHospitalId(hospitalId);
        prescription.setMedicineList(medicineList);
        prescription.setInstructions(instructions);
        prescription.setFollowUpDate(LocalDate.parse(followUpDate));
        prescription.setDiseases(diseases != null ? String.join(",", diseases) : null);

        // The image is the source document - store it even though the text was
        // already extracted client-side, so staff can re-read the original if
        // the OCR was imperfect. Failure to store the file doesn't block the
        // prescription record (the structured data is what matters); we just
        // lose the attached scan in that case.
        if (prescriptionFile != null && !prescriptionFile.isEmpty()) {
            var uploaded = fileServiceClient.upload(prescriptionFile, null, "Prescription", patientId);
            uploaded.ifPresent(meta -> prescription.setFileReference(String.valueOf(meta.getId())));
        }

        var created = backendServiceClient.createPrescription(prescription);
        if (created.isPresent()) {
            auditClient.log(null, currentUsername(), "PRESCRIPTION_UPLOADED", "Prescription",
                    created.get().getPrescriptionId(),
                    "Uploaded & OCR-extracted prescription for patient " + patientId, request.getRemoteAddr());
            return "redirect:/patients/history/" + patientId;
        }
        model.addAttribute("title", "Upload Prescription");
        model.addAttribute("pageTitle", "Upload & Extract Prescription");
        model.addAttribute("username", currentUsername());
        model.addAttribute("patients", backendServiceClient.getAllPatients());
        model.addAttribute("hospitals", backendServiceClient.getAllHospitals());
        model.addAttribute("diseases", backendServiceClient.getAllDiseases());
        model.addAttribute("preselectedPatientId", patientId);
        model.addAttribute("error", "Failed to save prescription. Please try again.");
        model.addAttribute("view", "prescription/upload");
        return "layout";
    }

    @GetMapping("/view/{id}")
    public String viewPrescription(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Prescription Details");
        model.addAttribute("pageTitle", "Prescription Details");
        model.addAttribute("username", currentUsername());
        model.addAttribute("prescriptionId", id);
        backendServiceClient.getPrescriptionById(id).ifPresent(p -> model.addAttribute("prescription", p));
        model.addAttribute("view", "prescription/view");
        return "layout";
    }

    @GetMapping("/print/{id}")
    public String printPrescription(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Print Prescription");
        model.addAttribute("pageTitle", "Print Prescription");
        model.addAttribute("prescriptionId", id);
        backendServiceClient.getPrescriptionById(id).ifPresent(p -> model.addAttribute("prescription", p));
        model.addAttribute("view", "prescription/print");
        return "layout";
    }

    @GetMapping("/preview/{id}")
    public String previewPrescription(@PathVariable Long id, Model model) {
        model.addAttribute("prescriptionId", id);
        backendServiceClient.getPrescriptionById(id).ifPresent(p -> {
            model.addAttribute("prescription", p);
            backendServiceClient.getDoctorById(p.getDoctorId()).ifPresent(d -> model.addAttribute("doctorName", d.getName()));
            backendServiceClient.getHospitalById(p.getHospitalId()).ifPresent(h -> model.addAttribute("hospitalName", h.getHospitalName()));
            backendServiceClient.getPatientById(p.getPatientId()).ifPresent(pt -> model.addAttribute("patientName", pt.getFullName()));
        });
        return "prescription/preview";
    }

    @GetMapping("/history")
    public String prescriptionHistory(Model model) {
        model.addAttribute("title", "Prescription History");
        model.addAttribute("pageTitle", "Prescription History");
        model.addAttribute("username", currentUsername());
        model.addAttribute("view", "prescription/history");
        return "layout";
    }
}
