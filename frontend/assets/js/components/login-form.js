import { login } from '../api/auth.js';
import { register } from '../api/auth.js';
import { saveToken } from '../utils/storage.js';

let isLoginMode = true;

export function initAuth() {
    const form = document.getElementById('authForm');
    const errorEl = document.getElementById('error');
    const titleEl = document.getElementById('form-title');
    const submitBtn = document.getElementById('submitBtn');
    const switchBtn = document.getElementById('switchBtn');
    const fullNameField = document.getElementById('fullName');
    const switchText = document.getElementById('switchText');

    function toggleMode() {
        isLoginMode = !isLoginMode;

        if (isLoginMode) {
            titleEl.textContent = "Sign In";
            submitBtn.textContent = "Login";
            fullNameField.style.display = "none";
            switchText.innerHTML = `Don't have an account? <span id="switchBtn" class="link">Sign Up</span>`;
        } else {
            titleEl.textContent = "Create Account";
            submitBtn.textContent = "Sign Up";
            fullNameField.style.display = "block";
            switchText.innerHTML = `Already have an account? <span id="switchBtn" class="link">Sign In</span>`;
        }

        document.getElementById('switchBtn').addEventListener('click', toggleMode);
    }

    switchBtn.addEventListener('click', toggleMode);

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        errorEl.textContent = '';

        const email = document.getElementById('email').value.trim();
        const password = document.getElementById('password').value.trim();
        const fullName = document.getElementById('fullName').value.trim();

        try {
            if (isLoginMode) {
                const data = await login(email, password);
                saveToken(data.token);
                alert("✅ Login successful!");
            } else {
                if (!fullName) {
                    errorEl.textContent = "Full name is required!";
                    return;
                }

                await register(email, password, fullName);
                alert("✅ Account created successfully! Please login now.");

                isLoginMode = false;
                toggleMode();
            }
        } catch (err) {
            errorEl.textContent = err.message || "Something went wrong";
        }
    });
}