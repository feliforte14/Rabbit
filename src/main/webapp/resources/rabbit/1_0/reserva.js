/*
 * reserva.js - Capa de Presentacion (recurso JSF versionado)
 *
 * Hace que la cuenta regresiva de la reserva avance sola.
 *
 * El servidor calcula segundosRestantes UNA VEZ, cuando renderiza la
 * pagina: es un numero congelado. Sin esto, el usuario ve "quedan 272 s"
 * fijo hasta que haga otra accion, lo que hace parecer que la reserva no
 * vence nunca.
 *
 * La cuenta la lleva el navegador a partir de ese valor inicial. No hace
 * falta consultar al servidor: el vencimiento ya esta decidido, solo hay
 * que mostrarlo pasar. Cuando llega a cero se marca VENCIDA en rojo — el
 * estado real en la base lo corrige BarredorDeReservas dentro del minuto.
 */
(function () {
    "use strict";

    var timer = null;

    function arrancarCuenta() {
        // Si habia una cuenta de un render anterior, cortarla: si no,
        // quedan dos intervalos pisandose sobre el mismo elemento.
        if (timer !== null) {
            clearInterval(timer);
            timer = null;
        }

        var el = document.getElementById("cuentaRegresiva");
        if (!el) {
            return; // no hay reserva en curso en esta pagina
        }

        var restantes = parseInt(el.getAttribute("data-segundos"), 10);
        if (isNaN(restantes)) {
            return;
        }

        function pintar() {
            if (restantes <= 0) {
                el.textContent = "VENCIDA";
                el.className = "cuenta-regresiva vencida";
                return true; // terminado
            }
            var min = Math.floor(restantes / 60);
            var seg = restantes % 60;
            el.textContent = min + ":" + (seg < 10 ? "0" : "") + seg;
            // Ultimo minuto: se resalta en rojo para que se note.
            el.className = "cuenta-regresiva" + (restantes <= 60 ? " urgente" : "");
            return false;
        }

        if (pintar()) {
            return;
        }
        timer = setInterval(function () {
            restantes--;
            if (pintar()) {
                clearInterval(timer);
                timer = null;
            }
        }, 1000);
    }

    // Carga inicial de la pagina.
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", arrancarCuenta);
    } else {
        arrancarCuenta();
    }

    // Y de nuevo despues de cada peticion ajax de JSF, porque al
    // re-renderizar el panel se reemplaza el elemento del DOM y la cuenta
    // anterior quedaria apuntando a un nodo que ya no existe.
    // Mojarra 4 expone "faces"; se contempla "jsf" por compatibilidad.
    var api = window.faces || window.jsf;
    if (api && api.ajax && api.ajax.addOnEvent) {
        api.ajax.addOnEvent(function (data) {
            if (data.status === "success") {
                arrancarCuenta();
            }
        });
    }
})();
