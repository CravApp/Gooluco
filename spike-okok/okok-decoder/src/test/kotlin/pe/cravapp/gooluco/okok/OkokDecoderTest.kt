package pe.cravapp.gooluco.okok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OkokDecoderTest {

    private val delta = 1e-6

    private fun ok(v: OkokTestVectors.Vector): OkokFrame =
        when (val r = OkokDecoder.decode(v.companyId, v.data, v.mac)) {
            is DecodeResult.Ok -> r.frame
            else -> { fail("${v.id} debía decodificar y dio $r"); throw IllegalStateException() }
        }

    // ------------------------------------------------------------ vectores reales (SRS v2, Tabla 2)

    @Test
    fun tv01_c0_67_05kg_600ohm_estable() {
        val f = ok(OkokTestVectors.TV01)
        assertEquals(OkokVariant.C0, f.variant)
        assertEquals(67.05, f.weightKg, delta)
        assertEquals(600.0, f.impedanceOhm!!, delta)
        assertEquals(true, f.stable)
        assertEquals(ScaleUnit.KG, f.unit)
        assertEquals(100, f.divisor)
        assertEquals(6705, f.rawWeight)
        assertFalse(f.encrypted)
        assertEquals("24:16:51:4B:12:3C", f.embeddedMac)
        assertEquals(0x0A01, f.productId)
        assertEquals(0x07, f.counter)
        assertNull(f.checksumOk)
        assertTrue(f.notes.any { it.contains("MAC embebida = MAC del anunciante") })
    }

    @Test
    fun tv02_c0_76_30kg_500ohm() {
        val f = ok(OkokTestVectors.TV02)
        assertEquals(OkokVariant.C0, f.variant)
        assertEquals(76.30, f.weightKg, delta)
        assertEquals(500.0, f.impedanceOhm!!, delta)
        assertEquals(true, f.stable)
        assertEquals(0x3F, f.counter)
    }

    @Test
    fun tv03_c0_81_50kg_estable() {
        val f = ok(OkokTestVectors.TV03)
        assertEquals(81.50, f.weightKg, delta)
        assertEquals(true, f.stable)
        assertEquals("5D:02:49:A6:3D:E8", f.embeddedMac)
    }

    @Test
    fun tv04_c0_50_75kg_no_estable() {
        val f = ok(OkokTestVectors.TV04)
        assertEquals(50.75, f.weightKg, delta)
        assertEquals(false, f.stable)
    }

    // ------------------------------------------------------------ captura real propia (23/11/2026, MAC enmascarada)

    @Test
    fun rc01_reposo_peso_cero_se_descarta_como_reposo() {
        val v = OkokTestVectors.RC01
        val r = OkokDecoder.decode(v.companyId, v.data, v.mac)
        assertTrue(r is DecodeResult.Rejected)
        assertEquals(OkokDecoder.REASON_IDLE, (r as DecodeResult.Rejected).reason)
    }

    @Test
    fun rc02_provisional_92_85kg_sin_impedancia() {
        val f = ok(OkokTestVectors.RC02)
        assertEquals(92.85, f.weightKg, delta)
        assertEquals(false, f.stable)
        assertNull(f.impedanceOhm)
        assertEquals(100, f.divisor)
        assertEquals(ScaleUnit.KG, f.unit)
        assertEquals(0x06, f.counter)
    }

    @Test
    fun rc03_estable_92_80kg_impedancia_sospechosa() {
        val f = ok(OkokTestVectors.RC03)
        assertEquals(92.80, f.weightKg, delta)
        assertEquals(true, f.stable)
        assertEquals(600.0, f.impedanceOhm!!, delta)
        assertFalse(f.encrypted)
        assertEquals(0x0A01, f.productId)
        assertEquals("78:66:A5:00:00:01", f.embeddedMac)
        assertTrue(f.notes.contains(OkokDecoder.NOTE_SUSPECT_IMPEDANCE))
        assertTrue(f.notes.any { it.contains("MAC embebida = MAC del anunciante") })
    }

    // ------------------------------------------------------------ otras variantes (sintéticas)

    @Test
    fun v20_sintetica_decodifica_con_checksum() {
        val f = ok(OkokTestVectors.SYN_V20)
        assertEquals(OkokVariant.V20, f.variant)
        assertEquals(72.40, f.weightKg, delta)
        assertEquals(512.0, f.impedanceOhm!!, delta)
        assertEquals(true, f.stable)
        assertEquals(true, f.checksumOk)
    }

    @Test
    fun v20_checksum_alterado_se_descarta() {
        val v = OkokTestVectors.SYN_V20_BAD
        val r = OkokDecoder.decode(v.companyId, v.data)
        assertTrue("esperaba Rejected y fue $r", r is DecodeResult.Rejected)
        assertTrue((r as DecodeResult.Rejected).reason.contains("checksum"))
    }

    @Test
    fun v20_checksum_variante_corta_se_acepta() {
        val b = OkokTestVectors.SYN_V20.data
        b[12] = OkokDecoder.xorSeeded(0x20, b, 0, 10).toByte()
        val r = OkokDecoder.decode(OkokDecoder.COMPANY_V20, b)
        assertTrue(r is DecodeResult.Ok)
        assertTrue((r as DecodeResult.Ok).frame.notes.any { it.contains("0..10") })
    }

    @Test
    fun v20_longitud_incorrecta_se_descarta() {
        val r = OkokDecoder.decode(OkokDecoder.COMPANY_V20, ByteArray(18))
        assertTrue(r is DecodeResult.Rejected)
    }

    @Test
    fun v11_sintetica_decodifica() {
        val f = ok(OkokTestVectors.SYN_V11)
        assertEquals(OkokVariant.V11, f.variant)
        assertEquals(65.3, f.weightKg, delta)
        assertEquals(ScaleUnit.KG, f.unit)
        assertEquals(true, f.stable)
    }

    @Test
    fun v11_checksum_alterado_se_descarta() {
        val b = OkokTestVectors.SYN_V11.data
        b[16] = (b[16].toInt() xor 0x01).toByte()
        assertTrue(OkokDecoder.decode(OkokDecoder.COMPANY_V11, b) is DecodeResult.Rejected)
    }

    @Test
    fun v11_unidad_lb_se_convierte_a_kg() {
        val b = OkokTestVectors.SYN_V11.data
        b[9] = (2 shl 3).toByte() // lb, ÷10
        b[3] = 0x05; b[4] = 0xDC.toByte() // 1500 → 150,0 lb
        b[16] = OkokDecoder.xorSeeded(0xCA xor 0x11, b, 0, 15).toByte()
        val f = (OkokDecoder.decode(OkokDecoder.COMPANY_V11, b) as DecodeResult.Ok).frame
        assertEquals(ScaleUnit.LB, f.unit)
        assertEquals(150.0 * 0.45359237, f.weightKg, 1e-9)
    }

    @Test
    fun vf0_sintetica_decodifica_lsb_primero() {
        val f = ok(OkokTestVectors.SYN_VF0)
        assertEquals(OkokVariant.VF0, f.variant)
        assertEquals(58.6, f.weightKg, delta)
        assertNull(f.stable)
        assertNull(f.checksumOk)
    }

    @Test
    fun c0_unidad_jin_y_stlb() {
        val jin = OkokTestVectors.TV01.data.also { it[6] = (0x01 or (2 shl 1) or (1 shl 3)).toByte() }
        val fj = (OkokDecoder.decode(0x01C0, jin) as DecodeResult.Ok).frame
        assertEquals(ScaleUnit.JIN, fj.unit)
        assertEquals(67.05 / 2, fj.weightKg, delta)

        val st = OkokTestVectors.TV01.data.also {
            it[0] = 10; it[1] = 70 // 10 st 7,0 lb con ÷10
            it[6] = (0x01 or (0 shl 1) or (3 shl 3)).toByte()
        }
        val fs = (OkokDecoder.decode(0x01C0, st) as DecodeResult.Ok).frame
        assertEquals(ScaleUnit.ST_LB, fs.unit)
        assertEquals((10 * 14 + 7.0) * 0.45359237, fs.weightKg, 1e-9)
    }

    @Test
    fun c0_impedancia_cifrada_no_se_reporta() {
        val b = OkokTestVectors.TV01.data.also { it[2] = (it[2].toInt() or 0x80).toByte() }
        val f = (OkokDecoder.decode(0x07C0, b) as DecodeResult.Ok).frame
        assertTrue(f.encrypted)
        assertNull(f.impedanceOhm)
        assertEquals(67.05, f.weightKg, delta)
    }

    @Test
    fun c0_corta_o_peso_cero_se_descarta() {
        assertTrue(OkokDecoder.decode(0x07C0, ByteArray(12)) is DecodeResult.Rejected)
        assertTrue(OkokDecoder.decode(0x07C0, ByteArray(13)) is DecodeResult.Rejected)
    }

    @Test
    fun otras_marcas_no_son_okok() {
        assertEquals(DecodeResult.NotOkok, OkokDecoder.decode(0x004C, ByteArray(23))) // Apple
        assertEquals(DecodeResult.NotOkok, OkokDecoder.decode(0x0006, ByteArray(13))) // Microsoft
        assertEquals(DecodeResult.NotOkok, OkokDecoder.decode(0x0157, ByteArray(13)))
    }

    @Test
    fun deteccion_de_variante_por_company_id() {
        assertEquals(OkokVariant.V20, OkokDecoder.variantOf(0x20CA))
        assertEquals(OkokVariant.V11, OkokDecoder.variantOf(0x11CA))
        assertEquals(OkokVariant.VF0, OkokDecoder.variantOf(0xF0FF))
        assertEquals(OkokVariant.C0, OkokDecoder.variantOf(0xB1C0))
        assertNull(OkokDecoder.variantOf(0x10FF)) // V10: no implementada
    }
}
