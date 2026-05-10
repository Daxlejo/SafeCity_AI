import { useState, useEffect, useCallback } from 'react';
import { osintAPI } from '../services/api';
import {
  Newspaper, Calendar, ExternalLink, AlertCircle,
  ChevronLeft, ChevronRight, Shield
} from 'lucide-react';

const TYPE_LABELS = {
  ROBBERY: 'Robo', ACCIDENT: 'Accidente', TRAFFIC: 'Tráfico',
  TRANSIT_OP: 'Op. Tránsito', OTHER: 'Otro'
};

/**
 * Feed paginado de noticias OSINT.
 * Muestra artículos detectados por el módulo OSINT que no pudieron geolocalizarse.
 * Diseño: tarjetas glassmorphism con título, fuente, fecha y trust score.
 */
export default function OsintNewsFeed() {
  const [articles, setArticles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadNews = useCallback(async (pageNum) => {
    setLoading(true);
    try {
      const res = await osintAPI.getNews(pageNum, 10);
      const data = res.data;
      setArticles(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
      setPage(pageNum);
    } catch (err) {
      console.error('Error cargando noticias OSINT:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadNews(0); }, [loadNews]);

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleDateString('es-CO', {
        day: 'numeric', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return dateStr;
    }
  };

  const getTrustColor = (score) => {
    if (score >= 70) return 'var(--success)';
    if (score >= 40) return 'var(--warning)';
    return 'var(--error)';
  };

  if (loading && articles.length === 0) {
    return (
      <div className="osint-news-loading">
        <span className="spinner" />
        <p>Cargando noticias...</p>
      </div>
    );
  }

  return (
    <div className="osint-news-feed">
      <div className="osint-news-header">
        <div className="osint-news-title">
          <Newspaper size={20} />
          <h3>Noticias OSINT</h3>
        </div>
        <span className="osint-news-count">{totalElements} artículos</span>
      </div>

      {articles.length === 0 ? (
        <div className="osint-news-empty">
          <Shield size={32} style={{ opacity: 0.3 }} />
          <p>No hay noticias de seguridad detectadas aún</p>
        </div>
      ) : (
        <div className="osint-news-list">
          {articles.map((article) => (
            <div key={article.id} className="osint-news-card">
              <div className="osint-news-card-header">
                <span className={`badge badge-${article.incidentType?.toLowerCase()}`}>
                  {TYPE_LABELS[article.incidentType] || article.incidentType}
                </span>
                {article.trustScore != null && (
                  <span
                    className="osint-news-trust"
                    style={{ color: getTrustColor(article.trustScore) }}
                  >
                    {article.trustScore.toFixed(0)}% confianza
                  </span>
                )}
              </div>

              <h4 className="osint-news-card-title">{article.title}</h4>

              {article.summary && article.summary !== article.title && (
                <p className="osint-news-card-summary">{article.summary}</p>
              )}

              <div className="osint-news-card-meta">
                <span className="osint-news-meta-item">
                  <Calendar size={12} />
                  {formatDate(article.createdAt)}
                </span>
                {article.sourceType && (
                  <span className="osint-news-meta-item">
                    {article.sourceType === 'SOCIAL_MEDIA' ? '📱 Redes sociales' : '📰 Prensa'}
                  </span>
                )}
                {article.sourceUrl && (
                  <a
                    href={article.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="osint-news-meta-link"
                  >
                    <ExternalLink size={12} /> Fuente
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Paginación */}
      {totalPages > 1 && (
        <div className="osint-news-pagination">
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => loadNews(page - 1)}
            disabled={page === 0 || loading}
          >
            <ChevronLeft size={14} /> Anterior
          </button>
          <span className="osint-news-page-info">
            Página {page + 1} de {totalPages}
          </span>
          <button
            className="btn btn-ghost btn-sm"
            onClick={() => loadNews(page + 1)}
            disabled={page >= totalPages - 1 || loading}
          >
            Siguiente <ChevronRight size={14} />
          </button>
        </div>
      )}
    </div>
  );
}
