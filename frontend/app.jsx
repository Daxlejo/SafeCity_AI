const { useState, useEffect, useRef } = React;
const { Client } = window.StompJs;

// API configuration — apunta al backend desplegado en Render
const BACKEND_URL = 'https://safecity-ai-backend.onrender.com';
const WS_URL = 'wss://safecity-ai-backend.onrender.com/ws';

function App() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedLocation, setSelectedLocation] = useState(null);
  const [connected, setConnected] = useState(false);
  
  const [desc, setDesc] = useState('');
  const [type, setType] = useState('ROBBERY');
  
  const mapRef = useRef(null);
  const mapInstance = useRef(null);
  const markersRef = useRef([]);
  const selectedMarkerRef = useRef(null);
  const stompClient = useRef(null);

  useEffect(() => {
    // 1. Inicializar mapa de Leaflet puro integrando con React
    if (!mapInstance.current && mapRef.current) {
      mapInstance.current = L.map(mapRef.current).setView([1.2136, -77.2811], 13);
      L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; OSM'
      }).addTo(mapInstance.current);

      mapInstance.current.on('click', (e) => {
        const { lat, lng } = e.latlng;
        setSelectedLocation({ lat, lng });
        
        if (selectedMarkerRef.current) {
          selectedMarkerRef.current.setLatLng([lat, lng]);
        } else {
          selectedMarkerRef.current = L.marker([lat, lng], {
            icon: L.icon({
                iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
                shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
                iconAnchor: [12, 41]
            })
          }).addTo(mapInstance.current).bindPopup("Ubicación Seleccionada").openPopup();
        }
      });
    }

    // 2. Cargar reportes iniciales vía Axios
    axios.get(`${BACKEND_URL}/api/v1/reports?size=50&sort=reportDate&direction=DESC`)
      .then(res => {
        const data = res.data.content || [];
        setReports(data);
        setLoading(false);
        renderMarkers(data);
      })
      .catch(err => {
        console.error("Error fetching reports", err);
        setLoading(false);
      });

    // 3. Conectar WebSockets con STOMPJs
    stompClient.current = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });
    
    stompClient.current.onConnect = () => {
      setConnected(true);
      // Suscribirse a nuevos eventos globalmente
      stompClient.current.subscribe('/topic/reports/ALL', (message) => {
        const newReport = JSON.parse(message.body);
        setReports(prev => {
          const filtered = prev.filter(r => r.id !== newReport.id);
          const updated = [newReport, ...filtered];
          renderMarkers(updated);
          return updated;
        });
      });
    };
    
    stompClient.current.onDisconnect = () => setConnected(false);
    stompClient.current.activate();

    return () => {
      if (stompClient.current) stompClient.current.deactivate();
      if (mapInstance.current) {
        mapInstance.current.remove();
        mapInstance.current = null;
      }
    };
  }, []);

  const renderMarkers = (data) => {
    // Limpiar capa de markers anterior
    markersRef.current.forEach(m => m.remove());
    markersRef.current = [];
    
    data.forEach(r => {
      const m = L.marker([r.latitude, r.longitude], {
        icon: L.icon({
            iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
            shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
            iconAnchor: [12, 41]
        })
      }).bindPopup(`
        <strong>${r.incidentType}</strong><br/>
        ${r.description}
      `);
      if (mapInstance.current) {
          m.addTo(mapInstance.current);
          markersRef.current.push(m);
      }
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedLocation) {
        alert("Por favor selecciona una ubicación en el mapa");
        return;
    }
    
    const dto = {
      description: desc,
      incidentType: type,
      address: `Lat: ${selectedLocation.lat.toFixed(4)}, Lng: ${selectedLocation.lng.toFixed(4)}`, // Mock
      source: 'CITIZEN_TEXT',
      latitude: selectedLocation.lat,
      longitude: selectedLocation.lng
    };

    try {
      await axios.post(`${BACKEND_URL}/api/v1/reports`, dto, {
          headers: { 'Content-Type': 'application/json' }
      });
      setDesc('');
      setSelectedLocation(null);
      if (selectedMarkerRef.current) {
          selectedMarkerRef.current.remove();
          selectedMarkerRef.current = null;
      }
      // Se actualizará visualmente gracias al WebSocket callback
    } catch (err) {
      console.error("Error submitting report", err);
      alert("Error al enviar reporte al servidor");
    }
  };

  return (
    <div className="app-container">
      <div className="sidebar">
        <div className="sidebar-header">
          <h1>🛡️ SafeCity AI Demo</h1>
          <div className="live-indicator" style={{marginTop: '0.5rem'}}>
            <div className={`pulse`} style={{backgroundColor: connected ? 'var(--success)' : 'var(--error)'}}></div>
            <span style={{color: connected ? 'white' : '#fee2e2'}}>{connected ? 'En vivo (WebSocket)' : 'Desconectado'}</span>
          </div>
        </div>

        <div className="sidebar-content">
          <div className="create-report">
            <h2 style={{fontSize: '1.1rem', marginBottom: '1rem'}}>Nuevo Reporte</h2>
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label>Tipo de Incidente</label>
                <select value={type} onChange={e => setType(e.target.value)}>
                  <option value="ROBBERY">Robo</option>
                  <option value="ACCIDENT">Accidente</option>
                  <option value="TRAFFIC">Tráfico</option>
                  <option value="TRANSIT_OP">Operativo de Tránsito</option>
                  <option value="OTHER">Otro</option>
                </select>
              </div>
              <div className="form-group">
                <label>Descripción</label>
                <textarea 
                  rows="3" 
                  required 
                  value={desc} 
                  onChange={e => setDesc(e.target.value)}
                  placeholder="Detalles del incidente..."></textarea>
              </div>
              
              <div className="form-group">
                <label>Ubicación</label>
                <div style={{fontSize: '0.875rem', color: selectedLocation ? 'var(--success)' : 'var(--error)'}}>
                   {selectedLocation ? `Marcado: ${selectedLocation.lat.toFixed(3)}, ${selectedLocation.lng.toFixed(3)}` : 'Haz clic en el mapa para marcar'}
                </div>
              </div>

              <button type="submit" disabled={!selectedLocation}>Enviar Reporte Temprano</button>
            </form>
          </div>

          <hr style={{borderColor: 'var(--border)'}} />

          <div className="report-list">
             <h2 style={{fontSize: '1.1rem', marginBottom: '1rem'}}>Incidentes Recientes</h2>
             {loading ? <p>Cargando map...</p> : (
               <div className="report-list-container">
                 {reports.map((r, i) => (
                   <div className="report-card" key={`${r.id}-${i}`}>
                      <div className="report-header">
                         <span className={`badge ${r.incidentType.toLowerCase()}`}>
                           {r.incidentType}
                         </span>
                         <span className="report-meta">Reciente</span>
                      </div>
                      <div className="report-desc">{r.description}</div>
                      <div className="report-meta">📍 {r.address}</div>
                   </div>
                 ))}
                 {reports.length === 0 && <p style={{color: 'var(--text-muted)'}}>No hay reportes recientes.</p>}
               </div>
             )}
          </div>
        </div>
      </div>

      <div className="map-container">
        <div ref={mapRef} style={{ width: '100%', height: '100%' }}></div>
      </div>
    </div>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
