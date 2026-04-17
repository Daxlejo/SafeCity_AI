const fs = require('fs');
const postmanPath = 'c:/Users/Crist/OneDrive/Desktop/SafeCity_AI/SafeCity_AI.postman_collection.json';

try {
    let data = JSON.parse(fs.readFileSync(postmanPath, 'utf8'));
    let classificationFolder = data.item.find(i => i.name === 'Classification');
    
    if (classificationFolder) {
        
        const scriptCode = [
            '// 🪄 Script automático inyectado',
            'const loginReq = {',
            '  url: pm.environment.get("base_url") + "/api/v1/auth/login",',
            '  method: "POST",',
            '  header: "Content-Type:application/json",',
            '  body: { mode: "raw", raw: JSON.stringify({ identifier: "admin@safecity.com", password: "admin123" }) }',
            '};',
            '',
            'pm.sendRequest(loginReq, function (err, res) {',
            '  if (!err && res.code === 200) {',
            '    const token = res.json().token;',
            '    pm.environment.set("admin_token", token);',
            '    pm.environment.set("token", token);',
            '',
            '    const reportReq = {',
            '      url: pm.environment.get("base_url") + "/api/v1/reports",',
            '      method: "POST",',
            '      header: [',
            '        "Content-Type:application/json",',
            '        "Authorization:Bearer " + token',
            '      ],',
            '      body: {',
            '        mode: "raw",',
            '        raw: JSON.stringify({',
            '          description: "Me acaban de robar el celular a mano armada dos sujetos en moto, en la Calle 18.",',
            '          incidentType: "OTHER",',
            '          address: "Calle 18, Pasto",',
            '          source: "CITIZEN_TEXT",',
            '          latitude: 1.2136,',
            '          longitude: -77.2811',
            '        })',
            '      }',
            '    };',
            '',
            '    pm.sendRequest(reportReq, function (err2, res2) {',
            '      if (!err2 && res2.code === 201) {',
            '          pm.environment.set("current_report_id", res2.json().id);',
            '      }',
            '    });',
            '  }',
            '});'
        ];
        
        classificationFolder.event = [
            {
                listen: 'prerequest',
                script: {
                    type: 'text/javascript',
                    exec: scriptCode
                }
            }
        ];
        
        classificationFolder.item.forEach(req => {
            if (req.request && req.request.url && req.request.url.raw) {
                if (req.request.url.raw.includes('/ia/classify/')) {
                    req.request.url.raw = '{{base_url}}/api/v1/ia/classify/{{current_report_id}}';
                    req.request.url.path = ['api', 'v1', 'ia', 'classify', '{{current_report_id}}'];
                    if (!req.request.header) req.request.header = [];
                    const hasAuth = req.request.header.find(h => h.key === 'Authorization');
                    if (!hasAuth) {
                        req.request.header.push({ key: 'Authorization', value: 'Bearer {{token}}', type: 'text' });
                    }
                }
                
                if (req.request.url.raw.includes('/admin/reports/')) {
                    req.request.url.raw = '{{base_url}}/api/v1/admin/reports/{{current_report_id}}/status?status=VERIFIED';
                    req.request.url.path = ['api', 'v1', 'admin', 'reports', '{{current_report_id}}', 'status'];
                    if (!req.request.header) req.request.header = [];
                    const hasAuth = req.request.header.find(h => h.key === 'Authorization');
                    if (!hasAuth) {
                        req.request.header.push({ key: 'Authorization', value: 'Bearer {{admin_token}}', type: 'text' });
                    }
                }
            }
        });
        
        fs.writeFileSync(postmanPath, JSON.stringify(data, null, 4));
        console.log('✅ Cambios aplicados al Postman!');
    }
} catch (e) {
    console.error('Error:', e);
}
