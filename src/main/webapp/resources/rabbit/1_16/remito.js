/*
 * Foto del remito de entrega (ADR-033): antes de subirla, la achica en el
 * navegador. La camara de un celular saca fotos de 3 a 8 MB; para leer un
 * remito alcanza con 1600 px de lado, que en JPEG pesan unos 300 KB. Asi
 * sube rapido con poca senal y no ocupa de mas en la base.
 *
 * Si el navegador no puede achicarla (o no hace falta), se sube tal cual:
 * el servidor igual valida tipo y tamano.
 */
(function () {
    var LADO_MAXIMO = 1600;
    var CALIDAD = 0.8;
    var NO_ACHICAR_DEBAJO_DE = 600 * 1024;

    function achicar(input) {
        var archivo = input.files && input.files[0];
        if (!archivo || !/^image\//.test(archivo.type) || archivo.size < NO_ACHICAR_DEBAJO_DE
                || typeof DataTransfer === 'undefined' || !window.createImageBitmap) {
            return;
        }
        createImageBitmap(archivo).then(function (imagen) {
            var escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.width, imagen.height));
            var lienzo = document.createElement('canvas');
            lienzo.width = Math.round(imagen.width * escala);
            lienzo.height = Math.round(imagen.height * escala);
            lienzo.getContext('2d').drawImage(imagen, 0, 0, lienzo.width, lienzo.height);
            lienzo.toBlob(function (blob) {
                if (!blob || blob.size >= archivo.size) {
                    return;
                }
                var datos = new DataTransfer();
                datos.items.add(new File([blob], 'remito.jpg', { type: 'image/jpeg' }));
                input.files = datos.files;
            }, 'image/jpeg', CALIDAD);
        }).catch(function () {
            // Formato que el navegador no sabe abrir: se sube tal cual.
        });
    }

    document.addEventListener('change', function (evento) {
        if (evento.target.classList && evento.target.classList.contains('foto-remito')) {
            achicar(evento.target);
        }
    });
})();
