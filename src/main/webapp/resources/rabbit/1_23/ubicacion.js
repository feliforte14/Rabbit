/*
 * Ubicacion del repartidor (ADR-035): mientras la pantalla "Mis entregas"
 * esta abierta y tiene entregas en curso, manda su posicion a Rabbit cada
 * 30 segundos como mucho. La usa el mapa del personal y el seguimiento del
 * cliente.
 *
 * Va por el mismo formulario JSF (ajax con el boton oculto de
 * formUbicacion), asi viaja con la sesion y el token de la vista como
 * cualquier otra accion. El navegador solo da la ubicacion por HTTPS y si
 * el repartidor la permite.
 */
(function () {
    var INTERVALO_MS = 30000;
    var form = document.getElementById('formUbicacion');
    var estado = document.getElementById('ubicacionEstado');
    if (!form || !navigator.geolocation) {
        if (estado) {
            estado.textContent = 'Este navegador no puede compartir la ubicación.';
        }
        return;
    }
    var latitud = document.getElementById('formUbicacion:latitud');
    var longitud = document.getElementById('formUbicacion:longitud');
    var enviar = document.getElementById('formUbicacion:enviar');
    var ultimoEnvio = 0;

    function aviso(texto) {
        if (estado) {
            estado.textContent = texto;
        }
    }

    navigator.geolocation.watchPosition(function (posicion) {
        var ahora = Date.now();
        if (ahora - ultimoEnvio < INTERVALO_MS) {
            return;
        }
        ultimoEnvio = ahora;
        latitud.value = posicion.coords.latitude.toFixed(6);
        longitud.value = posicion.coords.longitude.toFixed(6);
        enviar.click();
        aviso('Compartiendo tu ubicación con Rabbit mientras tengas entregas en curso (última: '
            + new Date(ahora).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' }) + ').');
    }, function (error) {
        aviso(error.code === error.PERMISSION_DENIED
            ? 'No diste permiso para usar la ubicación: Rabbit y el cliente no te van a ver en el mapa.'
            : 'No se pudo obtener la ubicación. Se va a reintentar sola.');
    }, { enableHighAccuracy: true, maximumAge: 15000, timeout: 20000 });
})();
