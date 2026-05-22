import { useState, useEffect } from 'react';
import { notificationsAPI } from '../services/api';
import { Bell, BellOff, Check, AlertTriangle, Info, AlertCircle } from 'lucide-react';

const TYPE_CONFIG = {
  ALERT: { icon: AlertTriangle, color: 'var(--warning)', bg: 'var(--warning-bg)' },
  WARNING: { icon: AlertCircle, color: 'var(--error)', bg: 'var(--error-bg)' },
  INFO: { icon: Info, color: 'var(--info)', bg: 'var(--info-bg)' },
};

export default function NotificationsView({ section }) {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadNotifications(); }, []);

  const loadNotifications = async () => {
    try {
      const res = await notificationsAPI.getAll();
      setNotifications(Array.isArray(res.data) ? res.data : res.data?.content || []);
    } catch (err) {
      console.error('Error loading notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  const markAsRead = async (id) => {
    try {
      await notificationsAPI.markRead(id);
      setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (err) {
      console.error('Error marking as read:', err);
    }
  };



  const unreadCount = notifications.filter((n) => !n.read).length;

  if (section === 'main') {
    return (
      <div className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)' }}>
        <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
          <Bell size={48} style={{ marginBottom: '1rem', opacity: 0.3 }} />
          <p style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Centro de Notificaciones</p>
          <p style={{ fontSize: '0.82rem', maxWidth: 300, margin: '0.5rem auto 0' }}>
            Recibe alertas cuando se detecten incidentes cerca de tu zona o cuando tus reportes sean moderados.
          </p>
        </div>
      </div>
    );
  }

  // ═══════════════ SIDEBAR ═══════════════
  if (loading) {
    return (<div className="sidebar-content"><div className="empty-state"><span className="spinner" /><p>Cargando notificaciones...</p></div></div>);
  }

  return (
    <div className="sidebar-content">
      <div className="section-header">
        <h2>Notificaciones</h2>

      </div>

      {unreadCount > 0 && (
        <div style={{ background: 'var(--accent-glow)', borderRadius: 'var(--radius-md)', padding: '0.5rem 0.75rem', fontSize: '0.75rem', color: 'var(--accent-hover)', fontWeight: 600 }}>
          {unreadCount} notificación{unreadCount !== 1 ? 'es' : ''} sin leer
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {notifications.length === 0 ? (
          <div className="empty-state"><BellOff size={24} /><p>No tienes notificaciones</p></div>
        ) : (
          notifications.map((n) => {
            const config = TYPE_CONFIG[n.type] || TYPE_CONFIG.INFO;
            const IconComp = config.icon;
            return (
              <div key={n.id} className={`notification-item ${!n.read ? 'unread' : ''}`}
                onClick={() => !n.read && markAsRead(n.id)} style={{ cursor: n.read ? 'default' : 'pointer' }}>
                <div className="notification-icon" style={{ background: config.bg }}>
                  <IconComp size={16} style={{ color: config.color }} />
                </div>
                <div className="notification-body">
                  <div className="notif-title">{n.title}</div>
                  <div className="notif-message">{n.message}</div>
                  {n.createdAt && (
                    <div className="notif-time">
                      {new Date(n.createdAt).toLocaleString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
                    </div>
                  )}
                </div>
                {!n.read && <div style={{ flexShrink: 0, alignSelf: 'center' }}><Check size={14} style={{ color: 'var(--text-muted)' }} /></div>}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
