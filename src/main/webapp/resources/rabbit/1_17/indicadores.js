/*
 * Graficos del tablero de indicadores (ADR-044), en SVG y sin librerias.
 * Cada elemento con data-grafico trae sus datos en data-datos, un JSON que
 * arma IndicadoresBean:
 *   {"series": [..], "filas": [{"etiqueta", "detalle"?, "valores": [..], "extra"?: [..]}],
 *    "formato": "entero" | "porcentaje", "columnasExtra"?: [..]}
 * Tipos: "columnas-apiladas" (una columna por fila, apilando las series) y
 * "barras" (horizontales; agrupadas si hay mas de una serie).
 *
 * Los colores salen de variables CSS (--viz-s1..3 en estilos.css), asi que
 * cambiar de tema no obliga a redibujar. Los textos (nombres de comercios,
 * repartidores, motivos) se ponen con textContent, nunca como HTML.
 *
 * Cada grafico tiene leyenda (con 2 o mas series), tooltip al pasar el
 * mouse o al enfocar con el teclado, y un boton para verlo como tabla.
 */
var RabbitGraficos = (function () {
    var NS = 'http://www.w3.org/2000/svg';
    var NUMERO = new Intl.NumberFormat('es-AR');
    var tooltip = null;

    function svg(tag, atributos, padre) {
        var e = document.createElementNS(NS, tag);
        Object.keys(atributos || {}).forEach(function (k) { e.setAttribute(k, atributos[k]); });
        if (padre) { padre.appendChild(e); }
        return e;
    }

    function html(tag, clase, padre, texto) {
        var e = document.createElement(tag);
        if (clase) { e.className = clase; }
        if (texto != null) { e.textContent = texto; }
        if (padre) { padre.appendChild(e); }
        return e;
    }

    function formatear(valor, formato) {
        if (valor == null) { return '—'; }
        return formato === 'porcentaje' ? NUMERO.format(valor) + ' %' : NUMERO.format(valor);
    }

    // Maximo "redondo" y paso de las marcas del eje (1, 2, 5, 10, 20...).
    function escala(maximo) {
        if (maximo <= 0) { return {maximo: 4, paso: 1}; }
        var bruto = maximo / 4;
        var potencia = Math.pow(10, Math.floor(Math.log10(bruto)));
        var paso = [1, 2, 5, 10].map(function (m) { return m * potencia; })
            .filter(function (p) { return p >= bruto; })[0];
        paso = Math.max(1, paso);
        return {maximo: Math.ceil(maximo / paso) * paso, paso: paso};
    }

    // Barra con el extremo de dato redondeado (4px) y cuadrada en la base.
    function barra(x, y, ancho, alto, lado) {
        var r = Math.max(0, Math.min(4, ancho / 2, alto));
        if (lado === 'arriba') {
            return 'M' + x + ',' + (y + alto) + 'V' + (y + r) + 'Q' + x + ',' + y + ' ' + (x + r) + ',' + y
                + 'H' + (x + ancho - r) + 'Q' + (x + ancho) + ',' + y + ' ' + (x + ancho) + ',' + (y + r)
                + 'V' + (y + alto) + 'Z';
        }
        r = Math.max(0, Math.min(4, alto / 2, ancho));
        return 'M' + x + ',' + y + 'H' + (x + ancho - r) + 'Q' + (x + ancho) + ',' + y + ' ' + (x + ancho) + ',' + (y + r)
            + 'V' + (y + alto - r) + 'Q' + (x + ancho) + ',' + (y + alto) + ' ' + (x + ancho - r) + ',' + (y + alto)
            + 'H' + x + 'Z';
    }

    // ---------------------------------------------------------------
    // Tooltip: uno solo para toda la pagina. El valor manda, el nombre
    // de la serie acompaña; cada serie con su clave de linea.
    // ---------------------------------------------------------------
    function mostrarTooltip(datos, fila, evento, objetivo) {
        if (!tooltip) {
            tooltip = html('div', 'viz-tooltip', document.body);
            tooltip.setAttribute('role', 'status');
        }
        tooltip.textContent = '';
        html('div', 'viz-tooltip-titulo', tooltip, fila.detalle || fila.etiqueta);
        datos.series.forEach(function (serie, i) {
            var renglon = html('div', 'viz-tooltip-fila', tooltip);
            if (datos.series.length > 1) { html('span', 'viz-linea viz-b' + (i + 1), renglon); }
            html('strong', null, renglon, formatear(fila.valores[i], datos.formato));
            html('span', 'viz-tooltip-serie', renglon, serie);
        });
        if (datos.series.length > 1 && datos.formato !== 'porcentaje') {
            var total = fila.valores.reduce(function (a, v) { return a + (v || 0); }, 0);
            var renglonTotal = html('div', 'viz-tooltip-fila viz-tooltip-total', tooltip);
            html('strong', null, renglonTotal, formatear(total, datos.formato));
            html('span', 'viz-tooltip-serie', renglonTotal, 'Total');
        }
        tooltip.style.display = 'block';
        var x, y;
        if (evento && evento.clientX != null) {
            x = evento.clientX;
            y = evento.clientY;
        } else {
            var caja = objetivo.getBoundingClientRect();
            x = caja.left + caja.width / 2;
            y = caja.top;
        }
        var ancho = tooltip.offsetWidth;
        var alto = tooltip.offsetHeight;
        var izquierda = Math.min(Math.max(8, x + 14), window.innerWidth - ancho - 8);
        var arriba = y - alto - 12 < 8 ? y + 16 : y - alto - 12;
        tooltip.style.left = izquierda + 'px';
        tooltip.style.top = arriba + 'px';
    }

    function ocultarTooltip() {
        if (tooltip) { tooltip.style.display = 'none'; }
    }

    // Zona sensible: mas grande que la marca. Se activa con mouse, toque o teclado.
    function zonaSensible(padre, atributos, datos, fila, grupo, etiquetaAccesible) {
        var zona = svg('rect', Object.assign({fill: 'transparent', tabindex: '0', 'class': 'viz-zona',
            'aria-label': etiquetaAccesible}, atributos), padre);
        function entrar(e) {
            grupo.classList.add('viz-activa');
            mostrarTooltip(datos, fila, e, zona);
        }
        function salir() {
            grupo.classList.remove('viz-activa');
            ocultarTooltip();
        }
        zona.addEventListener('pointermove', entrar);
        zona.addEventListener('pointerleave', salir);
        zona.addEventListener('focus', function () { entrar(null); });
        zona.addEventListener('blur', salir);
        return zona;
    }

    function textoAccesible(datos, fila) {
        return (fila.detalle || fila.etiqueta) + ': ' + datos.series.map(function (s, i) {
            return s + ' ' + formatear(fila.valores[i], datos.formato);
        }).join(', ');
    }

    // ---------------------------------------------------------------
    // Columnas apiladas (entregas por dia)
    // ---------------------------------------------------------------
    function columnasApiladas(lienzo, datos) {
        var ancho = Math.max(280, lienzo.clientWidth);
        var m = {arriba: 12, derecha: 8, abajo: 26, izquierda: 34};
        var altoPlot = 200;
        var alto = altoPlot + m.arriba + m.abajo;
        var anchoPlot = ancho - m.izquierda - m.derecha;
        var totales = datos.filas.map(function (f) {
            return f.valores.reduce(function (a, v) { return a + (v || 0); }, 0);
        });
        var e = escala(Math.max.apply(null, totales.concat([0])));
        var y = function (v) { return m.arriba + altoPlot - v / e.maximo * altoPlot; };
        var raiz = svg('svg', {width: ancho, height: alto, viewBox: '0 0 ' + ancho + ' ' + alto, 'aria-hidden': 'true'});

        for (var t = 0; t <= e.maximo; t += e.paso) {
            svg('line', {x1: m.izquierda, x2: ancho - m.derecha, y1: y(t), y2: y(t),
                'class': t === 0 ? 'viz-base' : 'viz-grilla'}, raiz);
            svg('text', {x: m.izquierda - 6, y: y(t) + 4, 'text-anchor': 'end', 'class': 'viz-eje'}, raiz)
                .textContent = NUMERO.format(t);
        }

        var n = datos.filas.length;
        var banda = anchoPlot / n;
        var anchoBarra = Math.max(1, Math.min(24, banda - 2));
        var cadaCuanto = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(anchoPlot / 56))));
        datos.filas.forEach(function (fila, i) {
            var x = m.izquierda + i * banda + (banda - anchoBarra) / 2;
            var grupo = svg('g', {'class': 'viz-columna'}, raiz);
            var base = 0;
            var ultima = -1;
            fila.valores.forEach(function (v, s) { if (v > 0) { ultima = s; } });
            fila.valores.forEach(function (v, s) {
                if (!(v > 0)) { return; }
                var arriba = y(base + v);
                var abajo = y(base);
                // 2px de superficie entre segmentos (no un borde dibujado).
                if (base > 0) { abajo -= 2; }
                var h = Math.max(1, abajo - arriba);
                if (s === ultima) {
                    svg('path', {d: barra(x, arriba, anchoBarra, h, 'arriba'), 'class': 'viz-f' + (s + 1)}, grupo);
                } else {
                    svg('rect', {x: x, y: arriba, width: anchoBarra, height: h, 'class': 'viz-f' + (s + 1)}, grupo);
                }
                base += v;
            });
            if (i % cadaCuanto === 0 || (i === n - 1 && i % cadaCuanto > cadaCuanto / 2)) {
                svg('text', {x: m.izquierda + i * banda + banda / 2, y: alto - 8, 'text-anchor': 'middle',
                    'class': 'viz-eje'}, raiz).textContent = fila.etiqueta;
            }
            zonaSensible(raiz, {x: m.izquierda + i * banda, y: m.arriba, width: banda, height: altoPlot},
                datos, fila, grupo, textoAccesible(datos, fila));
        });
        lienzo.appendChild(raiz);
    }

    // ---------------------------------------------------------------
    // Barras horizontales (agrupadas si hay mas de una serie)
    // ---------------------------------------------------------------
    function barras(lienzo, datos) {
        var ancho = Math.max(280, lienzo.clientWidth);
        var series = datos.series.length;
        var grosor = series > 1 ? 12 : 18;
        var altoFila = series * grosor + (series - 1) * 2 + 14;
        var largoMax = datos.filas.reduce(function (a, f) { return Math.max(a, f.etiqueta.length); }, 0);
        var anchoEtiqueta = Math.min(Math.round(ancho * 0.55), largoMax * 6.6 + 12);
        var reservaValor = 48;
        var anchoPlot = Math.max(40, ancho - anchoEtiqueta - reservaValor);
        var maximo = datos.formato === 'porcentaje' ? 100 : escala(Math.max.apply(null,
            datos.filas.map(function (f) { return Math.max.apply(null, f.valores.map(function (v) { return v || 0; })); })
                .concat([0]))).maximo;
        var alto = datos.filas.length * altoFila + 4;
        var raiz = svg('svg', {width: ancho, height: alto, viewBox: '0 0 ' + ancho + ' ' + alto, 'aria-hidden': 'true'});
        var x0 = anchoEtiqueta;
        svg('line', {x1: x0, x2: x0, y1: 0, y2: alto, 'class': 'viz-base'}, raiz);

        datos.filas.forEach(function (fila, i) {
            var top = i * altoFila + 7;
            var grupo = svg('g', {'class': 'viz-columna'}, raiz);
            var etiqueta = fila.etiqueta;
            var caben = Math.max(4, Math.floor((anchoEtiqueta - 12) / 6.6));
            if (etiqueta.length > caben) { etiqueta = etiqueta.slice(0, caben - 1) + '…'; }
            svg('text', {x: x0 - 8, y: top + (altoFila - 14) / 2 + 4, 'text-anchor': 'end', 'class': 'viz-etiqueta'}, raiz)
                .textContent = etiqueta;
            fila.valores.forEach(function (v, s) {
                var yb = top + s * (grosor + 2);
                var largo = v == null ? 0 : Math.max(v > 0 ? 2 : 0, v / maximo * anchoPlot);
                if (largo > 0) {
                    svg('path', {d: barra(x0, yb, largo, grosor, 'derecha'), 'class': 'viz-f' + (s + 1)}, grupo);
                }
                // Valor en la punta: con pocas filas cada valor tiene su lugar.
                svg('text', {x: x0 + largo + 6, y: yb + grosor / 2 + 4, 'class': 'viz-valor'}, raiz)
                    .textContent = formatear(v, datos.formato);
            });
            zonaSensible(raiz, {x: 0, y: i * altoFila, width: ancho, height: altoFila},
                datos, fila, grupo, textoAccesible(datos, fila));
        });
        lienzo.appendChild(raiz);
    }

    // ---------------------------------------------------------------
    // Leyenda y vista de tabla
    // ---------------------------------------------------------------
    function leyenda(datos, tipo) {
        var lista = html('ul', 'viz-leyenda');
        datos.series.forEach(function (serie, i) {
            var item = html('li', null, lista);
            html('span', 'viz-clave viz-b' + (i + 1) + (tipo === 'barras' ? ' viz-clave-barra' : ''), item);
            item.appendChild(document.createTextNode(serie));
        });
        return lista;
    }

    function tabla(datos, titulo) {
        var contenedor = html('div', 'tabla-scroll viz-tabla');
        var t = html('table', 'tabla', contenedor);
        html('caption', 'viz-oculto', t, titulo);
        var cabecera = html('tr', null, html('thead', null, t));
        html('th', null, cabecera, '');
        datos.series.concat(datos.columnasExtra || []).forEach(function (c) { html('th', null, cabecera, c); });
        var cuerpo = html('tbody', null, t);
        datos.filas.forEach(function (fila) {
            var tr = html('tr', null, cuerpo);
            html('th', null, tr, fila.detalle || fila.etiqueta).setAttribute('scope', 'row');
            fila.valores.forEach(function (v) { html('td', 'viz-numero', tr, formatear(v, datos.formato)); });
            (fila.extra || []).forEach(function (v) { html('td', 'viz-numero', tr, v); });
        });
        return contenedor;
    }

    function dibujar(lienzo) {
        var datos;
        try {
            datos = JSON.parse(lienzo.getAttribute('data-datos') || '{}');
        } catch (e) {
            return;
        }
        var tipo = lienzo.getAttribute('data-grafico');
        var tarjeta = lienzo.closest('.viz-tarjeta');
        lienzo.textContent = '';
        var vacio = !datos.filas || datos.filas.length === 0 || (datos.formato !== 'porcentaje'
            && datos.filas.every(function (f) { return f.valores.every(function (v) { return !v; }); }));
        if (vacio) {
            html('p', 'viz-vacio', lienzo, lienzo.getAttribute('data-vacio') || 'Sin datos en el período.');
            return;
        }
        if (tipo === 'columnas-apiladas') {
            columnasApiladas(lienzo, datos);
        } else {
            barras(lienzo, datos);
        }
        if (lienzo._armado) { return; }
        lienzo._armado = true;
        // La leyenda va arriba del grafico (con 2 o mas series); la tabla y
        // su boton, en el pie de la tarjeta.
        if (datos.series.length > 1) {
            tarjeta.insertBefore(leyenda(datos, tipo), lienzo);
        }
        var titulo = tarjeta.querySelector('.viz-titulo');
        var vista = tabla(datos, titulo ? titulo.textContent : '');
        vista.hidden = true;
        tarjeta.appendChild(vista);
        var boton = html('button', 'viz-boton-tabla', tarjeta, 'Ver como tabla');
        boton.type = 'button';
        boton.setAttribute('aria-pressed', 'false');
        boton.addEventListener('click', function () {
            var aTabla = vista.hidden;
            vista.hidden = !aTabla;
            lienzo.hidden = aTabla;
            var lista = tarjeta.querySelector('.viz-leyenda');
            if (lista) { lista.hidden = aTabla; }
            boton.textContent = aTabla ? 'Ver gráfico' : 'Ver como tabla';
            boton.setAttribute('aria-pressed', String(aTabla));
            ocultarTooltip();
            if (!aTabla) { dibujarTodos(); }
        });
    }

    function dibujarTodos() {
        document.querySelectorAll('[data-grafico]').forEach(function (lienzo) {
            if (!lienzo.hidden && lienzo._ancho !== lienzo.clientWidth) {
                lienzo._ancho = lienzo.clientWidth;
                dibujar(lienzo);
            }
        });
    }

    var espera;
    window.addEventListener('resize', function () {
        clearTimeout(espera);
        espera = setTimeout(dibujarTodos, 150);
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') { ocultarTooltip(); }
    });
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', dibujarTodos);
    } else {
        dibujarTodos();
    }
    return {dibujarTodos: dibujarTodos};
})();
