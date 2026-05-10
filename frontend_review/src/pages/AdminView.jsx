import { useState, useEffect } from 'react';
import { adminAPI, reportsAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import OsintConfigPanel from '../components/OsintConfigPanel';
import OsintNewsFeed from '../components/OsintNewsFeed';
import AdminAuditLog from '../components/AdminAuditLog';
import {
  ShieldCheck, Users, FileText, CheckCircle, XCircle,
  AlertTriangle, Clock, Eye, Trash2, UserCheck, Ban, Shield,
  Settings, Newspaper, ClipboardList
} from 'lucide-react';

const STATUS_OPTIONS = ['PENDING', 'VERIFIED', 'REJECTED', 'RESOLVED'];
const STATUS_LABELS = { PENDING: 'Pendiente', VERIFIED: 'Verificado', REJECTED: 'Rechazado', RESOLVED: 'Resuelto' };
const STATUS_COLORS = { PENDING: '#f59e0b', VERIFIED: '#10b981', REJECTED: '#ef4444', RESOLVED: '#6366f1' };
const TYPE_LABELS = { ROBBERY: 'Robo', ACCIDENT: 'Accidente', TRAFFIC: 'Tráfico', TRANSIT_OP: 'Op. Tránsito', OTHER: 'Otro' };
const ROLE_OPTIONS = ['CITIZEN', 'ADMIN'];

export default function AdminView({ section, reports: globalReports, onReportUpdated }) {
  const { isAdmin } = useAuth();
  const [users, setUsers] = useState([]);
  const [reports, setReports] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [loadingReports, setLoadingReports] = useState(true);
  const [activeAdminTab, setActiveAdminTab] = useState('reports');
  const [filterStatus, setFilterStatus] = useState('ALL');

  useEffect(() => {
    if (!isAdmin) return;
    loadUsers();
    loadReports();
  }, [isAdmin]);

  // Sincronizar con actualizaciones del WebSocket que llegan via App.jsx
  // Cuando la IA cambia el estado de un reporte (ej: PENDING -> VERIFIED),
  // el WebSocket lo propaga al array global en App.jsx, que lo pasa aquí.
  useEffect(() => {
    if (globalReports && globalReports.length > 0) {
      setReports((prev) => {
        if (prev.length === 0) return prev; // si no ha cargado aún, no sobreescribir
        return prev.map((r) => {
          const updated = globalReports.find((gr) => gr.id === r.id);
          return updated ? { ...r, status: updated.status, trustScore: updated.trustScore } : r;
        });
      });
    }
  }, [globalReports]);

  const loadUsers = async () => {
    try {
      const res = await adminAPI.getUsers(0, 50);
      setUsers(res.data?.content || res.data || []);
    } catch (err) { console.error('Error loading users:', err); }
    finally { setLoadingUsers(false); }
  };

  const loadReports = async () => {
    try {
      const res = await reportsAPI.getAll(0, 100);
      setReports(res.data?.content || res.data || []);
    } catch (err) { console.error('Error loading reports:', err); }
    finally { setLoadingReports(false); }
  };

  const updateStatus = async (id, newStatus) => {
    try {
      await adminAPI.updateReportStatus(id, newStatus);
      // Actualizar estado local
      setReports((prev) => prev.map((r) => (r.id === id ? { ...r, status: newStatus } : r)));
      // Notificar al componente padre (App.jsx) para mantener el estado global sincronizado
      if (onReportUpdated) onReportUpdated(id, newStatus);
    } catch (err) {
      alert('Error: ' + (err.response?.data?.message || err.message));
    }
  };

  const changeRole = async (id, newRole) => {
    try {
      const res = await adminAPI.changeRole(id, newRole);
      setUsers((prev) => prev.map((u) => (u.id === id ? res.data : u)));
    } catch (err) {
      alert('Error: ' + (err.response?.data?.message || err.message));
    }
  };

  const toggleBan = async (id) => {
    try {
      const res = await adminAPI.toggleBan(id);
      setUsers((prev) => prev.map((u) => (u.id === id ? res.data : u)));
    } catch (err) {
      alert('Error: ' + (err.response?.data?.message || err.message));
    }
  };

  const deleteUser = async (id, name) => {
    if (!confirm(`¿Eliminar permanentemente a "${name}"? Esta acción NO se puede deshacer.`)) return;
    try {
      await adminAPI.deleteUser(id);
      setUsers((prev) => prev.filter((u) => u.id !== id));
    } catch (err) {
      alert('Error: ' + (err.response?.data?.message || err.message));
    }
  };

  // ═══════════════ SECCIÓN MAIN (pantalla completa) ═══════════════
  if (section === 'main') {
    if (!isAdmin) {
      return (
        <div className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
            <ShieldCheck size={48} style={{ opacity: 0.3 }} />
            <p style={{ marginTop: '1rem' }}>Acceso restringido a administradores</p>
          </div>
        </div>
      );
    }

    const filteredReports = filterStatus === 'ALL' ? reports : reports.filter((r) => r.status === filterStatus);

    return (
      <div className="main-content" style={{ overflow: 'auto', padding: '2rem', background: 'var(--bg-primary)' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>

          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2rem' }}>
            <div>
              <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <ShieldCheck size={24} style={{ color: 'var(--accent)' }} />
                Panel de Administración
              </h1>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Gestión de usuarios y moderación de reportes
              </p>
            </div>
          </div>

          {/* Tabs */}
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.75rem', flexWrap: 'wrap' }}>
            <button
              className={`btn btn-sm ${activeAdminTab === 'reports' ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setActiveAdminTab('reports')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <FileText size={16} /> Reportes ({reports.length})
            </button>
            <button
              className={`btn btn-sm ${activeAdminTab === 'users' ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setActiveAdminTab('users')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <Users size={16} /> Usuarios ({users.length})
            </button>
            <button
              className={`btn btn-sm ${activeAdminTab === 'osint-config' ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setActiveAdminTab('osint-config')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <Settings size={16} /> Config OSINT
            </button>
            <button
              className={`btn btn-sm ${activeAdminTab === 'osint-news' ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setActiveAdminTab('osint-news')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <Newspaper size={16} /> Noticias OSINT
            </button>
            <button
              className={`btn btn-sm ${activeAdminTab === 'audit-log' ? 'btn-primary' : 'btn-ghost'}`}
              onClick={() => setActiveAdminTab('audit-log')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}
            >
              <ClipboardList size={16} /> Audit Log
            </button>
          </div>

          {/* ═══ REPORTS TABLE ═══ */}
          {activeAdminTab === 'reports' && (
            <div>
              {/* Filtros */}
              <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
                <button className={`btn btn-sm ${filterStatus === 'ALL' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setFilterStatus('ALL')}>
                  Todos ({reports.length})
                </button>
                {STATUS_OPTIONS.map((s) => {
                  const count = reports.filter((r) => r.status === s).length;
                  return (
                    <button key={s} className={`btn btn-sm ${filterStatus === s ? 'btn-primary' : 'btn-ghost'}`}
                      onClick={() => setFilterStatus(s)} style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                      <span style={{ width: 8, height: 8, borderRadius: '50%', background: STATUS_COLORS[s] }} />
                      {STATUS_LABELS[s]} ({count})
                    </button>
                  );
                })}
              </div>

              {loadingReports ? (
                <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}><span className="spinner" /></div>
              ) : (
                <div style={{ display: 'grid', gap: '0.75rem' }}>
                  {filteredReports.map((r) => (
                    <div key={r.id} className="glass-card" style={{ padding: '1rem', display: 'grid', gridTemplateColumns: '1fr auto', gap: '1rem', alignItems: 'center' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.3rem' }}>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>#{r.id}</span>
                          <span className={`badge badge-${r.incidentType?.toLowerCase()}`} style={{ fontSize: '0.65rem' }}>{TYPE_LABELS[r.incidentType] || r.incidentType}</span>
                          {r.trustScore != null && (
                            <span style={{ fontSize: '0.65rem', color: r.trustScore >= 50 ? '#10b981' : '#f59e0b', fontWeight: 600 }}>
                              IA: {r.trustScore.toFixed(0)}%
                            </span>
                          )}
                        </div>
                        <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: 1.4, marginBottom: '0.25rem' }}>
                          {r.description?.substring(0, 200) || 'Sin descripción'}
                        </p>
                        <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                          📍 {r.address || 'Sin dirección'} · {r.reportDate || ''}
                        </p>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem', minWidth: 120 }}>
                        {STATUS_OPTIONS.map((s) => (
                          <button key={s}
                            className={`btn btn-sm ${r.status === s ? 'btn-primary' : 'btn-ghost'}`}
                            onClick={() => updateStatus(r.id, s)}
                            style={{ fontSize: '0.7rem', padding: '0.25rem 0.5rem', display: 'flex', alignItems: 'center', gap: '0.25rem', justifyContent: 'flex-start' }}>
                            {s === 'PENDING' && <Clock size={12} />}
                            {s === 'VERIFIED' && <CheckCircle size={12} />}
                            {s === 'REJECTED' && <XCircle size={12} />}
                            {s === 'RESOLVED' && <Eye size={12} />}
                            {STATUS_LABELS[s]}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                  {filteredReports.length === 0 && (
                    <p style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>Sin reportes con ese filtro</p>
                  )}
                </div>
              )}
            </div>
          )}

          {/* ═══ USERS TABLE ═══ */}
          {activeAdminTab === 'users' && (
            <div>
              {loadingUsers ? (
                <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}><span className="spinner" /></div>
              ) : (
                <div style={{ display: 'grid', gap: '0.75rem' }}>
                  {users.map((u) => (
                    <div key={u.id} className="glass-card" style={{
                      padding: '1rem', display: 'grid',
                      gridTemplateColumns: '1fr auto', gap: '1rem', alignItems: 'center',
                      opacity: u.active === false ? 0.6 : 1,
                      borderLeft: u.active === false ? '3px solid #ef4444' : 'none',
                    }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
                          <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)' }}>{u.name}</span>
                          <span className={`badge ${u.role === 'ADMIN' ? 'badge-robbery' : 'badge-transit_op'}`} style={{ fontSize: '0.6rem' }}>
                            {u.role}
                          </span>
                          {u.active === false && (
                            <span style={{ fontSize: '0.6rem', padding: '0.1rem 0.4rem', borderRadius: '1rem', background: '#ef444422', color: '#ef4444', fontWeight: 600 }}>
                              BANEADO
                            </span>
                          )}
                        </div>
                        <div style={{ display: 'flex', gap: '1rem', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          <span>📧 {u.email}</span>
                          <span>🪪 {u.cedula || '—'}</span>
                          <span>⭐ Trust: {u.trustLevel != null ? u.trustLevel : '—'}</span>
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                        {/* Cambiar rol */}
                        <select
                          value={u.role}
                          onChange={(e) => changeRole(u.id, e.target.value)}
                          style={{
                            padding: '0.3rem 0.5rem', fontSize: '0.72rem', borderRadius: '0.35rem',
                            background: 'var(--bg-card)', color: 'var(--text-primary)',
                            border: '1px solid var(--border-color)', cursor: 'pointer'
                          }}
                        >
                          {ROLE_OPTIONS.map((r) => <option key={r} value={r}>{r}</option>)}
                        </select>
                        {/* Ban/Unban */}
                        <button
                          className="btn btn-sm btn-ghost"
                          onClick={() => toggleBan(u.id)}
                          title={u.active === false ? 'Desbanear' : 'Banear'}
                          style={{ color: u.active === false ? '#10b981' : '#f59e0b', fontSize: '0.72rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}
                        >
                          {u.active === false ? <><UserCheck size={14} /> Unban</> : <><Ban size={14} /> Ban</>}
                        </button>
                        {/* Eliminar */}
                        <button
                          className="btn btn-sm btn-ghost"
                          onClick={() => deleteUser(u.id, u.name)}
                          title="Eliminar permanentemente"
                          style={{ color: '#ef4444', fontSize: '0.72rem', display: 'flex', alignItems: 'center', gap: '0.25rem' }}
                        >
                          <Trash2 size={14} /> Eliminar
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* ═══ OSINT CONFIG ═══ */}
          {activeAdminTab === 'osint-config' && (
            <OsintConfigPanel />
          )}

          {/* ═══ OSINT NEWS ═══ */}
          {activeAdminTab === 'osint-news' && (
            <OsintNewsFeed />
          )}

          {/* ═══ AUDIT LOG ═══ */}
          {activeAdminTab === 'audit-log' && (
            <AdminAuditLog />
          )}
        </div>
      </div>
    );
  }

  // ═══════════════ SECCIÓN SIDEBAR (resumen) ═══════════════
  if (!isAdmin) {
    return (
      <div className="sidebar-content">
        <div className="empty-state"><ShieldCheck size={24} /><p>No tienes permisos de administrador</p></div>
      </div>
    );
  }

  return (
    <div className="sidebar-content">
      <div className="section-header"><h2>Admin</h2></div>

      {/* Resumen rápido */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{reports.length}</div>
          <div className="stat-label"><FileText size={12} style={{ display: 'inline', marginRight: 4 }} />Reportes</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{users.length}</div>
          <div className="stat-label"><Users size={12} style={{ display: 'inline', marginRight: 4 }} />Usuarios</div>
        </div>
      </div>

      <div className="glass-card" style={{ marginTop: '0.5rem' }}>
        <div className="section-header"><h2 style={{ fontSize: '0.85rem' }}>Pendientes de Revisión</h2></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', marginTop: '0.5rem' }}>
          {reports.filter((r) => r.status === 'PENDING').slice(0, 5).map((r) => (
            <div key={r.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem' }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#f59e0b', flexShrink: 0 }} />
              <span style={{ flex: 1, color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {r.description?.substring(0, 40) || 'Sin desc'}
              </span>
              <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>#{r.id}</span>
            </div>
          ))}
          {reports.filter((r) => r.status === 'PENDING').length === 0 &&
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'center' }}>Todo revisado ✅</p>
          }
        </div>
      </div>

      <div className="glass-card">
        <div className="section-header"><h2 style={{ fontSize: '0.85rem' }}>Usuarios Baneados</h2></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', marginTop: '0.5rem' }}>
          {users.filter((u) => u.active === false).map((u) => (
            <div key={u.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem' }}>
              <Ban size={12} style={{ color: '#ef4444', flexShrink: 0 }} />
              <span style={{ flex: 1, color: 'var(--text-secondary)' }}>{u.name}</span>
              <button className="btn btn-ghost btn-sm" onClick={() => toggleBan(u.id)} style={{ fontSize: '0.65rem', color: '#10b981', padding: '0.1rem 0.3rem' }}>
                Unban
              </button>
            </div>
          ))}
          {users.filter((u) => u.active === false).length === 0 &&
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'center' }}>Ninguno baneado</p>
          }
        </div>
      </div>
    </div>
  );
}
