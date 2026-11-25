package pe.cravapp.gooluco.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

sealed interface EstadoModelo {
    data class Disponible(val version: String, val entradas: Int) : EstadoModelo
    data class NoDisponible(val motivo: String) : EstadoModelo
}

/** Capa B (RF-30, RF-31, RF-34). La Capa A (reglas) siempre se muestra; esto es una estimación complementaria. */
interface ModeloRiesgo {
    val estado: EstadoModelo
    /** Probabilidad 0..1, o null si el modelo no está disponible o la entrada no cumple el contrato. */
    fun inferir(entrada: FloatArray): Float?
}

/** Sin modelo: la app funciona solo con la Capa A. */
object ModeloAusente : ModeloRiesgo {
    override val estado = EstadoModelo.NoDisponible("Sin modelo empaquetado (TBD-04).")
    override fun inferir(entrada: FloatArray): Float? = null
}

/**
 * Cargador LiteRT. Busca `assets/models/riesgo.tflite`; si no existe o falla, degrada a [ModeloAusente].
 * Pendiente: manifiesto firmado, SHA-256 y versión (RF-35), actualización (RF-36).
 */
class ModeloLiteRt private constructor(private val interprete: Interpreter, private val n: Int) : ModeloRiesgo {
    override val estado: EstadoModelo = EstadoModelo.Disponible("assets/$RUTA", n)

    override fun inferir(entrada: FloatArray): Float? {
        if (entrada.size != n || entrada.any { it.isNaN() }) return null
        val inBuf = ByteBuffer.allocateDirect(4 * n).order(ByteOrder.nativeOrder())
        entrada.forEach { inBuf.putFloat(it) }
        inBuf.rewind()
        val out = Array(1) { FloatArray(1) }
        return runCatching { interprete.run(inBuf, out); out[0][0].coerceIn(0f, 1f) }.getOrNull()
    }

    companion object {
        const val RUTA = "models/riesgo.tflite"
        /** Contrato del manifiesto ilustrativo del SRS (§6, 18 entradas). */
        const val ENTRADAS_CONTRATO = 18

        fun cargar(ctx: Context): ModeloRiesgo = try {
            val fd = ctx.assets.openFd(RUTA)
            val buf = FileInputStream(fd.fileDescriptor).channel.use {
                it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
            val interp = Interpreter(buf, Interpreter.Options().setNumThreads(2))
            val shape = interp.getInputTensor(0).shape()
            val n = shape.lastOrNull() ?: 0
            if (n != ENTRADAS_CONTRATO) {
                interp.close()
                object : ModeloRiesgo {
                    override val estado = EstadoModelo.NoDisponible("Contrato inválido: $n entradas (se esperan $ENTRADAS_CONTRATO).")
                    override fun inferir(entrada: FloatArray): Float? = null
                }
            } else ModeloLiteRt(interp, n)
        } catch (e: java.io.FileNotFoundException) {
            ModeloAusente
        } catch (e: Throwable) {
            object : ModeloRiesgo {
                override val estado = EstadoModelo.NoDisponible("No se pudo cargar el modelo: ${e.message}")
                override fun inferir(entrada: FloatArray): Float? = null
            }
        }
    }
}
