import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8081', // Chat Service Base URL
});

// Interceptor to add the token to every request
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('chat_token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default api;
