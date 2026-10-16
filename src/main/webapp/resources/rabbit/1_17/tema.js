/*
 * tema.js - Capa de Presentacion
 *
 * Tres temas visuales que se cambian en el momento, sin recargar la
 * página: "normal" (el de la marca, por defecto), "claro" y "oscuro".
 * Cada tema es un juego de variables CSS (ver el final de estilos.css);
 * este script solo pone data-tema en <html>.
 *
 * Se carga en el <head> y corre ANTES de que se pinte la página: así el
 * tema elegido se aplica desde el primer cuadro, sin un parpadeo del tema
 * normal. La elección se guarda en el navegador (localStorage) y vale para
 * todas las pantallas; si el navegador no deja guardar (modo privado), el
 * tema igual cambia, pero dura hasta recargar.
 *
 * También marca <html class="js">: el CSS solo esconde el menú del
 * celular cuando hay JavaScript para volver a abrirlo.
 */
(function () {
    var CLAVE = "rabbit.tema";
    var TEMAS = ["normal", "claro", "oscuro"];
    var raiz = document.documentElement;

    function leer() {
        try {
            var guardado = window.localStorage.getItem(CLAVE);
            return TEMAS.indexOf(guardado) >= 0 ? guardado : "normal";
        } catch (e) {
            return "normal";
        }
    }

    function aplicar(tema) {
        raiz.setAttribute("data-tema", tema);
        document.querySelectorAll(".tema-selector button[data-tema]").forEach(function (boton) {
            boton.setAttribute("aria-pressed", String(boton.getAttribute("data-tema") === tema));
        });
    }

    function elegir(tema) {
        aplicar(tema);
        try {
            window.localStorage.setItem(CLAVE, tema);
        } catch (e) {
            // Sin almacenamiento: el tema queda aplicado en esta página.
        }
    }

    raiz.classList.add("js");
    aplicar(leer());

    document.addEventListener("DOMContentLoaded", function () {
        aplicar(raiz.getAttribute("data-tema"));
        document.addEventListener("click", function (evento) {
            var boton = evento.target.closest(".tema-selector button[data-tema]");
            if (boton) {
                elegir(boton.getAttribute("data-tema"));
            }
        });
    });

    // Otra pestaña cambió el tema: se aplica también acá.
    window.addEventListener("storage", function (evento) {
        if (evento.key === CLAVE && TEMAS.indexOf(evento.newValue) >= 0) {
            aplicar(evento.newValue);
        }
    });
})();
