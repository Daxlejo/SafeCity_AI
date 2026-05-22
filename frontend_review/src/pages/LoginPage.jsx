import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { authAPI } from '../services/api';
import { Shield, AlertCircle, Mail, Lock, User, CreditCard, Sun, Moon, ArrowLeft } from 'lucide-react';

export default function LoginPage({ onBack, initialView, initialToken }) {
  const { login, register } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [view, setView] = useState(initialView || 'login');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const [form, setForm] = useState({
    identifier: '', password: '', name: '', email: '', cedula: '',
    token: initialToken || '', newPassword: ''
  });

  const handleChange = (e) => { setForm({ ...form, [e.target.name]: e.target.value }); setError(''); setSuccess(''); };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      if (view === 'register') {
        await register({ name: form.name, email: form.email, cedula: form.cedula, password: form.password });
      } else if (view === 'login') {
        await login(form.identifier, form.password);
      } else if (view === 'forgot') {
        // Forgot password call
        await authAPI.forgotPassword(form.email);
        setSuccess('Se ha enviado un correo electrónico con las instrucciones para restablecer la contraseña.');
        setTimeout(() => setView('reset'), 2000);
      } else if (view === 'reset') {
        // Reset password call
        await authAPI.resetPassword(form.token, form.newPassword);
        setSuccess('Contraseña actualizada correctamente. Iniciando sesión...');
        setTimeout(() => setView('login'), 2000);
      }
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.error || 'Error de conexión');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          {onBack && (
            <button className="btn btn-ghost btn-sm" onClick={onBack}>
              <ArrowLeft size={14} /> Volver
            </button>
          )}
          <div style={{ marginLeft: 'auto' }}>
            <button className="theme-toggle" onClick={toggleTheme}>
              {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
            </button>
          </div>
        </div>

        <div className="auth-logo"><Shield size={32} /></div>
        <h1>
          {view === 'register' ? 'Crear Cuenta' :
            view === 'forgot' ? 'Recuperar Contraseña' :
              view === 'reset' ? 'Nueva Contraseña' : 'Iniciar Sesión'}
        </h1>
        <p className="auth-subtitle">
          {view === 'register' ? 'Únete a la red de seguridad ciudadana' :
            view === 'forgot' ? 'Ingresa tu correo para restablecer la contraseña' :
              view === 'reset' ? 'Ingresa tu nueva contraseña' :
                'Plataforma de seguridad ciudadana'}
        </p>

        {error && <div className="form-error"><AlertCircle size={16} />{error}</div>}
        {success && <div className="form-error" style={{ background: 'var(--success-bg)', color: 'var(--success)' }}><Shield size={16} />{success}</div>}

        <form onSubmit={handleSubmit}>
          {view === 'register' && (
            <>
              <div className="form-group">
                <label>Nombre completo</label>
                <div style={{ position: 'relative' }}>
                  <User size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input type="text" name="name" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="Tu nombre" value={form.name} onChange={handleChange} required />
                </div>
              </div>
              <div className="form-group">
                <label>Email</label>
                <div style={{ position: 'relative' }}>
                  <Mail size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input type="email" name="email" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="correo@ejemplo.com" value={form.email} onChange={handleChange} required />
                </div>
              </div>
              <div className="form-group">
                <label>Cédula</label>
                <div style={{ position: 'relative' }}>
                  <CreditCard size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input type="text" name="cedula" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="Número de cédula" value={form.cedula} onChange={handleChange} required pattern="[0-9]{6,11}" title="6 a 11 dígitos" />
                </div>
              </div>
            </>
          )}

          {view === 'login' && (
            <div className="form-group">
              <label>Email o Cédula</label>
              <div style={{ position: 'relative' }}>
                <Mail size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                <input type="text" name="identifier" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="correo@ejemplo.com o cédula" value={form.identifier} onChange={handleChange} required />
              </div>
            </div>
          )}

          {view === 'forgot' && (
            <div className="form-group">
              <label>Email registrado</label>
              <div style={{ position: 'relative' }}>
                <Mail size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                <input type="email" name="email" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="correo@ejemplo.com" value={form.email} onChange={handleChange} required />
              </div>
            </div>
          )}


          {view === 'reset' && (
            <>
              {!form.token && (
                <div className='form-error'>
                  <AlertCircle size={16} />
                  Enlace invalido. Solicita un nuevo correo de recuperación.
                </div>
              )}
              <div className="form-group">
                <label>Nueva Contraseña</label>
                <div style={{ position: 'relative' }}>
                  <Lock size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input type="password" name="newPassword" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="••••••••" value={form.newPassword} onChange={handleChange} required minLength={6} />
                </div>
              </div>
            </>
          )}

          {(view === 'login' || view === 'register') && (
            <div className="form-group">
              <label>Contraseña</label>
              <div style={{ position: 'relative' }}>
                <Lock size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                <input type="password" name="password" className="form-input" style={{ paddingLeft: '2.25rem', width: '100%' }} placeholder="••••••••" value={form.password} onChange={handleChange} required minLength={6} />
              </div>
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-full" disabled={loading} style={{ marginTop: '0.5rem' }}>
            {loading && <span className="spinner" />}
            {loading ? 'Procesando...' :
              view === 'register' ? 'Registrarse' :
                view === 'forgot' ? 'Enviar Token' :
                  view === 'reset' ? 'Actualizar Contraseña' : 'Ingresar'}
          </button>
        </form>

        {view === 'login' && (
          <div style={{ textAlign: 'center', marginTop: '1rem' }}>
            <button className="btn btn-ghost btn-sm" onClick={() => { setView('forgot'); setError(''); setSuccess(''); }}>
              ¿Olvidaste tu contraseña?
            </button>
          </div>
        )}

        <div className="auth-toggle">
          {view === 'register' ? '¿Ya tienes cuenta? ' :
            view === 'login' ? '¿No tienes cuenta? ' :
              '¿Recordaste tu contraseña? '}
          <button onClick={() => { setView(view === 'login' ? 'register' : 'login'); setError(''); setSuccess(''); }}>
            {view === 'login' ? 'Regístrate' : 'Inicia sesión'}
          </button>
        </div>
      </div>
    </div>
  );
}
