// Nutri-Tareas · sync bridge for the "Plantilla" button.
//
// Lets the app read and edit https://docs.google.com/document/d/1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA
// directly: GET returns every paragraph so the assistant can refer to one by index, POST
// replaces the text of the paragraphs it names. Runs under whoever deploys it - no OAuth is
// needed on the Android side, only this Web App's (secret) URL, pasted into Ajustes.
//
// Deploy: open the Doc → Extensiones → Apps Script → replace the boilerplate with this file →
// guardar → Implementar → Nueva implementación → tipo "Aplicación web" → ejecutar como "Yo",
// acceso "Cualquier usuario" → autorizar → copiar la URL que termina en /exec y pegarla en
// Ajustes de la app. Volver a "Nueva implementación" (no solo guardar) cada vez que cambies
// este archivo, o los cambios no se publican.

var DOC_ID = '1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA';

function doGet(e) {
  try {
    return jsonResponse_({ ok: true, paragraphs: readParagraphs_() });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  try {
    var payload = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    var edits = payload.edits || [];
    var doc = DocumentApp.openById(DOC_ID);
    var paragraphs = doc.getBody().getParagraphs();
    edits.forEach(function (edit) {
      var index = edit && edit.index;
      if (typeof index === 'number' && index >= 0 && index < paragraphs.length) {
        paragraphs[index].setText(String(edit.text || ''));
      }
    });
    doc.saveAndClose();
    return jsonResponse_({ ok: true, paragraphs: readParagraphs_() });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

function readParagraphs_() {
  var doc = DocumentApp.openById(DOC_ID);
  return doc.getBody().getParagraphs().map(function (p) {
    return p.getText();
  });
}

function jsonResponse_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
