import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const ChatListPage = () => {
    const [chats, setChats] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
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

        fetchChats();
    }, [navigate]);

    const handleChatClick = (chatId) => {
        navigate(`/chat/${chatId}`);
    };

    const containerStyle = {
        maxWidth: '800px',
        margin: '20px auto',
        padding: '20px',
        fontFamily: 'Arial, sans-serif'
    };

    const listStyle = { listStyle: 'none', padding: 0 };
    const itemStyle = {
        display: 'flex',
        padding: '15px',
        borderBottom: '1px solid #eee',
        cursor: 'pointer',
        alignItems: 'center'
    };

    if (loading) return <div style={containerStyle}>Loading chats...</div>;
    if (error) return <div style={containerStyle}><p style={{color: 'red'}}>{error}</p></div>;

    return (
        <div style={containerStyle}>
            <h1>My Chats</h1>
            <ul style={listStyle}>
                {chats.map((chat) => (
                    <li key={chat.id} style={itemStyle} onClick={() => handleChatClick(chat.id)}>
                        <div style={{
                            width: '40px', height: '40px', borderRadius: '50%',
                            backgroundColor: '#0084ff', color: 'white',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            marginRight: '15px'
                        }}>
                            {chat.title ? chat.title[0] : 'C'}
                        </div>
                        <div>
                            <div style={{fontWeight: 'bold'}}>{chat.title || `Chat #${chat.id}`}</div>
                            <div style={{fontSize: '12px', color: '#666'}}>{chat.type}</div>
                        </div>
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default ChatListPage;
