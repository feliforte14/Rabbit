package com.rabbit.infraestructura;

/**
 * Lee un archivo subido desde un formulario (h:inputFile) con un tope: un
 * archivo más grande no se termina de leer en memoria. Si el archivo se
 * pasa, devuelve null y la pantalla avisa.
 */

import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;

public final class ArchivosSubidos {

    private ArchivosSubidos() {}

    /**
     * Los bytes del archivo, o un arreglo vacío si no se eligió ninguno.
     *
     * @return null si el archivo supera maximoBytes
     */
    public static byte[] leer(Part archivo, int maximoBytes) throws IOException {
        if (archivo == null || archivo.getSize() == 0) {
            return new byte[0];
        }
        if (archivo.getSize() > maximoBytes) {
            return null;
        }
        try (InputStream entrada = archivo.getInputStream()) {
            byte[] datos = entrada.readNBytes(maximoBytes + 1);
            return datos.length > maximoBytes ? null : datos;
        }
    }
}
