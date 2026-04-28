import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './ChatListPage.css';

const ChatListPage = () => {
    const [chats, setChats] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // Search State
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [isSearching, setIsSearching] = useState(false);

    const navigate = useNavigate();

    // Fetch existing chats
    const fetchChats = async () => {
        const token = localStorage.getItem('chat_token');
        if (!token) {
            navigate('/login');
            return;
        }

        try {
            const response = await fetch('http://localhost:8081/api/chats/my-chats', {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            setChats(data);
        } catch (err) {
            console.error('Error fetching chats:', err);
            setError('Failed to load chats.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchChats();
    }, [navigate]);

    // Debounced Search Effect
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
                // Call Auth Service on port 8080
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

    const handleChatClick = (chatId) => {
        navigate(`/chat/${chatId}`);
    };

    const handleUserClick = async (userId) => {
        const token = localStorage.getItem('chat_token');
        try {
            const res = await fetch(`http://localhost:8081/api/chats/private?partnerId=${userId}`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) {
                const newChat = await res.json();
                navigate(`/chat/${newChat.id}`);
            }
        } catch (err) {
            console.error("Create chat error:", err);
            alert("Error creating chat");
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('chat_token');
        navigate('/login');
    };

    if (loading) return <div className="loading-message">Loading conversations...</div>;

    return (
        <div className="chat-list-container">
            <div className="header-actions">
                <h1>Messages</h1>
                <button className="logout-btn" onClick={handleLogout}>Logout</button>
            </div>

            {/* User Search Input */}
            <div className="search-section">
                <input
                    type="text"
                    placeholder="Search people by username..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="search-input"
                />
            </div>

            {error && <div className="error-message"><p>{error}</p></div>}

            <div className="content-view">
                {searchQuery ? (
                    /* Search Results View */
                    <div className="results-view">
                        <p className="view-label">Global Search</p>
                        {isSearching && <div className="searching-indicator">Searching...</div>}
                        {!isSearching && searchResults.length === 0 && (
                            <div className="no-results">No users found matching "{searchQuery}"</div>
                        )}
                        <ul className="chat-list">
                            {searchResults.map((user) => (
                                <li key={user.id} className="chat-list-item" onClick={() => handleUserClick(user.id)}>
                                    <div className="chat-avatar search-avatar">
                                        {user.username ? user.username[0].toUpperCase() : 'U'}
                                    </div>
                                    <div className="chat-info">
                                        <div className="chat-title">{user.username}</div>
                                        <div className="chat-type">Start conversation</div>
                                    </div>
                                    <div className="plus-icon">+</div>
                                </li>
                            ))}
                        </ul>
                    </div>
                ) : (
                    /* Existing Chats View */
                    <div className="chats-view">
                        <p className="view-label">Recent Conversations</p>
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
                            {chats.length === 0 && (
                                <div className="no-chats">No active chats. Search for someone above!</div>
                            )}
                        </ul>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ChatListPage;
