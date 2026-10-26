/*
 * Mapas con Leaflet (ADR-035): el de repartidores del personal y el del
 * seguimiento del cliente. Cada elemento con clase "mapa" trae sus puntos
 * en data-puntos, un JSON [{lat, lng, titulo, detalle}], que arma el bean
 * (los textos llegan escapados como atributo HTML y aca se ponen como
 * texto, nunca como HTML).
 *
 * Las teselas son de OpenStreetMap, con su atribucion. Sin puntos, el mapa
 * muestra la Ciudad de Buenos Aires.
 */
var RabbitMapa = (function () {
    var CENTRO_POR_DEFECTO = [-34.6158, -58.4333];

    function puntos(elemento) {
        try {
            return JSON.parse(elemento.getAttribute('data-puntos') || '[]');
        } catch (e) {
            return [];
        }
    }

    function popup(punto) {
        var caja = document.createElement('div');
        var titulo = document.createElement('strong');
        titulo.textContent = punto.titulo || '';
        caja.appendChild(titulo);
        if (punto.detalle) {
            caja.appendChild(document.createElement('br'));
            caja.appendChild(document.createTextNode(punto.detalle));
        }
        return caja;
    }

    function dibujar(elemento) {
        if (typeof L === 'undefined') {
            elemento.textContent = 'No se pudo cargar el mapa.';
            return;
        }
        var lista = puntos(elemento);
        var mapa = elemento._rabbitMapa;
        if (!mapa) {
            mapa = L.map(elemento, { scrollWheelZoom: false }).setView(CENTRO_POR_DEFECTO, 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; colaboradores de <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            }).addTo(mapa);
            elemento._rabbitMapa = mapa;
            elemento._rabbitCapa = L.layerGroup().addTo(mapa);
            elemento._rabbitPrimeraVez = true;
        }
        var capa = elemento._rabbitCapa;
        capa.clearLayers();
        var limites = [];
        lista.forEach(function (p) {
            L.circleMarker([p.lat, p.lng], {
                radius: 9, weight: 3, color: '#ffffff', fillColor: p.color || '#c2410c', fillOpacity: 1
            }).bindPopup(popup(p)).bindTooltip(p.titulo || '').addTo(capa);
            limites.push([p.lat, p.lng]);
        });
        // Solo la primera vez: despues no se le mueve el mapa a quien lo esta mirando.
        if (elemento._rabbitPrimeraVez && limites.length) {
            mapa.fitBounds(limites, { maxZoom: 15, padding: [30, 30] });
            elemento._rabbitPrimeraVez = false;
        }
    }

    function dibujarTodos() {
        document.querySelectorAll('.mapa').forEach(dibujar);
    }

    // El mapa del personal se actualiza por ajax: el bean re-renderiza el
    // contenedor de datos y aca se copian al mapa (onevent de f:ajax).
    function alActualizar(evento) {
        if (evento.status !== 'success') {
            return;
        }
        var datos = document.getElementById('datosMapa');
        var mapa = document.getElementById('mapaRepartidores');
        if (datos && mapa) {
            mapa.setAttribute('data-puntos', datos.getAttribute('data-puntos'));
            dibujar(mapa);
            var resumen = document.getElementById('resumenMapa');
            if (resumen) {
                resumen.textContent = datos.getAttribute('data-resumen') || '';
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', dibujarTodos);
    } else {
        dibujarTodos();
    }

    return { dibujar: dibujar, alActualizar: alActualizar };
})();
