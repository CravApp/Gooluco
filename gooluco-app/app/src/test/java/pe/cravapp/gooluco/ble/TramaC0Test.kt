package pe.cravapp.gooluco.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.cravapp.gooluco.okok.DecodeResult
import pe.cravapp.gooluco.okok.OkokDecoder
import pe.cravapp.gooluco.okok.OkokTestVectors
import pe.cravapp.gooluco.okok.StableReadingDetector
import pe.cravapp.gooluco.okok.toHex

class TramaC0Test {
    @Test fun reproduceRc03ByteAByte() {
        val rc03 = OkokTestVectors.RC03
        val gen = TramaC0.payload(92.80, estable = true, impedanciaOhm = 600.0, mac = rc03.mac)
        assertEquals(rc03.dataHex, gen.toHex())
    }

    @Test fun sesionSimuladaDaUnSoloPesajeEstable() {
        val det = StableReadingDetector()
        var estables = 0
        var ultimo = 0.0
        TramaC0.sesion(72.4).forEachIndexed { i, (kg, est) ->
            val r = OkokDecoder.decode(TramaC0.companyId(i + 1), TramaC0.payload(kg, est), TramaC0.MAC_SIMULADA)
            if (r is DecodeResult.Ok && det.accept(TramaC0.MAC_SIMULADA, r.frame)) { estables++; ultimo = r.frame.weightKg }
        }
        assertEquals(1, estables)
        assertEquals(72.4, ultimo, 1e-9)
        assertTrue(TramaC0.companyId(7) and 0xFF == 0xC0)
    }
}
