import { isLoggedIn, getToken } from './utils/storage.js';
import { getMyChats, initChatWebSocket } from './api/chat.js';
import { getEmailFromToken } from './api/auth.js';

document.addEventListener('DOMContentLoaded', async () => {
    if (!isLoggedIn()) {
        window.location.href = 'index.html';
        return;
    }

    const token = String(getToken());
    const chatListEl = document.getElementById('chatList');

    try {
        const email = await getEmailFromToken(token);
        console.log(token, email);
        const wsController = initChatWebSocket(token, email);

        chatListEl.innerHTML = '<p>Loading chats...</p>';
        const myChats = await getMyChats();
        renderChatList(myChats, chatListEl);
    } catch (err) {
        console.error(err);
        chatListEl.innerHTML = `<p class="error">Failed to initialize: ${err.message}</p>`;
    }
});

function renderChatList(myChats, container) {
    if (!myChats || myChats.length === 0) {
        container.innerHTML = '<p>No chats yet</p>';
        return;
    }

    let html = '';
    myChats.forEach(chat => {
        html += `
            <div class="chat-item" data-chat-id="${chat.id || ''}" onclick="">
                <div class="chat-info">
                    <h4>${chat.chatTitle || chat.name || 'Unnamed Chat'}</h4>
                    <p>${chat.lastMessage || 'No messages yet'}</p>
                    <small>${chat.createdAt ? new Date(chat.createdAt).toLocaleDateString() : ''}</small>
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}