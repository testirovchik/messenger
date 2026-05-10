// assets/js/components/login-form.js
import { login } from '../api/auth.js';
import { saveToken } from '../utils/storage.js';
// import { navigateTo } from '../router.js';

export function initLogin() {
    const form = document.getElementById('loginForm');
    const errorEl = document.getElementById('error');

    if (!form) {
        console.error("Login form not found!");
        return;
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorEl.textContent = '';

        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value.trim();

        if (!email || !password) {
            errorEl.textContent = 'Please fill in all fields';
            return;
        }

        try {
            const data = await login(email, password);
            saveToken(data.token);

            console.log("✅ Login successful!", data);

            // TODO: Change this after creating dashboard
            alert("Login successful! Redirecting...");
            // navigateTo('/dashboard');   // or window.location.href = 'dashboard.html';

        } catch (err) {
            console.error(err);
            errorEl.textContent = err.message || 'Login failed. Please try again.';
        }
    });
}

// export function initSignup() {
//     form
// }