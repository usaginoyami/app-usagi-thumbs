import com.usaginoyami.thumb.*
import org.junit.Assert.*
import org.junit.Test

class ProjectJsonTest {

    private fun sample(): Project =
        Templates.all[0].build()
            .add { ShapeLayer.create(it, ShapeKind.ARROW).copy(rotation = -20f, cx = 123.5f) }
            .add { ShapeLayer.create(it, ShapeKind.STAR) }
            .add { emojiLayer(it, "😱") }
            .add { ImageLayer(it, "img:7", 500, 400, stickerWidth = 14f, stickerColor = 0xFF00E5FF.toInt(), flipH = true, blend = Blend.SCREEN, opacity = 0.7f) }
            .add { TextLayer(it, "Linha \"1\"\nLinha\\2\tTab", Font.SERIF, glow = 0x80FF0000.toInt()) }
            .copy(background = Background(BgKind.GRADIENT, listOf(0xFF112233.toInt(), 0xFFAABBCC.toInt()), 45f))

    @Test fun roundTripKeepsEverything() {
        val p = sample()
        val back = ProjectJson.decode(ProjectJson.encode(p, "Meu \"teste\" 😱"))!!
        assertEquals("Meu \"teste\" 😱", back.name)
        assertEquals(p, back.project)
    }

    @Test fun badTextIsRejected() {
        assertNull(ProjectJson.decode("{ruim"))
        assertNull(ProjectJson.decode(""))
        assertNull(ProjectJson.decode("[]"))
    }

    @Test fun unknownLayersAreSkippedAndNextIdFixed() {
        val odd = """{"v":9,"nextId":1,"layers":[{"id":5,"type":"alien"},{"id":6,"type":"text","text":"a","font":"XXX"},{"id":7,"type":"image","src":"img:1","srcW":0,"srcH":10}]}"""
        val o = ProjectJson.decode(odd)!!
        assertEquals(1, o.project.layers.size)
        assertEquals(Font.IMPACT, (o.project.layers[0] as TextLayer).font)
        assertEquals(7, o.project.nextId)
    }

    @Test fun imageKeysSkipMascotAndCounterContinues() {
        val p = Project()
            .add { ImageLayer(it, MASCOT_SRC, MASCOT_W, MASCOT_H) }
            .add { ImageLayer(it, "img:3", 10, 10) }
            .add { ImageLayer(it, "img:12", 10, 10) }
        assertEquals(setOf("img:3", "img:12"), ProjectJson.imageKeys(p))
        assertEquals(12, ProjectJson.maxImageNumber(listOf("img:3", "img:12", "x")))
        assertEquals(0, ProjectJson.maxImageNumber(emptyList()))
    }

    @Test fun titleUsesFirstRealText() {
        val p = Project().add { emojiLayer(it, "🔥") }.add { TextLayer(it, "\nGRANDE VITÓRIA\nsub") }
        assertEquals("GRANDE VITÓRIA", projectTitle(p))
        assertEquals("Thumb sem título", projectTitle(Project()))
        assertEquals(28, projectTitle(Project().add { TextLayer(it, "A".repeat(80)) }).length)
    }

    @Test fun duplicatingAShapeKeepsItsKind() {
        val p = Project().add { ShapeLayer.create(it, ShapeKind.ELLIPSE) }
        val d = p.duplicate(p.layers[0].id)
        assertEquals(2, d.layers.size)
        assertEquals(ShapeKind.ELLIPSE, (d.layers[1] as ShapeLayer).kind)
        assertNotEquals(d.layers[0].id, d.layers[1].id)
    }

    @Test fun shapeGeometryHasExpectedVertices() {
        assertEquals(14, ShapeLayer.arrowPoints(400f, 200f).size)
        val star = ShapeLayer.starPoints(300f, 300f)
        assertEquals(20, star.size)
        assertEquals(-150f, star[1], 0.01f) // primeira ponta aponta para cima
    }
}
