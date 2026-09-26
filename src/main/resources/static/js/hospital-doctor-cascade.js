// Wires Hospital -> Doctor Category (specialization) -> Doctor selects so
// staff pick a doctor by browsing, instead of having to know IDs. Doctors
// for the chosen hospital are fetched once via /doctors/by-hospital/{id};
// the category step just filters that list client-side, no extra request.
(function () {
    window.initHospitalDoctorCascade = function (config) {
        var hospitalSelect = document.getElementById(config.hospitalSelectId);
        var specializationSelect = document.getElementById(config.specializationSelectId);
        var doctorSelect = document.getElementById(config.doctorSelectId);

        var doctorsAtHospital = [];

        function resetSelect(select, placeholderText) {
            select.innerHTML = '';
            var option = document.createElement('option');
            option.value = '';
            option.textContent = placeholderText;
            select.appendChild(option);
            select.disabled = true;
        }

        hospitalSelect.addEventListener('change', function () {
            resetSelect(specializationSelect, 'Loading categories...');
            resetSelect(doctorSelect, 'Select Category First');
            doctorsAtHospital = [];

            var hospitalId = hospitalSelect.value;
            if (!hospitalId) {
                resetSelect(specializationSelect, 'Select Hospital First');
                return;
            }

            fetch('/doctors/by-hospital/' + hospitalId)
                .then(function (response) { return response.json(); })
                .then(function (doctors) {
                    doctorsAtHospital = doctors || [];
                    var specializations = Array.from(new Set(doctorsAtHospital.map(function (d) { return d.specialization; })))
                        .filter(Boolean)
                        .sort();

                    resetSelect(specializationSelect, 'Select Category');
                    specializations.forEach(function (spec) {
                        var option = document.createElement('option');
                        option.value = spec;
                        option.textContent = spec;
                        specializationSelect.appendChild(option);
                    });
                    specializationSelect.disabled = specializations.length === 0;
                    if (specializations.length === 0) {
                        resetSelect(specializationSelect, 'No doctors at this hospital');
                    }
                })
                .catch(function () {
                    resetSelect(specializationSelect, 'Could not load categories');
                });
        });

        specializationSelect.addEventListener('change', function () {
            resetSelect(doctorSelect, 'Select Doctor');

            var specialization = specializationSelect.value;
            if (!specialization) {
                resetSelect(doctorSelect, 'Select Category First');
                return;
            }

            var matches = doctorsAtHospital.filter(function (d) { return d.specialization === specialization; });
            matches.forEach(function (doctor) {
                var option = document.createElement('option');
                option.value = doctor.doctorId;
                option.textContent = doctor.name + (doctor.licenseNumber ? ' (License: ' + doctor.licenseNumber + ')' : '');
                doctorSelect.appendChild(option);
            });
            doctorSelect.disabled = matches.length === 0;
        });
    };
})();
