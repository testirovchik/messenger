import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { jwtDecode } from 'jwt-decode';
import api from '../api/axios';
import './TelegramDashboard.css';

const TelegramDashboard = () => {
    // --- Chat & Message State ---
    const [chats, setChats] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [myUserId, setMyUserId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [isUploading, setIsUploading] = useState(false);
    const [ws, setWs] = useState(null);

    // --- Search State ---
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [isSearching, setIsSearching] = useState(false);

    // --- Refs ---
    const messagesEndRef = useRef(null);
    const fileInputRef = useRef(null);
    const navigate = useNavigate();

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    // 1. Initial Load & WebSocket Setup
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

    // 2. Fetch Chat History when Active Chat Changes
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

    // 3. Debounced Search Effect
    useEffect(() => {
        if (!searchQuery.trim()) {
            setSearchResults([]);
            setIsSearching(false);
            return;
        }

        setIsSearching(true);
        const timer = setTimeout(async () => {
            const token = localStorage.getItem('chat_token');
            try {
                // We use raw fetch here because it points to the Auth Service (port 8080)
                // while your Axios instance defaults to the Chat Service (port 8081)
                const response = await fetch(`http://localhost:8080/api/users/search?query=${searchQuery}`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });

                if (response.ok) {
                    const data = await response.json();
                    setSearchResults(data);
                }
            } catch (err) {
                console.error('Search error:', err);
            } finally {
                setIsSearching(false);
            }
        }, 500); // 0.5s Debounce

        return () => clearTimeout(timer);
    }, [searchQuery]);

    // 4. Send Standard Text Message
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

    // 5. Handle Starting a New Chat from Search
    const handleUserClick = async (userId) => {
        try {
            const response = await api.post(`/api/chats/private?partnerId=${userId}`);
            const newChat = response.data;

            // Clear search state
            setSearchQuery('');
            setSearchResults([]);

            // Add to chat list if it's brand new, then open it
            setChats(prev => {
                const exists = prev.find(c => c.id === newChat.id);
                return exists ? prev : [newChat, ...prev];
            });
            setActiveChat(newChat);

        } catch (err) {
            console.error("Create chat error:", err);
            alert("Error creating chat");
        }
    };

    // 6. Handle Image Uploads
    const handleAttachmentClick = () => {
        fileInputRef.current.click();
    };

    const handleFileChange = async (event) => {
        const file = event.target.files[0];
        if (!file || !activeChat) return;

        setIsUploading(true);

        const formData = new FormData();
        formData.append('chatId', activeChat.id);
        formData.append('file', file);

        try {
            await api.post('/api/messages/upload', formData);
            // WebSocket will handle displaying the image automatically!
        } catch (error) {
            console.error("Upload failed:", error);
            alert("Failed to send image.");
        } finally {
            setIsUploading(false);
            event.target.value = null; // Reset input
        }
    };

    if (loading) return <div className="loading" style={{padding: '20px', textAlign: 'center'}}>Connecting to Telegram...</div>;

    return (
        <div className="telegram-container">
            {/* --- SIDEBAR --- */}
            <div className="sidebar">
                <div className="sidebar-header">
                    <div className="menu-icon">☰</div>
                    <div className="search-container">
                        <input
                            type="text"
                            className="search-input"
                            placeholder="Search users..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>
                </div>

                <div className="chat-list">
                    {searchQuery.trim() ? (
                        /* SEARCH RESULTS VIEW */
                        <>
                            <div style={{ padding: '8px 16px', fontSize: '13px', fontWeight: 'bold', color: '#707579', backgroundColor: '#f4f4f5' }}>
                                Global Search
                            </div>
                            {isSearching && <div style={{padding: '15px', textAlign: 'center', color: '#707579'}}>Searching...</div>}
                            {!isSearching && searchResults.length === 0 && (
                                <div style={{padding: '15px', textAlign: 'center', color: '#707579'}}>No users found</div>
                            )}
                            {searchResults.map((user) => (
                                <div key={user.id} className="chat-item" onClick={() => handleUserClick(user.id)}>
                                    <div className="avatar" style={{backgroundColor: '#6b9fcb'}}>
                                        {user.username ? user.username[0].toUpperCase() : 'U'}
                                    </div>
                                    <div className="chat-info">
                                        <div className="chat-top-row">
                                            <span className="chat-name">{user.username}</span>
                                        </div>
                                        <div className="chat-preview" style={{color: '#3390ec'}}>Click to start chat</div>
                                    </div>
                                </div>
                            ))}
                        </>
                    ) : (
                        /* EXISTING CHATS VIEW */
                        chats.map((chat) => (
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
                                        <span className="chat-time">
                                            {/* You could format chat.lastMessageTime here if added to backend */}
                                        </span>
                                    </div>
                                    <div className="chat-preview">{chat.lastMessage || chat.type}</div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* --- MAIN CHAT AREA --- */}
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
                                <input
                                    type="file"
                                    accept="image/*"
                                    style={{ display: 'none' }}
                                    ref={fileInputRef}
                                    onChange={handleFileChange}
                                />
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