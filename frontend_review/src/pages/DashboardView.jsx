import { useState, useEffect } from 'react';
import { statsAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  FileText, Clock, CheckCircle, XCircle, Users, MapPin,
  BarChart3, TrendingUp, AlertTriangle, Shield, Activity
} from 'lucide-react';

const TYPE_LABELS = { ROBBERY: 'Robo', ACCIDENT: 'Accidente', TRAFFIC: 'Tráfico', TRANSIT_OP: 'Op. Tránsito', OTHER: 'Otro' };
const TYPE_COLORS = { ROBBERY: '#ef4444', ACCIDENT: '#f59e0b', TRAFFIC: '#eab308', TRANSIT_OP: '#3b82f6', OTHER: '#64748b' };
const RISK_COLORS = { HIGH: '#ef4444', MEDIUM: '#f59e0b', LOW: '#10b981' };
const RISK_LABELS = { HIGH: 'Alto', MEDIUM: 'Medio', LOW: 'Bajo' };

export default function DashboardView({ section, onReportClick }) {
  const { isAuthenticated } = useAuth();
  const [stats, setStats] = useState(null);
  const [dangerousZones, setDangerousZones] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => { loadAll(); }, []);

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryRes, zonesRes, timelineRes] = await Promise.all([
        statsAPI.getSummary(),
        statsAPI.getDangerousZones(7, 10),
        statsAPI.getTimeline(10),
      ]);
      setStats(summaryRes.data);
      setDangerousZones(zonesRes.data || []);
      setTimeline(timelineRes.data || []);
    } catch (err) {
      console.error('Error loading stats:', err);
      const status = err.response?.status;
      if (status === 403) {
        setError('No tienes permisos para ver estas estadísticas. Asegúrate de haber iniciado sesión como administrador.');
      } else if (status === 401) {
        setError('Tu sesión expiró. Por favor ingresa de nuevo.');
      } else {
        setError('No se pudieron cargar las estadísticas. Verifica tu conexión.');
      }
    } finally {
      setLoading(false);
    }
  };

  // ═══════════════ SECCIÓN MAIN (pantalla completa) ═══════════════
  if (section === 'main') {
    if (loading) {
      return (
        <div className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
            <span className="spinner" />
            <p style={{ marginTop: '1rem' }}>Cargando dashboard...</p>
          </div>
        </div>
      );
    }

    if (error) {
      return (
        <div className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ textAlign: 'center', color: 'var(--text-muted)', maxWidth: 400 }}>
            <AlertTriangle size={40} style={{ color: '#f59e0b', margin: '0 auto 1rem' }} />
            <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }}>{error}</p>
            <button className="btn btn-ghost" onClick={loadAll}>Reintentar</button>
          </div>
        </div>
      );
    }

    return (
      <div className="main-content" style={{ overflow: 'auto', padding: '2rem', background: 'var(--bg-primary)' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>

          {/* ═══ Header ═══ */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2rem' }}>
            <div>
              <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Shield size={24} style={{ color: 'var(--accent)' }} />
                Dashboard de Seguridad
              </h1>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Estadísticas en tiempo real · Actualizado por el motor de IA
              </p>
            </div>
            <button className="btn btn-ghost" onClick={loadAll} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <TrendingUp size={16} /> Actualizar
            </button>
          </div>

          {/* ═══ Stats Cards ═══ */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
            <StatCard icon={<FileText size={20} />} value={stats?.totalReports || 0} label="Total Reportes" gradient="linear-gradient(135deg, #6366f1, #8b5cf6)" />
            <StatCard icon={<Clock size={20} />} value={stats?.pendingReports || 0} label="Pendientes" gradient="linear-gradient(135deg, #f59e0b, #f97316)" />
            <StatCard icon={<CheckCircle size={20} />} value={stats?.verifiedReports || 0} label="Verificados" gradient="linear-gradient(135deg, #10b981, #059669)" />
            <StatCard icon={<XCircle size={20} />} value={stats?.rejectedReports || 0} label="Rechazados" gradient="linear-gradient(135deg, #ef4444, #dc2626)" />
            <StatCard icon={<Users size={20} />} value={stats?.totalUsers || 0} label="Usuarios" gradient="linear-gradient(135deg, #06b6d4, #0284c7)" />
            <StatCard icon={<MapPin size={20} />} value={stats?.totalZones || 0} label="Zonas" gradient="linear-gradient(135deg, #8b5cf6, #7c3aed)" />
          </div>

          {/* ═══ Grid de 2 columnas ═══ */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '2rem' }}>

            {/* Reportes por Tipo */}
            <div className="glass-card" style={{ padding: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '1rem' }}>
                <BarChart3 size={18} style={{ color: 'var(--accent)' }} />
                Reportes por Tipo
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {Object.entries(stats?.reportsByType || {}).map(([type, count]) => {
                  const maxCount = Math.max(...Object.values(stats?.reportsByType || {}).map(Number), 1);
                  return (
                    <div key={type}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                        <span style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>{TYPE_LABELS[type] || type}</span>
                        <span style={{ fontSize: '0.82rem', fontWeight: 700, color: TYPE_COLORS[type] || '#64748b' }}>{count}</span>
                      </div>
                      <div style={{ height: '8px', background: 'rgba(255,255,255,0.06)', borderRadius: '4px', overflow: 'hidden' }}>
                        <div style={{
                          height: '100%',
                          width: `${(count / maxCount) * 100}%`,
                          background: `linear-gradient(90deg, ${TYPE_COLORS[type] || '#64748b'}88, ${TYPE_COLORS[type] || '#64748b'})`,
                          borderRadius: '4px',
                          transition: 'width 0.6s ease'
                        }} />
                      </div>
                    </div>
                  );
                })}
                {Object.keys(stats?.reportsByType || {}).length === 0 &&
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', textAlign: 'center', padding: '1rem 0' }}>Sin datos todavía</p>
                }
              </div>
            </div>

            {/* Actividad Reciente (Timeline) */}
            <div className="glass-card" style={{ padding: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '1rem' }}>
                <Activity size={18} style={{ color: 'var(--accent)' }} />
                Actividad Reciente
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: 280, overflowY: 'auto' }}>
                {timeline.map((r) => (
                  <div 
                    key={r.id} 
                    onClick={() => { if (onReportClick) onReportClick(r); }}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '0.75rem',
                      padding: '0.6rem 0.75rem', borderRadius: '0.5rem',
                      background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)',
                      cursor: onReportClick ? 'pointer' : 'default',
                      transition: 'all 0.2s ease'
                  }}>
                    <div style={{
                      width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
                      background: TYPE_COLORS[r.incidentType] || '#64748b'
                    }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <p style={{ fontSize: '0.8rem', color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {r.description}
                      </p>
                      <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                        {TYPE_LABELS[r.incidentType] || r.incidentType} · {r.reportDate || 'Sin fecha'}
                      </p>
                    </div>
                    <span className={`badge badge-${r.status?.toLowerCase?.() || 'pending'}`} style={{ fontSize: '0.65rem', padding: '0.15rem 0.4rem' }}>
                      {r.status === 'VERIFIED' ? 'Verificado' : r.status === 'REJECTED' ? 'Rechazado' : 'Pendiente'}
                    </span>
                  </div>
                ))}
                {timeline.length === 0 &&
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', textAlign: 'center', padding: '1rem 0' }}>Sin reportes recientes</p>
                }
              </div>
            </div>
          </div>

          {/* ═══ Ranking de Zonas Peligrosas ═══ */}
          {isAuthenticated ? (
            <div className="glass-card" style={{ padding: '1.25rem' }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '1rem' }}>
                <AlertTriangle size={18} style={{ color: '#ef4444' }} />
                Zonas con Mayor Riesgo — Última Semana
              </h3>
              {dangerousZones.length === 0 ? (
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', textAlign: 'center', padding: '2rem 0' }}>
                  No hay suficientes datos esta semana para generar el ranking
                </p>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
                  {dangerousZones.map((zone) => (
                    <div key={zone.rank} style={{
                      display: 'flex', alignItems: 'center', gap: '1rem',
                      padding: '1rem', borderRadius: '0.75rem',
                      background: zone.rank === 1 ? 'rgba(239,68,68,0.08)' : 'rgba(255,255,255,0.03)',
                      border: `1px solid ${zone.rank === 1 ? 'rgba(239,68,68,0.2)' : 'rgba(255,255,255,0.06)'}`,
                      transition: 'transform 0.2s ease',
                    }}>
                      {/* Posición */}
                      <div style={{
                        width: 40, height: 40, borderRadius: '0.5rem', display: 'flex',
                        alignItems: 'center', justifyContent: 'center', fontWeight: 800,
                        fontSize: '1.1rem', flexShrink: 0,
                        background: RISK_COLORS[zone.riskLevel] + '22',
                        color: RISK_COLORS[zone.riskLevel],
                      }}>
                        #{zone.rank}
                      </div>
                      {/* Info */}
                      <div style={{ flex: 1 }}>
                        <p style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>{zone.areaName}</p>
                        <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          {zone.incidentCount} incidentes · Tipo común: {TYPE_LABELS[zone.mostCommonType] || zone.mostCommonType}
                        </p>
                      </div>
                      {/* Risk badge */}
                      <span style={{
                        padding: '0.2rem 0.6rem', borderRadius: '1rem', fontSize: '0.7rem', fontWeight: 600,
                        background: RISK_COLORS[zone.riskLevel] + '22',
                        color: RISK_COLORS[zone.riskLevel],
                      }}>
                        {RISK_LABELS[zone.riskLevel] || zone.riskLevel}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="glass-card" style={{
              padding: '2.5rem 1.25rem', textAlign: 'center',
              background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-color)',
              borderRadius: '0.75rem'
            }}>
              <AlertTriangle size={32} style={{ color: 'var(--text-muted)', margin: '0 auto 0.75rem auto' }} />
              <p style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>
                🔒 Ranking de Zonas de Riesgo
              </p>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', maxWidth: 300, margin: '0 auto' }}>
                Inicia sesión para ver el ranking semanal de zonas más peligrosas
              </p>
            </div>
          )}
        </div>
      </div>
    );
  }

  // ═══════════════ SECCIÓN SIDEBAR ═══════════════
  if (loading) {
    return (<div className="sidebar-content"><div className="empty-state"><span className="spinner" /><p>Cargando...</p></div></div>);
  }

  if (!stats) {
    return (<div className="sidebar-content"><div className="empty-state"><BarChart3 size={24} /><p>No se pudieron cargar las estadísticas</p><button className="btn btn-ghost btn-sm" onClick={loadAll}>Reintentar</button></div></div>);
  }

  const maxTypeCount = Math.max(...Object.values(stats.reportsByType || {}).map(Number), 1);

  return (
    <div className="sidebar-content">
      <div className="section-header">
        <h2>Dashboard</h2>
        <button className="btn btn-ghost btn-sm" onClick={loadAll}><TrendingUp size={14} /> Actualizar</button>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{stats.totalReports || 0}</div>
          <div className="stat-label"><FileText size={12} style={{ display: 'inline', marginRight: 4 }} />Total</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ background: 'linear-gradient(135deg, #f59e0b, #f97316)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>{stats.pendingReports || 0}</div>
          <div className="stat-label"><Clock size={12} style={{ display: 'inline', marginRight: 4 }} />Pendientes</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ background: 'linear-gradient(135deg, #10b981, #059669)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>{stats.verifiedReports || 0}</div>
          <div className="stat-label"><CheckCircle size={12} style={{ display: 'inline', marginRight: 4 }} />Verificados</div>
        </div>
        <div className="stat-card">
          <div className="stat-value" style={{ background: 'linear-gradient(135deg, #ef4444, #dc2626)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>{stats.rejectedReports || 0}</div>
          <div className="stat-label"><XCircle size={12} style={{ display: 'inline', marginRight: 4 }} />Rechazados</div>
        </div>
      </div>

      {/* Mini ranking en sidebar */}
      {isAuthenticated && (
        <div className="glass-card" style={{ marginTop: '0.5rem' }}>
          <div className="section-header"><h2 style={{ fontSize: '0.85rem' }}>🔴 Zonas de Riesgo</h2></div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '0.5rem' }}>
            {dangerousZones.slice(0, 3).map((zone) => (
              <div key={zone.rank} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.75rem' }}>
                <span style={{ fontWeight: 800, color: RISK_COLORS[zone.riskLevel], minWidth: 20 }}>#{zone.rank}</span>
                <span style={{ flex: 1, color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{zone.areaName}</span>
                <span style={{ fontWeight: 600, color: RISK_COLORS[zone.riskLevel] }}>{zone.incidentCount}</span>
              </div>
            ))}
            {dangerousZones.length === 0 && <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'center' }}>Sin datos</p>}
          </div>
        </div>
      )}

      <div className="glass-card">
        <div className="section-header"><h2 style={{ fontSize: '0.85rem' }}>Reportes por Tipo</h2></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', marginTop: '0.5rem' }}>
          {Object.entries(stats.reportsByType || {}).map(([type, count]) => (
            <div key={type}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>{TYPE_LABELS[type] || type}</span>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: TYPE_COLORS[type] || 'var(--text-muted)' }}>{count}</span>
              </div>
              <div style={{ height: '6px', background: 'rgba(255,255,255,0.06)', borderRadius: '3px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${(count / maxTypeCount) * 100}%`, background: TYPE_COLORS[type] || '#64748b', borderRadius: '3px', transition: 'width 0.5s ease' }} />
              </div>
            </div>
          ))}
          {Object.keys(stats.reportsByType || {}).length === 0 && <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', textAlign: 'center' }}>Sin datos</p>}
        </div>
      </div>
    </div>
  );
}

/* ═══ Componente reutilizable para las stat cards ═══ */
function StatCard({ icon, value, label, gradient }) {
  return (
    <div style={{
      padding: '1.25rem', borderRadius: '0.75rem',
      background: 'var(--bg-card)', border: '1px solid var(--border-color)',
      display: 'flex', flexDirection: 'column', gap: '0.5rem',
      transition: 'transform 0.2s ease, box-shadow 0.2s ease',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{
          width: 36, height: 36, borderRadius: '0.5rem', display: 'flex',
          alignItems: 'center', justifyContent: 'center',
          background: gradient + '22', color: 'var(--text-primary)',
        }}>
          {icon}
        </div>
      </div>
      <div style={{
        fontSize: '1.75rem', fontWeight: 800, lineHeight: 1,
        background: gradient, WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent', backgroundClip: 'text',
      }}>
        {value}
      </div>
      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 500 }}>{label}</div>
    </div>
  );
}
