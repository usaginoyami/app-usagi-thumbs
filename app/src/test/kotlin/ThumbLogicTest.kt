import com.usaginoyami.thumb.*
import org.junit.Assert.*
import org.junit.Test

class ThumbLogicTest {
    private val size: (Layer) -> Pair<Float, Float> = { 200f to 100f }

    @Test fun hitTestRespectsRotationAndOrder() {
        val a = TextLayer(1, "a", cx = 500f, cy = 300f)
        val b = TextLayer(2, "b", cx = 500f, cy = 300f, rotation = 90f)
        assertEquals(2, hitTest(listOf(a, b), 500f, 300f, sizeOf = size)?.id)
        // b girada 90°: 200x100 vira 100 de largura x 200 de altura
        assertEquals(2, hitTest(listOf(b), 500f, 380f, sizeOf = size)?.id)
        assertNull(hitTest(listOf(b), 580f, 300f, sizeOf = size))
        assertEquals(1, hitTest(listOf(a, b), 590f, 300f, sizeOf = size)?.id)
    }

    @Test fun gestureClampsAndWraps() {
        val l = TextLayer(1, "a", rotation = 170f)
        val g = applyGesture(l, 10f, -5f, 1000f, 20f) as TextLayer
        assertEquals(30f, g.scale, 0f)
        assertEquals(-170f, g.rotation, 0.001f)
        assertEquals(650f, g.cx, 0f)
    }

    @Test fun reorderAndDuplicate() {
        var p = Project().add { TextLayer(it, "1") }.add { TextLayer(it, "2") }.add { TextLayer(it, "3") }
        p = p.toFront(1)
        assertEquals(listOf(2, 3, 1), p.layers.map { it.id })
        p = p.toBack(3)
        assertEquals(listOf(3, 2, 1), p.layers.map { it.id })
        p = p.duplicate(2)
        assertEquals(4, p.layers.last().id)
        assertEquals(670f, p.layers.last().cx, 0f)
    }

    @Test fun snapWorks() {
        assertEquals(640f, snap(644f, listOf(640f), 8f), 0f)
        assertEquals(700f, snap(700f, listOf(640f), 8f), 0f)
    }

    @Test fun templatesBuild() {
        for (t in Templates.all) {
            val p = t.build()
            assertEquals(p.layers.map { it.id }.toSet().size, p.layers.size)
        }
        assertEquals(0, Templates.all.last().build().layers.size)
    }

    @Test fun magicEraseContiguous() {
        val w = 6; val h = 4
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        // quadrado branco 2x2 no meio e um pixel preto isolado dentro dele
        for (y in 1..2) for (x in 2..3) px[y * w + x] = 0xFFFFFFFF.toInt()
        val n = Cutout.magicErase(px, w, h, 0, 0, 20)
        assertEquals(w * h - 4, n)
        assertEquals(0, px[0])
        assertEquals(0xFFFFFFFF.toInt(), px[1 * w + 2])
    }

    @Test fun magicEraseNonContiguousAndTolerance() {
        val w = 5; val h = 1
        val px = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertEquals(2, Cutout.magicErase(px.clone(), w, h, 0, 0, 5, contiguous = false))
        assertEquals(3, Cutout.magicErase(px.clone(), w, h, 0, 0, 40, contiguous = false))
        assertEquals(1, Cutout.magicErase(px.clone(), w, h, 0, 0, 40, contiguous = true))
    }

    @Test fun brushEraseAndRestore() {
        val w = 10; val h = 10
        val orig = IntArray(w * h) { 0xFFFF0000.toInt() }
        val px = orig.clone()
        Cutout.brush(px, w, h, 5f, 5f, 2f)
        assertEquals(0, px[5 * w + 5])
        assertEquals(0xFFFF0000.toInt(), px[0])
        Cutout.brush(px, w, h, 5f, 5f, 1f, orig)
        assertEquals(0xFFFF0000.toInt(), px[5 * w + 5])
        assertEquals(0, px[5 * w + 7])
    }

    @Test fun softenEdgesOnlyTouchesBorder() {
        val w = 5; val h = 5
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        px[0] = 0
        Cutout.softenEdges(px, w, h)
        assertTrue((px[1] ushr 24) in 1..254)
        assertEquals(255, px[12] ushr 24)
    }

    @Test fun bounds() {
        val w = 6; val h = 5
        val px = IntArray(w * h)
        assertNull(Cutout.contentBounds(px, w, h))
        px[2 * w + 3] = 0xFFFFFFFF.toInt(); px[3 * w + 1] = 0xFFFFFFFF.toInt()
        assertArrayEquals(intArrayOf(1, 2, 4, 4), Cutout.contentBounds(px, w, h))
    }

    @Test fun bestQuality() {
        val calls = ArrayList<Int>()
        val q = Export.bestQuality(1000, 40, 95) { calls += it; it * 20 }
        assertEquals(50, q)
        assertEquals(95, Export.bestQuality(10_000, 40, 95) { it * 20 })
        assertEquals(40, Export.bestQuality(10, 40, 95) { it * 20 })
        assertTrue(calls.size < 12)
    }

    @Test fun fileNameAndSize() {
        assertEquals("thumb_20260101_000000.jpg", Export.fileName(1767225600000L, false))
        assertEquals("thumb_20241231_235959.png", Export.fileName(1735689599000L, true))
        assertEquals("512 KB", Export.humanSize(512 * 1024))
        assertEquals("1.50 MB", Export.humanSize(1572864))
    }

    @Test fun toCanvasInvertsToLocal() {
        val l = TextLayer(1, "a", cx = 300f, cy = 200f, rotation = 33f, scale = 1.7f)
        val (cx, cy) = toCanvas(l, 40f, -25f)
        val (lx, ly) = toLocal(l, cx, cy)
        assertEquals(40f, lx, 0.01f)
        assertEquals(-25f, ly, 0.01f)
        val r = TextLayer(2, "b", cx = 100f, cy = 100f, rotation = 90f)
        val (x, y) = toCanvas(r, 10f, 0f)
        assertEquals(100f, x, 0.01f)
        assertEquals(110f, y, 0.01f)
    }

    @Test fun coverScaleCoversCanvas() {
        assertEquals(1f, coverScale(1280, 720), 0f)
        assertEquals(7.2f, coverScale(1000, 100), 0.0001f)
        assertEquals(1.28f, coverScale(1000, 2000), 0.0001f)
    }
}
