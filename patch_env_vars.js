const fs = require('fs');
const postmanPath = './SafeCity_AI.postman_collection.json';
try {
    let raw = fs.readFileSync(postmanPath, 'utf8');
    raw = raw.replace(/pm\.environment\.get/g, 'pm.variables.get');
    raw = raw.replace(/pm\.environment\.set/g, 'pm.collectionVariables.set');
    fs.writeFileSync(postmanPath, raw);
    console.log('Fixed variable scope resolving!');
} catch (e) {
    console.error(e);
}
