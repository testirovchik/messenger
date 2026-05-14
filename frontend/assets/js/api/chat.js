// assets/js/api/chat.js
import { getToken } from '../utils/storage.js';

const API_BASE_URL = 'http://localhost:8081';

export async function getMyChats() {
    const token = getToken();

    if (!token) {
        throw new Error('No authentication token found');
    }

    const response = await fetch(`${API_BASE_URL}/api/chats/my-chats`, {
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        }
    });

    if (!response.ok) {
        if (response.status === 401) {
            throw new Error('Session expired. Please login again.');
        }
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Failed to load chats');
    }

    return response.json();
}