// Runs OCR on a prescription image entirely in the browser using Tesseract.js
// (all assets served locally from /vendor/tesseract - no network), then parses
// the recognized text into the labeled sections of the GMS prescription layout
// (Patient / Doctor / Hospital + MEDICINES / INSTRUCTIONS / FOLLOW-UP DATE
// blocks) and auto-fills every field of the upload form - including selecting
// the patient and resolving Hospital -> Category -> Doctor through the cascade.
// All matching is forgiving (case-insensitive, Dr. prefix stripped) because OCR
// on scans is imperfect; staff still review before saving.
(function () {
    var VENDOR = '/vendor/tesseract';
    var MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };

    // "15 Jul 2026" / "15 July 2026" -> "2026-07-15" (the value the <input type=date> expects).
    function parseDate(s) {
        if (!s) return null;
        var m = s.match(/(\d{1,2})\s*([A-Za-z]{3})[A-Za-z]*\s*(\d{4})/);
        if (!m) return null;
        var day = m[1].length < 2 ? '0' + m[1] : m[1];
        var mon = MONTHS[m[2].toLowerCase().slice(0, 3)];
        if (!mon) return null;
        mon = mon < 10 ? '0' + mon : '' + mon;
        return m[3] + '-' + mon + '-' + day;
    }

    function cleanName(s) {
        return (s || '').replace(/^dr\.?\s*/i, '').replace(/\s+/g, ' ').trim();
    }

    function sameName(a, b) {
        a = cleanName(a).toLowerCase();
        b = cleanName(b).toLowerCase();
        return a && b && (a === b || a.indexOf(b) !== -1 || b.indexOf(a) !== -1);
    }

    // Extracts structured fields from the GMS prescription layout. The labels
    // are distinctive uppercase section headers + "Field: value" lines, so we
    // locate header lines and slice the text between them.
    function parsePrescription(text) {
        var lines = text.split(/\r?\n/).map(function (l) { return l.trim(); });
        var nonEmpty = lines.filter(function (l) { return l.length; });
        var parsed = { patient: null, doctor: null, hospital: null, medicines: null, instructions: null, followUpDate: null };

        // Single-line "Label: value" fields (Patient / Doctor / Hospital). Also
        // tolerate the value being on the next line.
        for (var i = 0; i < nonEmpty.length; i++) {
            var line = nonEmpty[i];
            var m = line.match(/^(patient|doctor|hospital)\s*:?\s*(.*)$/i);
            if (m) {
                var key = m[1].toLowerCase();
                var val = m[2].trim();
                if (!val && i + 1 < nonEmpty.length) val = nonEmpty[i + 1]; // value on next line
                if (val && !parsed[key]) parsed[key] = val.replace(/\s*\(.*\)$/, '').trim();
            }
        }

        function headerIndex(re) {
            for (var i = 0; i < nonEmpty.length; i++) {
                if (re.test(nonEmpty[i])) return i;
            }
            return -1;
        }
        var medIdx = headerIndex(/^medicines?$/i);
        var insIdx = headerIndex(/^instructions?$/i);
        var fuIdx = headerIndex(/^follow[-\s]?up\s*(date)?$/i);

        // Skip the ℞ / "Rx" glyph line that sits above MEDICINES.
        function block(from, to) {
            if (from < 0) return null;
            var end = to < 0 ? nonEmpty.length : to;
            var slice = nonEmpty.slice(from + 1, end)
                .filter(function (l) { return !/^[rx℞]+$/i.test(l); });
            return slice.length ? slice.join('\n').trim() : null;
        }
        parsed.medicines = block(medIdx, insIdx);
        parsed.instructions = block(insIdx, fuIdx);
        if (fuIdx >= 0 && nonEmpty[fuIdx + 1]) {
            parsed.followUpDate = parseDate(nonEmpty[fuIdx + 1]);
        }
        return parsed;
    }

    function setSelectByText(select, name) {
        if (!select || !name) return false;
        var target = cleanName(name).toLowerCase();
        for (var i = 0; i < select.options.length; i++) {
            var opt = select.options[i];
            if (!opt.value) continue;
            if (cleanName(opt.text).toLowerCase().indexOf(target) !== -1) {
                select.value = opt.value;
                select.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            }
        }
        return false;
    }

    function waitFor(predicate, timeoutMs) {
        return new Promise(function (resolve, reject) {
            var start = Date.now();
            (function check() {
                try { if (predicate()) return resolve(true); } catch (e) { /* keep polling */ }
                if (Date.now() - start > (timeoutMs || 8000)) return reject(new Error('timed out waiting for dropdown'));
                setTimeout(check, 150);
            })();
        });
    }

    // The doctor select is gated by the cascade (Hospital -> Category -> Doctor),
    // so we resolve the doctor from the hospital's doctor list, pick their
    // specialization to unlock the doctor dropdown, then select them by id.
    function autoSelectHospitalDoctor(hospitalName, doctorName, els, status) {
        var hospitalId = null;
        for (var i = 0; i < els.hospital.options.length; i++) {
            if (sameName(els.hospital.options[i].text, hospitalName)) {
                hospitalId = els.hospital.options[i].value;
                break;
            }
        }
        if (!hospitalId) {
            status.textContent += ' Could not match hospital "' + hospitalName + '" in the list.';
            return Promise.resolve();
        }
        els.hospital.value = hospitalId;
        els.hospital.dispatchEvent(new Event('change', { bubbles: true }));

        return fetch('/doctors/by-hospital/' + hospitalId).then(function (r) { return r.json(); }).then(function (doctors) {
            var doctor = (doctors || []).find(function (d) { return sameName(d.name, doctorName); });
            if (!doctor) {
                status.textContent += ' Could not match doctor "' + doctorName + '" at ' + hospitalName + '.';
                return;
            }
            return waitFor(function () { return !els.specialization.disabled && els.specialization.options.length > 1; }).then(function () {
                var hasSpec = Array.prototype.some.call(els.specialization.options, function (o) { return o.value === doctor.specialization; });
                if (!hasSpec) {
                    status.textContent += ' Category "' + doctor.specialization + '" not found.';
                    return;
                }
                els.specialization.value = doctor.specialization;
                els.specialization.dispatchEvent(new Event('change', { bubbles: true }));
                return waitFor(function () { return !els.doctor.disabled; }).then(function () {
                    els.doctor.value = String(doctor.doctorId);
                    els.doctor.dispatchEvent(new Event('change', { bubbles: true }));
                });
            });
        });
    }

    window.initPrescriptionOcr = function (config) {
        var fileInput = document.getElementById(config.fileInputId);
        var extractBtn = document.getElementById(config.extractBtnId);
        var statusEl = document.getElementById(config.statusId);
        var rawText = document.getElementById(config.rawTextId);
        var rawTextWrap = document.getElementById(config.rawTextWrapId);
        var medicineList = document.getElementById(config.medicineListId);
        var instructions = document.getElementById(config.instructionsId);
        var followUpDate = document.getElementById(config.followUpDateId);
        var patientSelect = document.getElementById(config.patientSelectId);
        var hospitalSelect = document.getElementById(config.hospitalSelectId);
        var specializationSelect = document.getElementById(config.specializationSelectId);
        var doctorSelect = document.getElementById(config.doctorSelectId);

        var workerPromise = null;
        var running = false;

        function setStatus(text, cls) {
            statusEl.textContent = text;
            statusEl.className = 'small ms-3 ' + (cls || 'text-muted');
        }

        fileInput.addEventListener('change', function () {
            extractBtn.disabled = fileInput.files.length === 0;
            setStatus(fileInput.files.length ? 'Ready to extract' : '');
        });

        function getWorker() {
            if (!workerPromise) {
                setStatus('Loading OCR engine (first run loads language data)...', 'text-info');
                workerPromise = Tesseract.createWorker('eng', 1, {
                    workerPath: VENDOR + '/worker.min.js',
                    corePath: VENDOR,        // dir -> auto-picks simd vs non-simd LSTM core
                    langPath: VENDOR + '/lang'
                });
            }
            return workerPromise;
        }

        extractBtn.addEventListener('click', function () {
            if (running || fileInput.files.length === 0) return;
            running = true;
            extractBtn.disabled = true;
            medicineList.value = '';
            if (instructions) instructions.value = '';
            setStatus('Recognizing text...', 'text-info');

            getWorker().then(function (worker) {
                return worker.recognize(fileInput.files[0]);
            }).then(function (result) {
                var text = (result && result.data && result.data.text ? result.data.text : '').trim();
                if (rawText) rawText.value = text;
                if (rawTextWrap) rawTextWrap.style.display = text ? '' : 'none';
                if (!text) {
                    setStatus('No text recognized - try a clearer image or type manually.', 'text-warning');
                    return;
                }

                var parsed = parsePrescription(text);
                if (medicineList) medicineList.value = parsed.medicines || text;
                if (instructions && parsed.instructions) instructions.value = parsed.instructions;
                if (followUpDate && parsed.followUpDate) {
                    followUpDate.value = parsed.followUpDate;
                    followUpDate.dispatchEvent(new Event('change', { bubbles: true }));
                }
                if (patientSelect && parsed.patient) setSelectByText(patientSelect, parsed.patient);

                setStatus('Extraction complete - review all fields before saving.', 'text-success');

                var els = { hospital: hospitalSelect, specialization: specializationSelect, doctor: doctorSelect };
                if (parsed.hospital && parsed.doctor && hospitalSelect) {
                    autoSelectHospitalDoctor(parsed.hospital, parsed.doctor, els, statusEl).catch(function () { /* status already notes it */ });
                }
            }).catch(function (e) {
                setStatus('OCR failed: ' + (e && e.message ? e.message : e), 'text-danger');
            }).then(function () {
                running = false;
                extractBtn.disabled = fileInput.files.length === 0;
            });
        });
    };
})();
