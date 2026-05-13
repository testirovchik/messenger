const API_BASE_URL = 'http://localhost:8080';

export async function login(email, password) {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });

    if (!res.ok) throw new Error((await res.json()).message || 'Login failed');
    return res.json();
}

export async function register(email, password, fullName) {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, fullName })
    });

    if (!res.ok) throw new Error((await res.json()).message || 'Registration failed');
    return res.json();
}