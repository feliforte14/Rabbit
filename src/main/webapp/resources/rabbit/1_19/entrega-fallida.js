/*
 * Ventana "No se pudo entregar" del repartidor (ADR-045).
 *
 * 1. Ubicacion: al abrirla, le pide al celular donde esta en ese momento
 *    y la manda con el formulario (campos ocultos). Si no la da (sin
 *    permiso, sin senal, computadora), Rabbit usa la ultima que compartio
 *    mientras repartia, si es de los ultimos minutos. La visita se registra
 *    igual: la ubicacion es evidencia, no un requisito.
 * 2. Segun el motivo elegido: avisa que pasa con el pedido (si cuenta como
 *    visita, si vuelve al comercio) y marca como obligatorios el comentario
 *    o la foto cuando ese motivo los pide. El servidor valida lo mismo.
 * 3. Las fotos aparecen de a una: la segunda cuando ya hay primera.
 */
(function () {
    var form = document.getElementById('formDevolucion');
    if (!form) {
        return;
    }
    var campo = function (id) { return document.getElementById('formDevolucion:' + id); };
    var motivo = campo('motivo');
    var detalle = campo('detalle');
    var fotos = [campo('foto1'), campo('foto2'), campo('foto3')];
    var consecuencia = document.getElementById('consecuenciaMotivo');
    var estadoUbicacion = document.getElementById('ubicacionVisita');
    var etiquetaComentario = form.querySelector('label[for="formDevolucion:detalle"]');
    var etiquetaFotos = document.getElementById('etiquetaFotos');
    var maximo = parseInt(consecuencia.getAttribute('data-maximo'), 10) || 2;
    var visita = { enCamino: false, visitas: 0 };

    var reglas = {};
    form.querySelectorAll('[data-motivo]').forEach(function (n) {
        reglas[n.getAttribute('data-motivo')] = {
            visita: n.getAttribute('data-visita') === 'true',
            devuelve: n.getAttribute('data-devuelve') === 'true',
            comentario: n.getAttribute('data-pide-comentario') === 'true',
            foto: n.getAttribute('data-pide-foto') === 'true'
        };
    });

    function queVaAPasar(r) {
        if (!r) {
            return '';
        }
        if (r.devuelve && (visita.enCamino || !r.visita)) {
            return 'El pedido vuelve al comercio: no se vuelve a intentar.';
        }
        if (r.visita && visita.enCamino) {
            var numero = visita.visitas + 1;
            return numero >= maximo
                ? 'Es la visita ' + numero + ' de ' + maximo + ': el pedido vuelve al comercio.'
                : 'Cuenta como visita ' + numero + ' de ' + maximo + '. Rabbit se lo asigna a otro repartidor y le avisa al cliente.';
        }
        return 'No le cuenta una visita al cliente: Rabbit se lo asigna a otro repartidor.';
    }

    function actualizar() {
        var r = reglas[motivo.value];
        consecuencia.textContent = queVaAPasar(r);
        var pideComentario = !!(r && r.comentario);
        var pideFoto = !!(r && r.foto);
        detalle.required = pideComentario;
        etiquetaComentario.classList.toggle('obligatorio', pideComentario);
        etiquetaComentario.textContent = pideComentario ? 'Comentario (obligatorio)' : 'Comentario (opcional)';
        fotos[0].required = pideFoto;
        etiquetaFotos.textContent = pideFoto ? 'Fotos del daño (al menos 1, hasta 3)' : 'Fotos (hasta 3, opcionales)';
    }

    function mostrarFotos() {
        // Se ve la siguiente recien cuando la anterior tiene archivo.
        for (var i = 1; i < fotos.length; i++) {
            var anteriorConArchivo = fotos[i - 1].files && fotos[i - 1].files.length > 0;
            fotos[i].hidden = !anteriorConArchivo && !(fotos[i].files && fotos[i].files.length > 0);
        }
    }

    function tomarUbicacion() {
        campo('latitud').value = '';
        campo('longitud').value = '';
        campo('precision').value = '';
        if (!navigator.geolocation) {
            estadoUbicacion.textContent = 'Este navegador no da la ubicación: se usa la última que compartiste.';
            return;
        }
        estadoUbicacion.textContent = 'Tomando tu ubicación…';
        navigator.geolocation.getCurrentPosition(function (p) {
            campo('latitud').value = p.coords.latitude.toFixed(6);
            campo('longitud').value = p.coords.longitude.toFixed(6);
            campo('precision').value = Math.round(p.coords.accuracy);
            estadoUbicacion.textContent = 'Ubicación tomada (±' + Math.round(p.coords.accuracy) + ' m). Queda como prueba de la visita.';
        }, function (error) {
            estadoUbicacion.textContent = error.code === error.PERMISSION_DENIED
                ? 'No diste permiso para la ubicación: se usa la última que compartiste, si es reciente.'
                : 'No se pudo tomar la ubicación: se usa la última que compartiste, si es reciente.';
        }, { enableHighAccuracy: true, maximumAge: 30000, timeout: 15000 });
    }

    motivo.addEventListener('change', actualizar);
    fotos.forEach(function (f) { f.addEventListener('change', mostrarFotos); });

    document.addEventListener('rabbit:entrega-fallida', function (evento) {
        visita = evento.detail || visita;
        actualizar();
        mostrarFotos();
        tomarUbicacion();
    });

    actualizar();
    mostrarFotos();
})();
