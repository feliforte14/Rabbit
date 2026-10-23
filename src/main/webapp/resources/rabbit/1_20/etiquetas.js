/*
 * Etiquetas con QR (ADR-056): dibuja el codigo QR de cada etiqueta en el navegador con
 * qrcode.js (MIT, incluido en el proyecto: no se le pide nada a internet). El QR lleva la
 * direccion de escaneo.xhtml con el codigo de seguimiento del pedido. Sin JavaScript la
 * etiqueta igual lleva el codigo escrito.
 */
(function () {
    function dibujar() {
        document.querySelectorAll('.etiqueta-qr[data-url]').forEach(function (nodo) {
            if (nodo.firstChild || typeof qrcode === 'undefined') {
                return;
            }
            // 0 = tamano automatico; M = corrige hasta ~15% de dano (etiqueta rayada o gastada).
            var qr = qrcode(0, 'M');
            qr.addData(nodo.getAttribute('data-url'));
            qr.make();
            nodo.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
        });
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', dibujar);
    } else {
        dibujar();
    }
})();
