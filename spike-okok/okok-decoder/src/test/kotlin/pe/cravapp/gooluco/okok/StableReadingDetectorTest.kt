package pe.cravapp.gooluco.okok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableReadingDetectorTest {

    private fun frame(v: OkokTestVectors.Vector) =
        (OkokDecoder.decode(v.companyId, v.data, v.mac) as DecodeResult.Ok).frame

    @Test
    fun tv04_provisional_y_luego_tv03_estable() {
        val d = StableReadingDetector()
        val mac = OkokTestVectors.TV03.mac
        assertFalse(d.accept(mac, frame(OkokTestVectors.TV04)))
        assertTrue(d.accept(mac, frame(OkokTestVectors.TV03)))
        assertFalse("la repetición no se vuelve a marcar", d.accept(mac, frame(OkokTestVectors.TV03)))
    }

    @Test
    fun vf0_sin_flag_requiere_repeticiones() {
        val d = StableReadingDetector(repeatsForUnflagged = 3)
        val f = frame(OkokTestVectors.SYN_VF0)
        assertFalse(d.accept("M", f))
        assertFalse(d.accept("M", f))
        assertTrue(d.accept("M", f))
        assertFalse(d.accept("M", f))
    }

    @Test
    fun secuencia_del_simulador_marca_lecturas_esperadas() {
        val d = StableReadingDetector()
        val marked = OkokTestVectors.simulatorSequence().mapNotNull { v ->
            val r = OkokDecoder.decode(v.companyId, v.data, v.mac)
            if (r is DecodeResult.Ok && d.accept(v.mac, r.frame)) v.id else null
        }
        assertEquals(listOf("TV-01", "TV-03", "TV-02", "SYN-V20", "SYN-V11", "SYN-VF0 #3"), marked)
    }

    @Test
    fun csv_tiene_cabecera_y_fila_tv01() {
        val v = OkokTestVectors.TV01
        val c = Capture(1, 0L, CaptureSource.SIMULATOR, v.mac, v.name, -60, v.companyId, v.dataHex, null,
            OkokDecoder.decode(v.companyId, v.data, v.mac), stableReading = true)
        val row = CsvLog.row(c, "1970-01-01T00:00:00Z")
        assertEquals(CsvLog.HEADER.split(",").size, parseCsvLine(row).size)
        assertTrue(row.contains("0x07C0"))
        assertTrue(row.contains("67.050"))
        assertTrue(row.contains("600.0"))
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString(); return out
    }
}
