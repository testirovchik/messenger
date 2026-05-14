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

// assets/js/api/chat.js

export function initChatWebSocket(token, email) {
    let socket = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 5;
    const RECONNECT_DELAY_MS = 3000;

    function connect() {
        socket = new WebSocket('ws://localhost:8081/ws-chat');

        socket.onopen = () => {
            console.log('WebSocket connected');
            reconnectAttempts = 0; // reset on successful connect
            socket.send(JSON.stringify({
                type: 'REGISTER',
                token: `Bearer ${token}`,
                email: email
            }));
        };

        socket.onmessage = (event) => {
            console.log('Message from server:', event.data);
        };

        socket.onerror = (error) => {
            console.error('WebSocket error:', error);
        };

        socket.onclose = (event) => {
            console.warn(`WebSocket closed. Code: ${event.code}, Reason: ${event.reason}`);

            // Don't reconnect if closed intentionally (e.g. policy violation / logout)
            if (event.code === 1008 || event.code === 1000) {
                console.log('WebSocket closed intentionally, not reconnecting.');
                return;
            }

            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                console.log(`Reconnecting... attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}`);
                setTimeout(connect, RECONNECT_DELAY_MS);
            } else {
                console.error('Max reconnect attempts reached.');
            }
        };
    }

    connect();

    // Return a controller so you can close it manually (e.g. on logout)
    return {
        close: () => socket?.close(1000, 'User logged out'),
        getSocket: () => socket
    };
}