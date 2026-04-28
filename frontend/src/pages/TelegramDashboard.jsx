import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { jwtDecode } from 'jwt-decode';
import api from '../api/axios'; // IMPORTANT: Importing your Axios instance!
import './TelegramDashboard.css';

const TelegramDashboard = () => {
    const [chats, setChats] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [myUserId, setMyUserId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isUploading, setIsUploading] = useState(false); // NEW: Upload state
    const [ws, setWs] = useState(null);

    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null); // NEW: Reference to the hidden file input
    const navigate = useNavigate();

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    // Initial load and Auth check
    useEffect(() => {
        const token = localStorage.getItem('chat_token');
        if (!token) {
            navigate('/login');
            return;
        }

        try {
            const decoded = jwtDecode(token);
            setMyUserId(decoded.sub);
        } catch (err) {
            console.error('Invalid token', err);
            navigate('/login');
            return;
        }

        const fetchChats = async () => {
            try {
                // Using your Axios instance to keep things clean!
                const response = await api.get('/api/chats/my-chats');
                setChats(response.data);
            } catch (err) {
                console.error('Error fetching chats:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchChats();

        // WebSocket Setup
        const socket = new WebSocket("ws://localhost:8081/ws-chat");
        socket.onopen = () => {
            const decoded = jwtDecode(token);
            socket.send(JSON.stringify({ type: "REGISTER", userId: decoded.sub }));
        };

        socket.onmessage = (event) => {
            const message = JSON.parse(event.data);
            // If message is for the currently active chat, add it to list
            if (activeChat && String(message.chatId) === String(activeChat.id)) {
                setMessages((prev) => [...prev, message]);
            }
            // Update last message in chat list
            setChats(prevChats => prevChats.map(c =>
                String(c.id) === String(message.chatId)
                ? { ...c, lastMessage: message.type === 'IMAGE' ? '📷 Photo' : message.content }
                : c
            ));
        };

        setWs(socket);
        return () => socket.close();
    }, [navigate, activeChat?.id]);

    // Fetch messages when active chat changes
    useEffect(() => {
        if (!activeChat) return;

        const fetchHistory = async () => {
            try {
                const response = await api.get(`/api/messages/${activeChat.id}`);
                setMessages(response.data);
            } catch (err) {
                console.error('Error fetching history:', err);
            }
        };

        fetchHistory();
    }, [activeChat]);

    const handleSendMessage = () => {
        if (ws && ws.readyState === WebSocket.OPEN && messageInput.trim() && activeChat) {
            const messagePayload = {
                type: "MESSAGE",
                chatId: activeChat.id,
                content: messageInput,
                senderId: myUserId
            };

            ws.send(JSON.stringify(messagePayload));

            // Optimistic update
            const newMessage = {
                chatId: activeChat.id,
                content: messageInput,
                type: 'TEXT',
                senderId: myUserId,
                createdAt: new Date().toISOString()
            };
            setMessages((prev) => [...prev, newMessage]);

            // Update chat list preview
            setChats(prevChats => prevChats.map(c =>
                c.id === activeChat.id ? { ...c, lastMessage: messageInput } : c
            ));

            setMessageInput('');
        }
    };

    // --- NEW: Handle clicking the paperclip ---
    const handleAttachmentClick = () => {
        fileInputRef.current.click();
    };

    // --- NEW: Handle the actual file upload ---
    const handleFileChange = async (event) => {
        const file = event.target.files[0];
        if (!file || !activeChat) return;

        setIsUploading(true);

        const formData = new FormData();
        formData.append('chatId', activeChat.id);
        formData.append('file', file);

        try {
            // Axios automatically sets 'Content-Type': 'multipart/form-data' and the boundary!
            await api.post('/api/messages/upload', formData);

            // Notice we do NOT manually update the `messages` array here!
            // The backend uploads the image, generates the Presigned URL,
            // and broadcasts it via WebSocket. Our `socket.onmessage` listener
            // will catch it instantly and render it!

        } catch (error) {
            console.error("Upload failed:", error);
            alert("Failed to send image.");
        } finally {
            setIsUploading(false);
            event.target.value = null; // Clear the input so you can upload the same file again
        }
    };

    if (loading) return <div className="loading">Connecting to Telegram...</div>;

    return (
        <div className="telegram-container">
            {/* Sidebar */}
            <div className="sidebar">
                <div className="sidebar-header">
                    <div className="menu-icon">☰</div>
                    <div className="search-container">
                        <input type="text" className="search-input" placeholder="Search" />
                    </div>
                </div>
                <div className="chat-list">
                    {chats.map((chat) => (
                        <div
                            key={chat.id}
                            className={`chat-item ${activeChat?.id === chat.id ? 'active' : ''}`}
                            onClick={() => setActiveChat(chat)}
                        >
                            <div className="avatar">
                                {chat.title ? chat.title[0].toUpperCase() : 'C'}
                            </div>
                            <div className="chat-info">
                                <div className="chat-top-row">
                                    <span className="chat-name">{chat.title || `Chat #${chat.id}`}</span>
                                    <span className="chat-time">12:45 PM</span>
                                </div>
                                <div className="chat-preview">{chat.lastMessage || chat.type}</div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Main Chat Area */}
            <div className="main-chat-area">
                {!activeChat ? (
                    <div className="empty-state">
                        <div className="empty-badge">Select a chat to start messaging</div>
                    </div>
                ) : (
                    <>
                        <div className="chat-header">
                            <div className="header-avatar">
                                {activeChat.title ? activeChat.title[0].toUpperCase() : 'C'}
                            </div>
                            <div className="header-info">
                                <div className="header-name">{activeChat.title || `Chat #${activeChat.id}`}</div>
                                <div className="header-status">last seen recently</div>
                            </div>
                        </div>

                        <div className="messages-list">
                            {messages.map((msg, index) => {
                                const isMe = String(msg.senderId) === String(myUserId);
                                return (
                                    <div key={index} className={`message-wrapper ${isMe ? 'me' : 'others'}`}>
                                        <div className="message-bubble">
                                            {/* NEW: Render Image vs Text */}
                                            {msg.type === 'IMAGE' ? (
                                                <img
                                                    src={msg.content}
                                                    alt="attachment"
                                                    style={{ maxWidth: '100%', maxHeight: '300px', borderRadius: '8px', display: 'block' }}
                                                />
                                            ) : (
                                                msg.content
                                            )}

                                            <span className="message-time">
                                                {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                            <div ref={messagesEndRef} />
                        </div>

                        <div className="input-area">
                            <div className="input-wrapper">
                                {/* NEW: Hidden File Input */}
                                <input
                                    type="file"
                                    accept="image/*"
                                    style={{ display: 'none' }}
                                    ref={fileInputRef}
                                    onChange={handleFileChange}
                                />
                                {/* NEW: Clickable Attachment Icon */}
                                <div
                                    className="attachment-icon"
                                    onClick={handleAttachmentClick}
                                    style={{ opacity: isUploading ? 0.5 : 1, cursor: isUploading ? 'not-allowed' : 'pointer' }}
                                >
                                    {isUploading ? '⏳' : '📎'}
                                </div>
                                <input
                                    type="text"
                                    className="message-input"
                                    placeholder={isUploading ? "Uploading..." : "Write a message..."}
                                    value={messageInput}
                                    onChange={(e) => setMessageInput(e.target.value)}
                                    onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
                                    disabled={isUploading}
                                />
                            </div>
                            <button className="send-button-circle" onClick={handleSendMessage} disabled={isUploading}>
                                <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                                    <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"></path>
                                </svg>
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

export default TelegramDashboard;