import axios from 'axios';

const api = axios.create({
    // Point directly to the Ingress!
    baseURL: 'http://messenger.local',
});

// Interceptor to add the token to every request (Keep this exactly the same!)
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