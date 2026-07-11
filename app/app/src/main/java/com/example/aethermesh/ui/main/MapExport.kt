package com.example.aethermesh.ui.main

object MapExport {
    fun buildKml(points: List<Pair<Double, Double>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        append("  <Document>\n")
        append("    <name>AetherMesh Tracklog</name>\n")
        append("    <Style id=\"trackLine\"><LineStyle><color>7f00ffff</color><width>4</width></LineStyle></Style>\n")
        append("    <Placemark><name>Track Path</name><styleUrl>#trackLine</styleUrl><LineString>\n")
        append("      <tessellate>1</tessellate><coordinates>\n")
        points.forEach { (latitude, longitude) -> append("        $longitude,$latitude,0\n") }
        append("      </coordinates></LineString></Placemark>\n")
        append("  </Document>\n</kml>\n")
    }
}
