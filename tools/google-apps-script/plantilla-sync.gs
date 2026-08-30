// Nutri-Tareas · sync bridge for the "Plantilla" button.
//
// Lets the app read and edit https://docs.google.com/document/d/1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA
// directly: GET returns every paragraph so the assistant can refer to one by index, POST
// replaces the text of the paragraphs it names and reformats them (see applyParagraphEdit_).
// Runs under whoever deploys it - no OAuth is needed on the Android side, only this Web App's
// (secret) URL, pasted into Ajustes.
//
// Deploy: open the Doc → Extensiones → Apps Script → replace the boilerplate with this file →
// guardar → Implementar → Nueva implementación → tipo "Aplicación web" → ejecutar como "Yo",
// acceso "Cualquier usuario" → autorizar → copiar la URL que termina en /exec y pegarla en
// Ajustes de la app. Volver a "Nueva implementación" (no solo guardar) cada vez que cambies
// este archivo, o los cambios no se publican.

var DOC_ID = '1gy_S-aNGET0DQDwp8hLKemsyMahGtfAFBH3gyEp80eA';

function doGet(e) {
  try {
    var snapshot = readDocument_();
    return jsonResponse_({ ok: true, paragraphs: snapshot.paragraphs, paragraphStyles: snapshot.styles });
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
        applyParagraphEdit_(paragraphs[index], String(edit.text || ''));
      }
    });
    doc.saveAndClose();
    var snapshot = readDocument_();
    return jsonResponse_({ ok: true, paragraphs: snapshot.paragraphs, paragraphStyles: snapshot.styles });
  } catch (err) {
    return jsonResponse_({ ok: false, error: String(err) });
  }
}

// Cada párrafo mantiene el estilo (Título/Subtítulo/Encabezado/Normal) que ya tenía en la
// plantilla - se usa para decidir el formato, así que el diseño no se pierde al reescribir texto:
//   Título (Título o Encabezado 1):    Arial 12, MAYÚSCULAS, negrita, centrado
//   Subtítulo (Subtítulo o Enc. 2/3):  Arial 12, tal como se escriba, alineado a la izquierda
//   Texto normal (todo lo demás):      Arial 10, justificado
// En los tres casos: sin cursiva/subrayado y en negro.
function applyParagraphEdit_(paragraph, newText) {
  var Heading = DocumentApp.ParagraphHeading;
  var heading = paragraph.getHeading();
  var isTitle = heading === Heading.TITLE || heading === Heading.HEADING1;
  var isSubtitle = heading === Heading.SUBTITLE || heading === Heading.HEADING2 || heading === Heading.HEADING3;

  paragraph.setText(isTitle ? newText.toUpperCase() : newText);

  var text = paragraph.editAsText();
  text.setFontFamily('Arial');
  text.setForegroundColor('#000000');
  text.setItalic(false);
  text.setUnderline(false);

  if (isTitle) {
    text.setFontSize(12);
    text.setBold(true);
    paragraph.setAlignment(DocumentApp.HorizontalAlignment.CENTER);
  } else if (isSubtitle) {
    text.setFontSize(12);
    text.setBold(false);
    paragraph.setAlignment(DocumentApp.HorizontalAlignment.LEFT);
  } else {
    text.setFontSize(10);
    text.setBold(false);
    paragraph.setAlignment(DocumentApp.HorizontalAlignment.JUSTIFY);
  }
}

// Returns both the plain text (what the assistant reads/edits) and each paragraph's style -
// "title"/"subtitle"/"normal", the same three looks applyParagraphEdit_ enforces - so the app's
// "Ver en vivo" preview can render a paragraph the way it actually looks in the Doc.
function readDocument_() {
  var doc = DocumentApp.openById(DOC_ID);
  var Heading = DocumentApp.ParagraphHeading;
  var paragraphs = [];
  var styles = [];
  doc.getBody().getParagraphs().forEach(function (p) {
    var heading = p.getHeading();
    var isTitle = heading === Heading.TITLE || heading === Heading.HEADING1;
    var isSubtitle = heading === Heading.SUBTITLE || heading === Heading.HEADING2 || heading === Heading.HEADING3;
    paragraphs.push(p.getText());
    styles.push(isTitle ? 'title' : (isSubtitle ? 'subtitle' : 'normal'));
  });
  return { paragraphs: paragraphs, styles: styles };
}

function jsonResponse_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
