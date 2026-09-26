// Wires a live camera feed + face-api.js to a capture button, and on capture
// computes a 128-dimension face descriptor (a numeric encoding of facial
// geometry from the landmark + recognition models, NOT a photo) and exposes
// it as a File attached to an existing <input type="file"> so it flows
// through the normal multipart form submit unchanged. biometric-service
// compares descriptors by Euclidean distance - this is what makes matching
// actually compare faces instead of raw image bytes (two JPEGs of the same
// person from different moments never share enough raw bytes to "match").
(function () {
    // Served locally from gateway-service's own static resources - no
    // internet dependency at runtime. See static/models/ and static/vendor/.
    var MODEL_URL = '/models';
    var modelsLoaded = false;
    var modelLoadPromise = null;

    function loadModels() {
        if (!modelLoadPromise) {
            modelLoadPromise = Promise.all([
                faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
                faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODEL_URL),
                faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL)
            ]).then(function () { modelsLoaded = true; });
        }
        return modelLoadPromise;
    }

    window.initFaceCapture = function (config) {
        var video = document.getElementById(config.videoId);
        var canvas = document.getElementById(config.canvasId);
        var startBtn = document.getElementById(config.startBtnId);
        var captureBtn = document.getElementById(config.captureBtnId);
        var statusEl = document.getElementById(config.statusId);
        var fileInput = document.getElementById(config.fileInputId);
        var previewImg = config.previewId ? document.getElementById(config.previewId) : null;

        var stream = null;
        var detectInterval = null;
        var faceCurrentlyDetected = false;

        function setStatus(text, cls) {
            statusEl.textContent = text;
            statusEl.className = 'small mt-2 ' + (cls || 'text-muted');
        }

        function detectorOptions() {
            return new faceapi.TinyFaceDetectorOptions();
        }

        startBtn.addEventListener('click', function () {
            setStatus('Starting camera...');
            navigator.mediaDevices.getUserMedia({ video: { width: 320, height: 240 } })
                .then(function (s) {
                    stream = s;
                    video.srcObject = stream;
                    video.style.display = 'block';
                    return video.play();
                })
                .then(function () {
                    startBtn.disabled = true;
                    setStatus('Loading face recognition models...');
                    return loadModels();
                })
                .then(function () {
                    setStatus('Position face in frame...');
                    detectInterval = setInterval(detectFace, 400);
                })
                .catch(function (e) {
                    setStatus('Could not access camera: ' + e.message, 'text-danger');
                });
        });

        function detectFace() {
            if (!modelsLoaded || video.readyState !== 4) {
                return;
            }
            faceapi.detectSingleFace(video, detectorOptions())
                .then(function (result) {
                    faceCurrentlyDetected = !!result;
                    if (faceCurrentlyDetected) {
                        setStatus('Face detected - ready to capture', 'text-success');
                        captureBtn.disabled = false;
                    } else {
                        setStatus('No face detected - position face in frame', 'text-warning');
                        captureBtn.disabled = true;
                    }
                })
                .catch(function () {
                    // transient detection hiccup - leave previous status as-is
                });
        }

        captureBtn.addEventListener('click', function () {
            if (!faceCurrentlyDetected) {
                return;
            }
            captureBtn.disabled = true;
            setStatus('Reading facial features...');

            faceapi.detectSingleFace(video, detectorOptions())
                .withFaceLandmarks(true)
                .withFaceDescriptor()
                .then(function (result) {
                    if (!result || !result.descriptor) {
                        setStatus('Could not read facial features - try again with better lighting', 'text-warning');
                        captureBtn.disabled = false;
                        return;
                    }

                    canvas.width = video.videoWidth;
                    canvas.height = video.videoHeight;
                    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
                    if (previewImg) {
                        previewImg.src = canvas.toDataURL('image/jpeg');
                        previewImg.style.display = 'block';
                    }

                    // result.descriptor is a Float32Array(128) - the actual
                    // facial-geometry encoding used for matching, not a photo.
                    var file = new File([result.descriptor.buffer], 'face-descriptor.bin', { type: 'application/octet-stream' });
                    var dataTransfer = new DataTransfer();
                    dataTransfer.items.add(file);
                    fileInput.files = dataTransfer.files;

                    setStatus('Face captured', 'text-success');

                    if (stream) {
                        stream.getTracks().forEach(function (t) { t.stop(); });
                    }
                    clearInterval(detectInterval);
                    video.style.display = 'none';
                    startBtn.textContent = 'Retake Photo';
                    startBtn.disabled = false;

                    if (typeof config.onCaptured === 'function') {
                        config.onCaptured();
                    }
                })
                .catch(function (e) {
                    setStatus('Could not read facial features: ' + e.message, 'text-danger');
                    captureBtn.disabled = false;
                });
        });
    };
})();
