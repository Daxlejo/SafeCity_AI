import { useState } from 'react';
import { ClipboardList, Calendar, User, Filter, AlertCircle, Shield } from 'lucide-react';

/**
 * Tabla de logs de auditoría para el panel admin.
 * 🔗 Preparado para consumir AdminLogService del Agente 1.
 */
export default function AdminAuditLog() {
  const [filterAction, setFilterAction] = useState('ALL');
  const [filterAdmin, setFilterAdmin] = useState('ALL');

  const isReady = true;
  const logs = [];

  const ACTION_LABELS = {
    APPROVE_REPORT: 'Aprobar reporte',
    REJECT_REPORT: 'Rechazar reporte',
    BAN_USER: 'Banear usuario',
    UNBAN_USER: 'Desbanear usuario',
    CHANGE_ROLE: 'Cambiar rol',
    DELETE_REPORT: 'Eliminar reporte',
    DELETE_USER: 'Eliminar usuario',
    OSINT_TOGGLE: 'Toggle OSINT',
    OSINT_TRIGGER: 'Trigger OSINT',
  };

  if (!isReady) {
    return (
      <div className="audit-log-panel">
        <div className="audit-log-header">
          <div className="audit-log-title">
            <ClipboardList size={20} />
            <h3>Registro de Auditoría</h3>
          </div>
        </div>
        <div className="audit-log-pending">
          <Shield size={40} style={{ opacity: 0.2 }} />
          <h4>Módulo en preparación</h4>
          <p>El registro de auditoría se activará automáticamente cuando el sistema de logging esté configurado.</p>
          <div className="audit-log-pending-features">
            {['Aprobación/rechazo de reportes', 'Ban/unban de usuarios', 'Cambios de rol', 'Acciones OSINT'].map((f, i) => (
              <div key={i} className="audit-log-feature">
                <AlertCircle size={14} />
                <span>{f}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="audit-log-panel">
      <div className="audit-log-header">
        <div className="audit-log-title">
          <ClipboardList size={20} />
          <h3>Registro de Auditoría</h3>
        </div>
        <span className="audit-log-count">{logs.length} registros</span>
      </div>
      <div className="audit-log-filters">
        <div className="audit-log-filter">
          <Filter size={12} />
          <select className="form-select" value={filterAction} onChange={(e) => setFilterAction(e.target.value)}>
            <option value="ALL">Todas las acciones</option>
            {Object.entries(ACTION_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="audit-log-filter">
          <User size={12} />
          <select className="form-select" value={filterAdmin} onChange={(e) => setFilterAdmin(e.target.value)}>
            <option value="ALL">Todos los admins</option>
          </select>
        </div>
      </div>
      <div className="audit-log-table-container">
        <table className="audit-log-table">
          <thead>
            <tr><th>Fecha</th><th>Admin</th><th>Acción</th><th>Objetivo</th><th>Detalles</th></tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr><td colSpan="5" className="audit-log-empty">Sin registros</td></tr>
            ) : logs.map((log) => (
              <tr key={log.id}>
                <td><Calendar size={12} /> {new Date(log.timestamp).toLocaleString('es-CO')}</td>
                <td>{log.adminName || log.adminId}</td>
                <td><span className="badge badge-other">{ACTION_LABELS[log.action] || log.action}</span></td>
                <td>{log.targetType} #{log.targetId}</td>
                <td className="audit-log-details">{log.details || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
