package ar.edu.iua.integrador.util;

/** Funciones chicas para limpiar textos que llegan de afuera, compartidas entre los services. */
public final class TextUtil {

    private TextUtil() {}

    /**
     * Devuelve null si el texto es null, "" o solo espacios; si no, el mismo texto. Sirve para
     * tratar un código vacío como "no vino": si no, se guardaría "" y el segundo que llegue vacío
     * chocaría con el índice único.
     */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
