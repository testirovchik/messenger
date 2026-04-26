import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './ChatListPage.css';

const ChatListPage = () => {
    const [chats, setChats] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [partnerId, setPartnerId] = useState('');
    const navigate = useNavigate();

    const fetchChats = async () => {
        const token = localStorage.getItem('chat_token');
        if (!token) {
            navigate('/login');
            return;
        }

        try {
            const response = await fetch('http://localhost:8081/api/chats/my-chats', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            setChats(data);
        } catch (err) {
            console.error('Error fetching chats:', err);
            setError('Failed to load chats. Ensure Chat Service (8081) is running.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchChats();
    }, [navigate]);

    const handleChatClick = (chatId) => {
        navigate(`/chat/${chatId}`);
    };

    const createChat = async () => {
        if (!partnerId) return;
        const token = localStorage.getItem('chat_token');
        try {
            const res = await fetch(`http://localhost:8081/api/chats/private?partnerId=${partnerId}`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) {
                const newChat = await res.json();
                navigate(`/chat/${newChat.id}`);
            } else {
                alert("Could not start chat with this user.");
            }
        } catch (err) {
            alert("Error creating chat");
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('chat_token');
        navigate('/login');
    };

    if (loading) return <div className="loading-message">Loading chats...</div>;

    return (
        <div className="chat-list-container">
            <div className="header-actions">
                <h1>My Chats</h1>
                <button className="logout-btn" onClick={handleLogout}>Logout</button>
            </div>

            <div className="start-chat-section">
                <input
                    placeholder="Enter Partner User ID..."
                    value={partnerId}
                    onChange={(e) => setPartnerId(e.target.value)}
                    className="partner-input"
                />
                <button onClick={createChat} className="start-btn">Start Private Chat</button>
            </div>

            {error && <div className="error-message"><p>{error}</p></div>}

            <ul className="chat-list">
                {chats.map((chat) => (
                    <li key={chat.id} className="chat-list-item" onClick={() => handleChatClick(chat.id)}>
                        <div className="chat-avatar">
                            {chat.title ? chat.title[0].toUpperCase() : 'C'}
                        </div>
                        <div className="chat-info">
                            <div className="chat-title">{chat.title || `Chat #${chat.id}`}</div>
                            <div className="chat-type">{chat.type}</div>
                        </div>
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default ChatListPage;
