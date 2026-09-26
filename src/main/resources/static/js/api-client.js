// API Client for making HTTP calls to microservices
class APIClient {
    constructor(baseURL = '/api') {
        this.baseURL = baseURL;
        this.token = localStorage.getItem('jwt_token');
    }

    getToken() {
        return localStorage.getItem('jwt_token');
    }

    setToken(token) {
        localStorage.setItem('jwt_token', token);
        this.token = token;
    }

    removeToken() {
        localStorage.removeItem('jwt_token');
        this.token = null;
    }

    getHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        
        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }
        
        return headers;
    }

    async request(url, options = {}) {
        const response = await fetch(`${this.baseURL}${url}`, {
            ...options,
            headers: {
                ...this.getHeaders(),
                ...options.headers
            }
        });

        if (response.status === 401) {
            this.removeToken();
            window.location.href = '/login';
            throw new Error('Unauthorized');
        }

        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || `HTTP Error: ${response.status}`);
        }

        return response.json().catch(() => ({}));
    }

    async get(url, options = {}) {
        return this.request(url, {
            ...options,
            method: 'GET'
        });
    }

    async post(url, data = {}, options = {}) {
        return this.request(url, {
            ...options,
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    async put(url, data = {}, options = {}) {
        return this.request(url, {
            ...options,
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    async delete(url, options = {}) {
        return this.request(url, {
            ...options,
            method: 'DELETE'
        });
    }
}

// Global API client instance
const apiClient = new APIClient('/api');
