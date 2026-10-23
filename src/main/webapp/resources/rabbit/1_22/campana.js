/*
 * campana.js - Capa de Presentacion
 *
 * Mejora la campana de notificaciones de la cabecera (ver template.xhtml y
 * estilos.css). La campana funciona sin JavaScript: es un <details> que se
 * abre y se cierra solo. Este script suma:
 *  - el contador de avisos nuevos sobre la campana;
 *  - marcar los avisos como vistos al abrirla (el contador vuelve a 0);
 *  - cerrar con Escape o al tocar fuera del panel.
 *
 * "Nuevo" = un aviso con un id mayor al último que ese usuario vio en este
 * navegador. Se guarda en localStorage, un valor por usuario y por tipo de
 * aviso (los avisos a comercios y los avisos a quien recibe tienen ids
 * separados). Si el navegador no deja usar localStorage (modo privado),
 * todo sigue andando: los avisos se cuentan como nuevos hasta abrir el
 * panel y vuelven a contarse en la próxima pantalla.
 */
(function () {
    document.addEventListener("DOMContentLoaded", function () {
        var campana = document.querySelector(".campana");
        if (!campana) {
            return;
        }
        var contador = campana.querySelector(".campana-contador");
        var items = Array.prototype.slice.call(campana.querySelectorAll(".campana-item[data-id]"));
        var clave = "rabbit.campana." + (campana.getAttribute("data-usuario") || "");

        function leer() {
            try {
                return JSON.parse(window.localStorage.getItem(clave)) || {};
            } catch (e) {
                return {};
            }
        }

        function guardar(vistos) {
            try {
                window.localStorage.setItem(clave, JSON.stringify(vistos));
            } catch (e) {
                // sin localStorage: no se recuerda entre pantallas, y está bien
            }
        }

        function esNuevo(item, vistos) {
            var tipo = item.getAttribute("data-tipo");
            return Number(item.getAttribute("data-id")) > Number(vistos[tipo] || 0);
        }

        function pintar() {
            var vistos = leer();
            var nuevos = 0;
            items.forEach(function (item) {
                var nuevo = esNuevo(item, vistos);
                item.classList.toggle("nuevo", nuevo);
                if (nuevo) {
                    nuevos++;
                }
            });
            if (contador) {
                contador.textContent = nuevos > 9 ? "9+" : String(nuevos);
                contador.hidden = nuevos === 0;
            }
            campana.querySelector(".campana-boton").setAttribute(
                "aria-label",
                nuevos === 0 ? "Notificaciones" : "Notificaciones, " + nuevos + (nuevos === 1 ? " nueva" : " nuevas")
            );
        }

        function marcarComoVistos() {
            var vistos = leer();
            items.forEach(function (item) {
                var tipo = item.getAttribute("data-tipo");
                vistos[tipo] = Math.max(Number(vistos[tipo] || 0), Number(item.getAttribute("data-id")));
            });
            guardar(vistos);
        }

        pintar();

        // Al abrir, los avisos nuevos siguen resaltados mientras se leen; se marcan
        // como vistos al abrir (el contador baja a 0) y se apagan al cerrar.
        campana.addEventListener("toggle", function () {
            if (campana.open) {
                marcarComoVistos();
                if (contador) {
                    contador.hidden = true;
                }
            } else {
                pintar();
            }
        });

        document.addEventListener("keydown", function (evento) {
            if (evento.key === "Escape" && campana.open) {
                campana.open = false;
                campana.querySelector(".campana-boton").focus();
            }
        });

        document.addEventListener("click", function (evento) {
            if (campana.open && !campana.contains(evento.target)) {
                campana.open = false;
            }
        });
    });
})();
