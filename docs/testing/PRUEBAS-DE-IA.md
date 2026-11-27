# Pruebas de la funcionalidad de IA

Alcance: el gateway de IA (`com.rabbit.integracion.ia`, ADR-066 y ADR-073) y sus dos usos: clasificar el comentario de una
calificación (HU-37) y convertir las instrucciones de entrega en indicaciones fijas para el repartidor (ADR-073). Última
revisión: 26/11/2026.

## Qué se probó y qué no (estado honesto)

| Nivel | Estado |
|---|---|
| Pruebas automatizadas con un proveedor falso (válido, inválido, caído, lento) | **Hechas**, corren en cada build |
| Fallback en vivo contra la API real de Claude, con la app en Docker | **Hecho el 26/11/2026**: la API contestó que la clave no podía usarse y la app guardó la instrucción y le mostró el texto original al repartidor, sin errores |
| **Etiquetado con una respuesta real del modelo** | **No probado**: la cuenta de Anthropic no tiene saldo y el equipo decidió no cargarlo. Que el modelo devuelva las indicaciones correctas para textos reales queda sin verificar |

Por eso, lo que se afirma de la IA es el **comportamiento ante fallas y la validación**, no la calidad de sus respuestas.

## Casos de prueba

| Id | Caso | Resultado esperado | Cómo se prueba | Estado |
|---|---|---|---|---|
| IA-01 | Respuesta válida del modelo para unas instrucciones | se guardan las indicaciones de la lista cerrada | `EtiquetadorIaTest.etiquetaConLaRespuestaDelModelo`, `EtiquetadoDeInstruccionesTest.guardaLasIndicacionesQueContestaLaIa` | Pasa (proveedor falso) |
| IA-02 | Sin clave de API | no hay etiquetas y el pedido se guarda igual; el repartidor ve el texto | `EtiquetadorIaTest.sinClaveConfiguradaNoHayEtiquetas`, `ClasificadorIaTest.sinClaveConfiguradaElProveedorVigenteNoClasifica` | Pasa |
| IA-03 | El proveedor no responde en 5 s | se corta y se sigue sin etiquetas | `ProveedorClaudeTest.sinRespuestaATiempoSeCortaYSeCaeEnElFallback`, `elTimeoutPorDefectoEsDeCincoSegundos` | Pasa |
| IA-04 | El proveedor está caído o contesta con error | fallback, sin romper el flujo | `ProveedorClaudeTest.unServidorQueNoEstaEsUnaFalla`, `unaRespuestaQueNoEsCorrectaEsUnaFallaDelProveedor`, `EtiquetadorIaTest.siElProveedorNoEstaDisponibleSeSigueSinEtiquetas` | Pasa |
| IA-05 | Falla inesperada del proveedor | también cae en el fallback | `EtiquetadorIaTest.unaFallaInesperadaDelProveedorTambienCaeEnElFallback` | Pasa |
| IA-06 | Entrada vacía, nula o de más de 200 caracteres | no se llama al proveedor | `EtiquetadorIaTest.unaEntradaInvalidaNoLlegaAlProveedor` | Pasa |
| IA-07 | Caracteres de control y marcas `<` `>` en la entrada | se limpian antes de enviar | `EtiquetadorIaTest.laEntradaSeLimpiaDeControlYDeMarcas` | Pasa |
| IA-08 | Teléfonos y documentos en el texto | no salen hacia la IA | `EtiquetadorIaTest.losTelefonosYDocumentosNoSalenHaciaLaIa`, `losNumerosCortosDeLaDireccionSeQuedan` | Pasa |
| IA-09 | Un texto que intenta darle órdenes al modelo | no se puede colar texto libre: solo se aceptan valores de la lista | `EtiquetadorIaTest.unTextoQueIntentaDarOrdenesNoPuedeColarTextoLibre`, `ClasificadorIaTest.unComentarioQueIntentaDarOrdenesNoPuedeColarTextoLibre` | Pasa |
| IA-10 | Respuesta fuera de la lista, nula o con formato raro | se descarta; se aceptan espacios y saltos y hasta 4 indicaciones | `EtiquetadorIaTest.unaRespuestaNulaSeDescarta`, `aceptaHastaCuatroIndicaciones`, `aceptaLaRespuestaConEspaciosYSaltos`, `ClasificadorIaTest.lasRespuestasFueraDeLaListaSeDescartan` | Pasa |
| IA-11 | "Ninguna" como respuesta | es válida y no deja etiquetas | `EtiquetadorIaTest.ningunaEsUnaRespuestaValidaSinEtiquetas`, `EtiquetadoDeInstruccionesTest.sinIndicacionesConcretasSeGuardaNull` | Pasa |
| IA-12 | El cliente cambia las instrucciones mientras la IA responde | la respuesta vieja se descarta (`UPDATE ... WHERE instruccionesEntrega = :texto`) y no se rompe nada | `PedidoRepositoryTest.laRespuestaVieja_deLaIaSeDescartaPorqueLaConsultaExigeElMismoTexto` (la consulta exige el mismo texto), `EtiquetadoDeInstruccionesTest.siElTextoCambioMientrasLaIaContestabaLaRespuestaSeDescartaSinFallar` y prueba directa en PostgreSQL el 26/11/2026 (con el texto viejo actualiza 0 filas; con el vigente, 1) | Pasa (verifica la guarda de la consulta, no una carrera real de dos hilos) |
| IA-13 | Editar las instrucciones dispara el etiquetado después del commit | se dispara una vez y no bloquea el alta | `PedidoServiceTest.elDestinatarioReprogramaYDejaInstrucciones`, `EtiquetadoDeInstruccionesTest.elObservadorLePasaLasInstruccionesAlSegundoPlano` | Pasa |
| IA-14 | La pantalla del repartidor muestra las indicaciones | se ven antes de las instrucciones del cliente | `HojaDeRutaDTOTest` (propiedades que usa la vista), E2E-8 | Pasa (CI) |
| IA-15 | Fallback en vivo contra la API real, en Docker | instrucción guardada, texto original visible, sin errores | prueba manual del 26/11/2026 | Pasa |
| IA-16 | Respuesta real del modelo a textos reales | indicaciones razonables para el repartidor | — | **Sin probar** (falta saldo) |

## Riesgos conocidos

- La calidad del etiquetado depende del modelo y no está medida (IA-16).
- La lista cerrada limita el daño de una respuesta inesperada, pero una etiqueta equivocada válida (por ejemplo "Mascota" donde no
  hay) no se detecta: por eso el texto original se muestra siempre al lado.
- IA-12 verifica la guarda de la consulta y su efecto en PostgreSQL, pero no reproduce dos hilos compitiendo de verdad.
