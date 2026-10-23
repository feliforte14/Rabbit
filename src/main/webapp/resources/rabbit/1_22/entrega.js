/*
 * Entrega con prueba reforzada (ADR-055): al abrir la ventana "Entregar pedido",
 * el celular toma la ubicacion del repartidor y la deja en los campos ocultos del
 * formulario, y se limpia el codigo de la vez anterior. Junto con la foto del
 * remito y el codigo que dice quien recibe, queda registrado donde se entrego.
 *
 * La ubicacion es opcional: si el repartidor no da permiso (o el navegador no la
 * tiene) la entrega se registra igual, sin ella. El navegador solo la da por HTTPS.
 */
(function () {
    var campo = function (id) { return document.getElementById('formEntrega:' + id); };
    var estado = document.getElementById('ubicacionEntrega');

    function aviso(texto) {
        if (estado) { estado.textContent = texto; }
    }

    function tomarUbicacion() {
        ['latitud', 'longitud', 'precision'].forEach(function (n) { if (campo(n)) { campo(n).value = ''; } });
        if (!navigator.geolocation) {
            aviso('Este navegador no puede dar la ubicación: la entrega se registra sin ella.');
            return;
        }
        aviso('Tomando tu ubicación…');
        navigator.geolocation.getCurrentPosition(function (p) {
            campo('latitud').value = p.coords.latitude.toFixed(6);
            campo('longitud').value = p.coords.longitude.toFixed(6);
            campo('precision').value = Math.round(p.coords.accuracy);
            aviso('Ubicación tomada (precisión de unos ' + Math.round(p.coords.accuracy) + ' m): queda guardada con la entrega.');
        }, function () {
            aviso('No se pudo tomar la ubicación: la entrega se registra sin ella.');
        }, { enableHighAccuracy: true, maximumAge: 10000, timeout: 15000 });
    }

    document.addEventListener('rabbit:entrega', function () {
        if (campo('codigo')) { campo('codigo').value = ''; }
        tomarUbicacion();
    });
})();
