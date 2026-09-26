// Utility functions for the application

// Show notification toast
function showNotification(message, type = 'success', duration = 5000) {
    const alertClass = `alert-${type}`;
    const icon = type === 'success' ? 'fa-check-circle' : 
                 type === 'error' ? 'fa-exclamation-circle' : 
                 type === 'warning' ? 'fa-exclamation-triangle' : 'fa-info-circle';
    
    const alertHTML = `
        <div class="alert ${alertClass} alert-dismissible fade show" role="alert">
            <i class="fas ${icon}"></i> ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    `;
    
    const alertContainer = document.getElementById('alertContainer') || createAlertContainer();
    alertContainer.innerHTML += alertHTML;
    
    if (duration) {
        setTimeout(() => {
            alertContainer.querySelector('.alert')?.remove();
        }, duration);
    }
}

function createAlertContainer() {
    const container = document.createElement('div');
    container.id = 'alertContainer';
    container.style.position = 'fixed';
    container.style.top = '20px';
    container.style.right = '20px';
    container.style.zIndex = '9999';
    container.style.width = '400px';
    document.body.appendChild(container);
    return container;
}

// Format date to readable format
function formatDate(dateString) {
    const options = { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' };
    return new Date(dateString).toLocaleDateString('en-US', options);
}

// Format currency
function formatCurrency(amount) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(amount);
}

// Validate email
function isValidEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

// Validate phone number
function isValidPhone(phone) {
    const re = /^[0-9]{10,}$/;
    return re.test(phone.replace(/\D/g, ''));
}

// Get query parameter
function getQueryParam(param) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get(param);
}

// Debounce function for search
function debounce(func, wait) {
    let timeout;
    return function(...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func(...args), wait);
    };
}

// Throttle function
function throttle(func, limit) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func(...args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

// Export functions for CSV
function exportToCSV(filename, data) {
    const csv = convertToCSV(data);
    const link = document.createElement('a');
    link.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv);
    link.download = filename;
    link.click();
}

function convertToCSV(data) {
    const header = Object.keys(data[0]).join(',');
    const rows = data.map(obj => Object.values(obj).join(','));
    return [header, ...rows].join('\n');
}

// Print table
function printTable(tableId) {
    const printWindow = window.open('', '', 'height=400,width=600');
    printWindow.document.write('<html><head><title>Print</title>');
    printWindow.document.write('<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">');
    printWindow.document.write('</head><body>');
    printWindow.document.write(document.getElementById(tableId).outerHTML);
    printWindow.document.write('</body></html>');
    printWindow.document.close();
    printWindow.print();
}

// Confirm dialog
function confirmAction(message) {
    return confirm(message);
}

// Set dark mode
function toggleDarkMode() {
    const isDarkMode = localStorage.getItem('darkMode') === 'true';
    localStorage.setItem('darkMode', !isDarkMode);
    applyDarkMode(!isDarkMode);
}

function applyDarkMode(enable) {
    if (enable) {
        document.body.style.backgroundColor = '#1e1e1e';
        document.body.style.color = '#fff';
    } else {
        document.body.style.backgroundColor = '#f8f9fa';
        document.body.style.color = '#000';
    }
}

// Initialize dark mode on load
document.addEventListener('DOMContentLoaded', function() {
    const isDarkMode = localStorage.getItem('darkMode') === 'true';
    applyDarkMode(isDarkMode);
});

// Collapsible sidebar toggle - state persisted across full-page
// navigations via localStorage (this is a server-rendered MVC app,
// not an SPA, so every link click is a fresh page load).
document.addEventListener('DOMContentLoaded', function () {
    const toggleBtn = document.getElementById('sidebarToggleBtn');
    if (!toggleBtn) return;

    toggleBtn.addEventListener('click', function () {
        const collapsed = document.documentElement.classList.toggle('gms-collapsed');
        localStorage.setItem('gmsSidebarCollapsed', collapsed);
        toggleBtn.title = collapsed ? 'Expand sidebar' : 'Collapse sidebar';
    });

    toggleBtn.title = document.documentElement.classList.contains('gms-collapsed')
        ? 'Expand sidebar' : 'Collapse sidebar';
});

// Open the user menu dropdown on hover, not just click.
document.addEventListener('DOMContentLoaded', function () {
    const dropdownEl = document.getElementById('userMenuDropdown');
    const toggle = dropdownEl ? document.getElementById('userMenuButton') : null;
    if (!dropdownEl || !toggle || typeof bootstrap === 'undefined') return;

    const dropdownInstance = bootstrap.Dropdown.getOrCreateInstance(toggle);
    let closeTimer = null;

    dropdownEl.addEventListener('mouseenter', function () {
        clearTimeout(closeTimer);
        dropdownInstance.show();
    });
    dropdownEl.addEventListener('mouseleave', function () {
        closeTimer = setTimeout(function () {
            dropdownInstance.hide();
        }, 150);
    });
});

// Highlight the sidebar link matching the current page so the user can
// see where they are. Done client-side (rather than per-controller model
// attributes) since this is a full-page-reload MVC app with ~16 view
// controllers - one shared rule here covers all of them.
document.addEventListener('DOMContentLoaded', function () {
    const currentPath = window.location.pathname;
    const links = document.querySelectorAll('#sidebarNav .list-group-item');
    let bestMatch = null;
    let bestLength = -1;

    links.forEach(function (link) {
        const href = link.getAttribute('href');
        if (!href) return;

        let isMatch = false;
        if (href === currentPath) {
            isMatch = true;
        } else if (href !== '/' && currentPath.indexOf(href) === 0) {
            isMatch = true;
        } else if (href === '/dashboard' && /-dashboard$/.test(currentPath)) {
            isMatch = true;
        }

        if (isMatch && href.length > bestLength) {
            bestMatch = link;
            bestLength = href.length;
        }
    });

    if (bestMatch) {
        bestMatch.classList.add('active');
    }
});
